package com.example.monitoreotp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_locations")
data class OfflineLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val speed: Float?,
    val batteryLevel: Int?,
    val timestamp: String,
    val createdAt: Long = System.currentTimeMillis()
)