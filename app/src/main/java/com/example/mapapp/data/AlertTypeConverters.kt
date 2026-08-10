package com.example.mapapp.data

import androidx.room.TypeConverter
import com.example.mapapp.data.model.AlertType

class AlertTypeConverters {
    @TypeConverter
    fun fromAlertType(value: AlertType): String = value.name.lowercase()

    @TypeConverter
    fun toAlertType(value: String): AlertType = AlertType.valueOf(value.uppercase())
}
