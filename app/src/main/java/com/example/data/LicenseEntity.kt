package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "license_info")
data class LicenseEntity(
    @PrimaryKey val licenseKey: String,
    val status: String, // "ACTIVE", "EXPIRED", "DEACTIVATED"
    val planType: String, // "VIP Pro", "Ultra Fast", "Standard"
    val expiryTimestamp: Long,
    val maxDevices: Int = 3,
    val activeDevicesCount: Int = 1,
    val activatedAt: Long = System.currentTimeMillis()
)
