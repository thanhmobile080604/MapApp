package com.example.mapapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

/**
 * Entity representing a recorded trip point.
 */
@Entity(tableName = "trip_history")
data class TripHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val maxSpeedKmh: Float? = null
)
