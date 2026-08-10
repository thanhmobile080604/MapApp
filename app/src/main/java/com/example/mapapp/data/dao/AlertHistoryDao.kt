package com.example.mapapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mapapp.data.model.AlertHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertHistoryDao {
    @Insert
    suspend fun insert(alert: AlertHistory)

    @Query("SELECT * FROM alert_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AlertHistory>>

    @Query("DELETE FROM alert_history")
    suspend fun clearAll()
}
