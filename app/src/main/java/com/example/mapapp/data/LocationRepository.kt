package com.example.mapapp.data

import android.content.Context
import androidx.room.Room
import com.example.mapapp.data.dao.AlertHistoryDao
import com.example.mapapp.data.model.AlertHistory
import com.example.mapapp.data.model.AlertType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object LocationRepository {
    private lateinit var db: AppDatabase
    private lateinit var alertHistoryDao: AlertHistoryDao

    fun init(context: Context) {
        if (!::db.isInitialized) {
            db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "visual_gnss_db"
            ).fallbackToDestructiveMigration().build()
            alertHistoryDao = db.alertHistoryDao()
        }
    }

    fun recordAlert(type: AlertType) {
        CoroutineScope(Dispatchers.IO).launch {
            alertHistoryDao.insert(AlertHistory(alertType = type))
        }
    }
}

data class LocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedKmh: Float = 0f,
    val hasFix: Boolean = false
)
