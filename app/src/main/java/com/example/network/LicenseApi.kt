package com.example.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Request / response models for the IFIX VPN License API */

data class ActivateLicenseRequest(
    val licenseKey: String,
    val deviceId: String,
    val deviceName: String = "Android"
)

data class CheckLicenseRequest(
    val licenseKey: String,
    val deviceId: String
)

data class DeactivateLicenseRequest(
    val licenseKey: String,
    val deviceId: String
)

data class LicenseDto(
    val licenseKey: String,
    val status: String,
    val planType: String,
    val expiryTimestamp: Long,
    val maxDevices: Int = 5,
    val activeDevicesCount: Int = 1
)

data class LicenseApiResponse(
    val success: Boolean,
    val license: LicenseDto? = null,
    val error: String? = null,
    val message: String? = null,
    val status: String? = null
)

data class ServerListResponse(
    val success: Boolean,
    val servers: List<ServerDto> = emptyList()
)

data class ServerDto(
    val id: String,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val ipOrDomain: String,
    val port: Int,
    val protocol: String,
    val latencyMs: Int = 50,
    val status: String = "ONLINE",
    val userCapacityPercent: Int = 30,
    val flagEmoji: String = "🌐"
)

data class ConfigResponse(
    val success: Boolean,
    val config: RemoteConfigDto? = null
)

data class RemoteConfigDto(
    val announcementMessage: String = "",
    val isForceUpdateRequired: Boolean = false,
    val latestVersionName: String = "1.0.0",
    val latestVersionCode: Int = 1,
    val releaseNotes: String = "",
    val telegramChannel: String = "",
    val supportUrl: String = ""
)

interface LicenseApiService {
    @POST("api/license/activate")
    suspend fun activateLicense(@Body body: ActivateLicenseRequest): LicenseApiResponse

    @POST("api/license/check")
    suspend fun checkLicense(@Body body: CheckLicenseRequest): LicenseApiResponse

    @POST("api/license/deactivate")
    suspend fun deactivateLicense(@Body body: DeactivateLicenseRequest): LicenseApiResponse

    @GET("api/server/list")
    suspend fun getServers(): ServerListResponse

    @GET("api/config")
    suspend fun getConfig(): ConfigResponse
}
