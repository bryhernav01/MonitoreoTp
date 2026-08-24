package com.example.monitoreotp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ServiceWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val shouldRun = prefs.getBoolean("service_should_run", false)

        if (shouldRun) {
            val isRunning = isLocationServiceRunning(applicationContext)
            if (!isRunning) {
                val deviceId = prefs.getString("device_id", "") ?: ""
                if (deviceId.isNotEmpty() && deviceId.length == 15) {
                    startLocationService(applicationContext)
                }
            }
        }
        return Result.success()
    }
}