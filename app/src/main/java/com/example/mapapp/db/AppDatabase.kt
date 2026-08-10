package com.example.mapapp.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.mapapp.db.dao.AlertHistoryDao
import com.example.mapapp.model.AlertHistory

@Database(
    entities = [AlertHistory::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(AlertTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alertHistoryDao(): AlertHistoryDao
}
