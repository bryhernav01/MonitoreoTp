package com.example.monitoreotp

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Build
import android.util.Log

class BootJobService : JobService () {
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("BootJobService", "Servicio de inicio de sistema iniciado")
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val shouldRun = prefs.getBoolean("service_should_run", false)
        val deviceId = prefs.getString("device_id", "") ?: ""

        if(shouldRun && deviceId.length == 15){
            Log.d("BootJobService", "Iniciando el servicio de ubicación")
            val serviceIntent = Intent(this, LocationService::class.java).apply {
                putExtra("EXTRA_DEVICE_ID", deviceId)
                putExtra("EXTRA_FROM_BOOT", true)
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            }else {
                startService(serviceIntent)
            }
        }

        jobFinished(params, false)
        return false
    }
    override fun onStopJob(params: JobParameters?) : Boolean {
        return false
    }
}