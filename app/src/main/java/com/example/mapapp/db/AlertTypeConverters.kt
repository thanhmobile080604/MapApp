package com.example.mapapp.db

import androidx.room.TypeConverter
import com.example.mapapp.model.AlertType

class AlertTypeConverters {
    @TypeConverter
    fun fromAlertType(value: AlertType): String = value.name.lowercase()

    @TypeConverter
    fun toAlertType(value: String): AlertType = AlertType.valueOf(value.uppercase())
}
