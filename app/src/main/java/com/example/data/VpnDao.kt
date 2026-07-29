package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnDao {
    // Servers
    @Query("SELECT * FROM vpn_servers ORDER BY latencyMs ASC")
    fun getAllServers(): Flow<List<VpnServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<VpnServerEntity>)

    @Query("DELETE FROM vpn_servers")
    suspend fun clearAllServers()

    // License
    @Query("SELECT * FROM license_info LIMIT 1")
    fun getActiveLicense(): Flow<LicenseEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLicense(license: LicenseEntity)

    @Query("DELETE FROM license_info")
    suspend fun clearLicense()

    // Subscriptions
    @Query("SELECT * FROM subscriptions ORDER BY lastUpdated DESC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(sub: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscription(id: String)
}
