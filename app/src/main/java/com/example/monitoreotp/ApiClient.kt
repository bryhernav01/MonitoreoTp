package com.example.monitoreotp

import android.content.Context
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Response
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

interface ApiService {
    @POST("api/tp/comercial/public/monitoreo/location")
    suspend fun sendLocation(@Body location: LocationRequestData): Response<LocationResponse>
}

data class LocationRequestData(
    val device_id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val speed: Float?,
    val battery_level: Int?,
    val timestamp: String
)

data class LocationResponse(
    val success: Boolean,
    val message: String,
    val data: LocationResponseData?
)

data class LocationResponseData(
    val driver_id: Int,
    val reported_at: String,
    val status: String
)

object ApiClient {
    private var baseUrl: String = "https://api.grupopakatnamu.com/"
    private var timeout: Int = 30

    private var retrofitInstance: Retrofit? = null
    private var apiService: ApiService? = null
    private var okHttpClient: OkHttpClient? = null

    fun initialize(context: Context) {
        val config = ConfigManager.getInstance(context)
        baseUrl = config.getApiBaseUrl()
        timeout = config.getApiTimeout()

        if (okHttpClient == null) {
            val builder = OkHttpClient.Builder()
                .connectTimeout(timeout.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeout.toLong(), TimeUnit.SECONDS)
                .writeTimeout(timeout.toLong(), TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)

            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            okHttpClient = builder.build()
        }
        if (retrofitInstance == null) {
            retrofitInstance = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient!!)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            apiService = retrofitInstance?.create(ApiService::class.java)
        }
    }

    fun getInstance(): ApiService {
        return apiService
            ?: throw IllegalStateException("ApiClient not initialized. Call initialize() first.")
    }
}