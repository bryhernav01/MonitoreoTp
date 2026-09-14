package com.example.monitoreotp

import android.app.Application
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MonitoreoTPApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "MonitoreoTPApp"
        private const val BOOT_JOB_ID = 1001

        /**
         * Programa (o reprograma) el Job persistente que sobrevive al reinicio
         * del dispositivo incluso en Motorola con políticas anti-sideload.
         *
         * Se puede llamar desde cualquier punto de la app con el Context de la aplicación:
         *   MonitoreoTPApplication.scheduleBootJob(context)
         */
        fun scheduleBootJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val componentName = ComponentName(context, BootJobService::class.java)

            // Cancelar el job anterior si existía, para reprogramar con la lógica actual
            jobScheduler.cancel(BOOT_JOB_ID)

            val jobInfo = JobInfo.Builder(BOOT_JOB_ID, componentName)
                .setPersisted(true)  // 🔑 Sobrevive al reinicio del dispositivo
                .setMinimumLatency(TimeUnit.MINUTES.toMillis(2))   // Arranca ~2 min después del boot
                .setOverrideDeadline(TimeUnit.MINUTES.toMillis(5)) // Como máximo en 5 min
                .build()

            val result = jobScheduler.schedule(jobInfo)
            Log.d(TAG, "📅 Job persistente programado: $result (JobInfo id=$BOOT_JOB_ID)")

            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "✅ JobScheduler aceptó el job persistente")
            } else {
                Log.e(TAG, "❌ JobScheduler RECHAZÓ el job persistente — revisa BIND_JOB_SERVICE en el manifest")
            }
        }

        /**
         * Cancela el Job persistente. Llamar cuando el usuario detiene el monitoreo
         * con el código de seguridad.
         */
        fun cancelBootJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(BOOT_JOB_ID)
            Log.d(TAG, "🚫 Job persistente cancelado (id=$BOOT_JOB_ID)")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()

        // 1) Inicializar WorkManager
        try {
            WorkManager.getInstance(this)
            Log.d(TAG, "WorkManager inicializado correctamente")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "WorkManager no estaba inicializado, inicializando manualmente")
            WorkManager.initialize(
                this,
                Configuration.Builder()
                    .setMinimumLoggingLevel(Log.DEBUG)
                    .build()
            )
        }

        // 2) Programar el Job persistente si el servicio debe estar corriendo
        //    Esto se ejecuta cada vez que el proceso principal arranca:
        //    - Cuando el usuario abre la app
        //    - Cuando el sistema despierta la app para ejecutar un Worker
        //    - Cuando el JobScheduler u otro componente arranca el proceso
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val shouldRun = prefs.getBoolean("service_should_run", false)
        val deviceId = prefs.getString("device_id", "") ?: ""

        if (shouldRun && deviceId.length == 15 && deviceId.matches(Regex("^\\d{15}$"))) {
            Log.d(TAG, "🔁 service_should_run=true y IMEI válido → programando Job persistente")
            scheduleBootJob(this)
        } else {
            Log.d(TAG, "⏸️ service_should_run=false o IMEI inválido → NO se programa Job persistente")
        }
    }
}