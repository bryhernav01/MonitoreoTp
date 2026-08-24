package com.example.monitoreotp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.IOException

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 2000L
    }

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getInstance(applicationContext).offlineLocationDao()
        val locations = dao.getAll()
        if (locations.isEmpty()) {
            return Result.success()
        }

        val api = ApiClient.getInstance()
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", "") ?: ""

        if (deviceId.isEmpty() || deviceId.length != 15) {
            // No hay IMEI válido, no podemos enviar
            return Result.failure()
        }

        var successCount = 0
        var retryCount = 0

        for (loc in locations) {
            var success = false
            var attempts = 0

            while (!success && attempts < MAX_RETRIES) {
                try {
                    val locationData = LocationRequestData(
                        device_id = deviceId,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracy = loc.accuracy,
                        speed = loc.speed,
                        battery_level = loc.batteryLevel,
                        timestamp = loc.timestamp
                    )

                    val response: Response<LocationResponse> = api.sendLocation(locationData)

                    if (response.isSuccessful) {
                        dao.delete(loc)
                        successCount++
                        success = true
                    } else {
                        // Si es error 4xx, no reintentar (problema de cliente)
                        if (response.code() in 400..499) {
                            // Loggear y continuar con la siguiente ubicación
                            android.util.Log.e("UploadWorker", "Error cliente: ${response.code()} - ${response.message()}")
                            success = true // Marcamos como "procesado" pero no eliminamos? Mejor eliminamos?
                            // Decidimos no eliminar para que quede en la cola y se intente más tarde
                            // Pero para evitar bucles, lo dejamos y saltamos
                            break
                        }
                        // Si es 5xx, reintentar con backoff
                        attempts++
                        if (attempts < MAX_RETRIES) {
                            val backoff = INITIAL_BACKOFF_MS * (1L shl attempts)
                            delay(backoff)
                        }
                    }
                } catch (e: IOException) {
                    // Error de red, reintentar con backoff
                    attempts++
                    if (attempts < MAX_RETRIES) {
                        val backoff = INITIAL_BACKOFF_MS * (1L shl attempts)
                        delay(backoff)
                    }
                } catch (e: Exception) {
                    // Otro error, reintentar
                    attempts++
                    if (attempts < MAX_RETRIES) {
                        val backoff = INITIAL_BACKOFF_MS * (1L shl attempts)
                        delay(backoff)
                    }
                }
            }

            // Si después de todos los reintentos falló, salimos del worker para reintentar más tarde
            if (!success) {
                return Result.retry()
            }
        }

        return Result.success()
    }
}