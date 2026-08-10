package com.example.mapapp.db

import android.content.Context
import androidx.room.Room
import com.example.mapapp.db.dao.AlertHistoryDao
import com.example.mapapp.model.AlertHistory
import com.example.mapapp.model.AlertType
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
