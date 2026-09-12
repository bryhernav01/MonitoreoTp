package com.example.monitoreotp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val shouldRun = prefs.getBoolean("service_should_run", false)
            val deviceId = prefs.getString("device_id", "") ?: ""

            if (shouldRun && deviceId.length == 15 && deviceId.matches(Regex("^\\d{15}$"))) {
                val serviceIntent = Intent(context, LocationService::class.java).apply {
                    putExtra("EXTRA_DEVICE_ID", deviceId)
                    putExtra("EXTRA_FROM_BOOT", true)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}