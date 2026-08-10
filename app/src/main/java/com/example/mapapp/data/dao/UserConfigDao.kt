package com.example.mapapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mapapp.data.model.UserConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface UserConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: UserConfig)

    @Query("SELECT * FROM user_config WHERE id = 0 LIMIT 1")
    fun getConfig(): Flow<UserConfig?>
}
