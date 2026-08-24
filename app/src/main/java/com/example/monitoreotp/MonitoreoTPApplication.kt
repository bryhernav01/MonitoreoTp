package com.example.monitoreotp

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager

class MonitoreoTPApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        try {
            WorkManager.getInstance(this)
            android.util.Log.d("MonitoreoTPApp", "WorkManager inicializado correctamente")
        } catch (e: IllegalStateException) {
            android.util.Log.w("MonitoreoTPApp", "WorkManager no estaba inicializado, inicializando manualmente")
            WorkManager.initialize(
                this,
                Configuration.Builder()
                    .setMinimumLoggingLevel(android.util.Log.DEBUG)
                    .build()
            )
        }
    }
}