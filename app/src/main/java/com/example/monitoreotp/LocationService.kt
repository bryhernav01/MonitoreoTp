package com.example.monitoreotp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val CHANNEL_ID = "location_channel"
    private val NOTIFICATION_ID = 1001

    private lateinit var prefs: android.content.SharedPreferences
    private var lastSendTime = 0L
    private var isServiceActive = false

    private lateinit var connectivityMonitor: ConnectivityMonitor
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Inicializar preferencias
        prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Inicializar API
        ApiClient.initialize(this)

        // Inicializar ConnectivityMonitor
        connectivityMonitor = ConnectivityMonitor(
            this,
            onNetworkAvailable = {
                Log.d("LocationService", "🌐 Red disponible, lanzando envío inmediato")
                triggerImmediateUpload()
            },
            onNetworkLost = {
                Log.d("LocationService", "📴 Red perdida")
            }
        )
        connectivityMonitor.startMonitoring()

        // Configurar notificación
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Inicializar FusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Programar WorkManager
        scheduleUploadWorker()
        scheduleWatchdog()

        // Iniciar actualizaciones de ubicación
        startLocationUpdates()

        // Programar limpieza de ubicaciones offline antiguas
        scheduleCleanup()

        Log.d("LocationService", "✅ Servicio iniciado correctamente")
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
            5000L // UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(3000L) // FASTEST_INTERVAL
            setMaxUpdateDelayMillis(0)
            setWaitForAccurateLocation(true)
            setMinUpdateDistanceMeters(0f)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                location?.let {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastSendTime >= 5000L) { // SEND_INTERVAL
                        lastSendTime = currentTime
                        serviceScope.launch {
                            sendLocationWithRetry(
                                it.latitude,
                                it.longitude,
                                it.accuracy,
                                it.speed
                            )
                        }
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
        val deviceId = prefs.getString("device_id", "") ?: ""
        if (deviceId.isEmpty() || deviceId.length != 15) {
            Log.e("LocationService", "❌ IMEI inválido: '$deviceId'")
            return
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = dateFormat.format(Date())

        val batteryLevel = getBatteryLevel()

        val locationData = LocationRequestData(
            device_id = deviceId,
            latitude = lat,
            longitude = lng,
            accuracy = accuracy,
            speed = speed,
            battery_level = batteryLevel,
            timestamp = timestamp
        )

        var attempts = 0
        var success = false

        while (!success && attempts < maxRetries) {
            try {
                val response = ApiClient.getInstance().sendLocation(locationData)

                if (response.isSuccessful) {
                    success = true
                    prefs.edit().putBoolean("api_ok", true).apply()
                    prefs.edit().putFloat("last_lat", lat.toFloat()).apply()
                    prefs.edit().putFloat("last_lng", lng.toFloat()).apply()

                    // Actualizar contador en UI
                    val count = prefs.getInt("locations_sent", 0)
                    prefs.edit().putInt("locations_sent", count + 1).apply()

                    Log.d("LocationService", "✅ Ubicación enviada correctamente")
                } else {
                    Log.e("LocationService", "⚠️ Error en envío: ${response.code()}")
                    attempts++
                    if (attempts < maxRetries) {
                        delay(2000L * (1L shl attempts))
                    }
                }
            } catch (e: Exception) {
                Log.e("LocationService", "❌ Error enviando ubicación: ${e.message}")
                attempts++
                if (attempts < maxRetries) {
                    delay(2000L * (1L shl attempts))
                }
            }
        }

        // Si fallaron todos los reintentos, guardar offline
        if (!success) {
            saveLocationOffline(lat, lng, accuracy, speed)
        }
    }

    private suspend fun saveLocationOffline(lat: Double, lng: Double, accuracy: Float?, speed: Float?) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")
            val timestamp = dateFormat.format(Date())

            val location = OfflineLocation(
                latitude = lat,
                longitude = lng,
                accuracy = accuracy,
                speed = speed,
                batteryLevel = getBatteryLevel(),
                timestamp = timestamp
            )

            val dao = AppDatabase.getInstance(this).offlineLocationDao()
            dao.insert(location)

            val count = dao.getCount()
            Log.d("LocationService", "💾 Ubicación guardada offline (total: $count)")

        } catch (e: Exception) {
            Log.e("LocationService", "❌ Error guardando offline: ${e.message}")
        }
    }

    private fun getBatteryLevel(): Int? {
        return try {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            null
        }
    }

    private fun scheduleUploadWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "upload_work",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun triggerImmediateUpload() {
        val workRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "immediate_upload",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun scheduleWatchdog() {
        val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "watchdog_work",
            ExistingPeriodicWorkPolicy.KEEP,
            request
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
                    Log.d("LocationService", "🧹 Limpieza de ubicaciones antiguas completada")
                } catch (e: Exception) {
                    Log.e("LocationService", "❌ Error en limpieza: ${e.message}")
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoreo de Flota",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificación persistente para tracking GPS"
                setShowBadge(false)
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
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
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