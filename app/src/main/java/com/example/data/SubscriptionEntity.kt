package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val subUrl: String,
    val serverCount: Int,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isAutoUpdateEnabled: Boolean = true
)
