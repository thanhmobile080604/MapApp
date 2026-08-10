package com.example.mapapp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mapapp.data.dao.TripHistoryDao
import com.example.mapapp.data.dao.UserConfigDao
import com.example.mapapp.data.model.TripHistory
import com.example.mapapp.data.model.UserConfig

@Database(
    entities = [TripHistory::class, UserConfig::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripHistoryDao(): TripHistoryDao
    abstract fun userConfigDao(): UserConfigDao
}
