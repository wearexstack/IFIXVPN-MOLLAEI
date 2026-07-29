package com.example.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

/**
 * Central HTTP client for IFIX VPN backend.
 *
 * Change [BASE_URL] to your real server address, e.g.:
 *   - Local test:  "http://10.0.2.2:3000/"   (Android emulator → host machine)
 *   - Real phone:  "http://192.168.1.10:3000/" (your PC LAN IP)
 *   - Production:  "https://api.ifixvpn.com/"
 */
object ApiClient {

    // ⚠️  Put your backend URL here (must end with /)
    const val BASE_URL = "https://api.ifixvpn.com/"

    // Fallback offline mode when server is unreachable
    const val ALLOW_OFFLINE_DEMO = true

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val licenseApi: LicenseApiService = retrofit.create(LicenseApiService::class.java)
}
