package com.example.monitoreotp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.work.*
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val CHANNEL_ID = "location_channel_v2"
    private val NOTIFICATION_ID = 1001

    private lateinit var prefs: android.content.SharedPreferences
    private var lastSendTime = 0L
    private var isServiceActive = false

    private var cachedDeviceId: String = ""

    private lateinit var connectivityMonitor: ConnectivityMonitor
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        ApiClient.initialize(this)

        connectivityMonitor = ConnectivityMonitor(
            this,
            onNetworkAvailable = {
                Log.d("LocationService", "🌐 Red disponible, activando envío inmediato")
                triggerImmediateUpload()
            },
            onNetworkLost = { Log.d("LocationService", "📴 Red perdida") }
        )
        connectivityMonitor.startMonitoring()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        scheduleUploadWorker()
        scheduleWatchdog()
        startLocationUpdates()
        scheduleCleanup()

        Log.d("LocationService", "✅ Servicio iniciado correctamente")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // recibir IMEI desde BootReceiver o MainActivity
        intent?.getStringExtra("EXTRA_DEVICE_ID")?.let {
            if (it.length == 15 && it.matches(Regex("^\\d{15}$"))) {
                cachedDeviceId = it
                // Refrescamos SharedPreferences para que el resto del código lo vea consistente
                prefs.edit().putString("device_id", it).apply()
                Log.d("LocationService", "📥 IMEI recibido por Intent: $it")
            }
        }

        //si el sistema revivió el servicio (START_STICKY con intent nulo),
        // caemos a SharedPreferences. En este punto el archivo ya está estable.
        if (cachedDeviceId.isEmpty()) {
            cachedDeviceId = prefs.getString("device_id", "") ?: ""
            Log.d("LocationService", "📂 IMEI recuperado desde prefs: $cachedDeviceId")
        }

        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationService", "❌ Sin permiso de ubicación")
            stopSelf()
            return
        }

        isServiceActive = true

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).apply {
            setMinUpdateIntervalMillis(3000L)
            setMaxUpdateDelayMillis(0)
            setWaitForAccurateLocation(true)
            setMinUpdateDistanceMeters(0f)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSendTime >= 5000L) {
                    lastSendTime = currentTime
                    serviceScope.launch {
                        sendLocationWithRetry(
                            location.latitude,
                            location.longitude,
                            location.accuracy,
                            location.speed
                        )
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        ).addOnFailureListener { e ->
            Log.e("LocationService", "❌ Error al iniciar actualizaciones: ${e.message}")
            stopSelf()
        }
    }

    private suspend fun sendLocationWithRetry(
        lat: Double,
        lng: Double,
        accuracy: Float?,
        speed: Float?,
        maxRetries: Int = 3
    ) {
        // 🔑 Usamos la variable cacheada (viene del Intent o, en su defecto, de prefs)
        val deviceId = cachedDeviceId
        if (deviceId.isEmpty() || deviceId.length != 15) {
            Log.e("LocationService", "❌ IMEI inválido: '$deviceId' — abortando envío")
            return
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val timestamp = dateFormat.format(Date())

        val locationData = LocationRequestData(
            device_id = deviceId,
            latitude = lat,
            longitude = lng,
            accuracy = accuracy,
            speed = speed,
            battery_level = getBatteryLevel(),
            timestamp = timestamp
        )

        var attempts = 0
        var success = false

        while (!success && attempts < maxRetries) {
            try {
                val response = ApiClient.getInstance().sendLocation(locationData)
                if (response.isSuccessful) {
                    success = true
                    prefs.edit()
                        .putBoolean("api_ok", true)
                        .putFloat("last_lat", lat.toFloat())
                        .putFloat("last_lng", lng.toFloat())
                        .putInt("locations_sent", prefs.getInt("locations_sent", 0) + 1)
                        .apply()
                    Log.d("LocationService", "✅ Ubicación enviada correctamente")
                } else {
                    Log.e("LocationService", "⚠️ Error en envío: ${response.code()}")
                    attempts++
                    if (attempts < maxRetries) delay(2000L * (1L shl attempts))
                }
            } catch (e: Exception) {
                Log.e("LocationService", "❌ Error enviando ubicación: ${e.message}")
                attempts++
                if (attempts < maxRetries) delay(2000L * (1L shl attempts))
            }
        }

        if (!success) {
            saveLocationOffline(lat, lng, accuracy, speed)
        }
    }

    private suspend fun saveLocationOffline(lat: Double, lng: Double, accuracy: Float?, speed: Float?) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val location = OfflineLocation(
                latitude = lat,
                longitude = lng,
                accuracy = accuracy,
                speed = speed,
                batteryLevel = getBatteryLevel(),
                timestamp = dateFormat.format(Date())
            )
            val dao = AppDatabase.getInstance(this).offlineLocationDao()
            dao.insert(location)
            Log.d("LocationService", "💾 Ubicación guardada offline (total: ${dao.getCount()})")
        } catch (e: Exception) {
            Log.e("LocationService", "❌ Error guardando offline: ${e.message}")
        }
    }

    private fun getBatteryLevel(): Int? {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) { null }
    }

    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "upload_work", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    private fun triggerImmediateUpload() {
        val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "immediate_upload", ExistingWorkPolicy.REPLACE, workRequest
        )
    }

    private fun scheduleWatchdog() {
        val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "watchdog_work", ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    private fun scheduleCleanup() {
        serviceScope.launch {
            while (isServiceActive) {
                delay(TimeUnit.HOURS.toMillis(4))
                try {
                    val dao = AppDatabase.getInstance(this@LocationService).offlineLocationDao()
                    val maxAge = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
                    dao.deleteOld(maxAge)
                    Log.d("LocationService", "🧹 Limpieza completada")
                } catch (e: Exception) {
                    Log.e("LocationService", "❌ Error en limpieza: ${e.message}")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANCE_LOW evita sonidos molestos, pero Motorola respeta mejor LOW para servicios persistentes
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoreo de Flota",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente para tracking GPS"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Transportes Pakatnamu S.A.C")
            .setContentText("Monitoreo de ubicación activo")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isServiceActive = false
        connectivityMonitor.stopMonitoring()
        serviceScope.cancel()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e("LocationService", "❌ Error removiendo actualizaciones: ${e.message}")
        }
        Log.d("LocationService", "🛑 Servicio detenido")
    }
}