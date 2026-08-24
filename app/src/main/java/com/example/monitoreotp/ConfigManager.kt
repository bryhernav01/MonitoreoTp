package com.example.monitoreotp

import android.content.Context
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.FileInputStream
import java.util.Properties

class ConfigManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigManager(context).also { instance = it }
            }
        }
    }


    private var cachedProperties: Properties? = null
    private var lastLoadTime = 0L
    private val CACHE_DURATION = TimeUnit.MINUTES.toMillis(5)

    private fun getProperties(): Properties {
        val currentTime = System.currentTimeMillis()
        if (cachedProperties != null && (currentTime - lastLoadTime) < CACHE_DURATION) {
            return cachedProperties!!
        }
        cachedProperties = loadProperties()
        lastLoadTime = currentTime
        return cachedProperties!!
    }

    private fun loadProperties(): Properties {
        val props = Properties()
        try {
            val rootDir = context.filesDir.parentFile?.parentFile
            val localFile = File(rootDir, "local.properties")


            if (localFile.exists()) {
                FileInputStream(localFile).use { inputStream ->
                    props.load(inputStream)
                }
            } else {
                setDefaultValues(props)
            }
        } catch (e: Exception) {
            println(" Error cargando configuración: ${e.message}")
            setDefaultValues(props)
        }
        return props
    }

    private fun setDefaultValues(props: Properties) {
        props["API_BASE_URL"] = "https://api.grupopakatnamu.com/"
        props["STOP_CODE"] = "1234"
        props["API_TIMEOUT"] = "30"
        props["LOCATION_INTERVAL"] = "5000"
        props["FASTEST_INTERVAL"] = "3000"
        props["MIN_UPDATE_DISTANCE"] = "0"
        props["GPS_PRIORITY"] = "BALANCED"
        props["MAX_TIME_BETWEEN_SENDS"] = "7000"
    }

    // Métodos para obtener configuraciones
    fun getApiBaseUrl(): String =
        getProperties().getProperty("API_BASE_URL", "https://api.grupopakatnamu.com/")

    fun getStopCode(): String =
        getProperties().getProperty("STOP_CODE", "1234")

    fun getApiTimeout(): Int =
        getProperties().getProperty("API_TIMEOUT", "30").toIntOrNull() ?: 30

    fun getLocationInterval(): Long =
        getProperties().getProperty("LOCATION_INTERVAL", "5000").toLongOrNull() ?: 10000

    fun getFastestInterval(): Long =
        getProperties().getProperty("FASTEST_INTERVAL", "3000").toLongOrNull() ?: 5000


    fun getProperty(key: String, defaultValue: String = ""): String =
        getProperties().getProperty(key, defaultValue)


    fun invalidateCache() {
        cachedProperties = null
        lastLoadTime = 0L
    }

    fun getMinUpdateDistance(): Float =
        getProperties().getProperty("MIN_UPDATE_DISTANCE", "3").toFloatOrNull() ?: 5f

    fun getMaxTimeBetweenSends(): Long =
        getProperties().getProperty("MAX_TIME_BETWEEN_SENDS", "7000").toLongOrNull() ?: 10000L
}