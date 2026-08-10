package com.example.mapapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mapapp.data.model.TripHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface TripHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripHistory)

    @Query("SELECT * FROM trip_history ORDER BY timestamp DESC")
    fun getAllTrips(): Flow<List<TripHistory>>

    @Query("DELETE FROM trip_history")
    suspend fun clearAll()
}
