package com.example.mapapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A single alert shown to the user. No location or continuous trip data is stored. */
@Entity(tableName = "alert_history")
data class AlertHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val alertType: AlertType
)

enum class AlertType {
    SPEED,
    DROWSINESS
}
