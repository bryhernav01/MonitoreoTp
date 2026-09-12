package com.example.monitoreotp

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.content.Intent
import android.os.Build

class ServiceWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val shouldRun = prefs.getBoolean("service_should_run", false)
        val deviceId = prefs.getString("device_id", "") ?: ""

        if (shouldRun && deviceId.length == 15 && deviceId.matches(Regex("^\\d{15}$"))) {

            if(!isLocationServiceRunning(applicationContext)){
                Log.d("Watchdog", "🔁 Servicio caído, reiniciando con IMEI=$deviceId")

                val intent = Intent(applicationContext, LocationService::class.java).apply {
                    putExtra("EXTRA_DEVICE_ID", deviceId)
                    putExtra("EXTRA_FROM_BOOT", true)
                }
                if(Build.VERSION.SDK_INT >=  Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                }else{
                    applicationContext.startService(intent)
                }
            }else {
                Log.d("Watchdog", "✅ Servicio sigue activo")
            }
        }else {
            Log.d("Watchdog", "⏸️ service_should_run=false o IMEI inválido, no se reinicia")
        }
        return Result.success()
    }
}