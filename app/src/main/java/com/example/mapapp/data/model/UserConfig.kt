package com.example.mapapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity storing user configuration such as speed limit.
 */
@Entity(tableName = "user_config")
data class UserConfig(
    @PrimaryKey val id: Int = 0, // single row
    val speedLimitKmh: Int = 80,
    val drowsinessAlertEnabled: Boolean = true
)
