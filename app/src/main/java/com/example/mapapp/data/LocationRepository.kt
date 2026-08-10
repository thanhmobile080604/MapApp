package com.example.mapapp.data

import android.content.Context
import androidx.lifecycle.asLiveData
import androidx.room.Room
import com.example.mapapp.data.dao.TripHistoryDao
import com.example.mapapp.data.model.TripHistory
import com.example.mapapp.data.dao.UserConfigDao
import com.example.mapapp.data.model.UserConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Singleton repository that holds the latest location data and user configuration.
 * It uses a StateFlow so ViewModels can observe updates.
 */
object LocationRepository {
    private lateinit var db: AppDatabase
    private lateinit var userConfigDao: UserConfigDao
    private lateinit var tripHistoryDao: TripHistoryDao

    // Exposed flows
    private val _locationFlow = MutableStateFlow(LocationData())
    val locationFlow: StateFlow<LocationData> = _locationFlow

    // User config flow – reads from Room and defaults if not present
    private val _configFlow = MutableStateFlow(UserConfig())
    val configFlow: StateFlow<UserConfig> = _configFlow

    fun init(context: Context) {
        if (!::db.isInitialized) {
            db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "visual_gnss_db"
            ).fallbackToDestructiveMigration().build()
            userConfigDao = db.userConfigDao()
            // Observe config from DB
            CoroutineScope(Dispatchers.IO).launch {
                userConfigDao.getConfig().collect { cfg ->
                    _configFlow.value = cfg ?: UserConfig()
                }
            }
            // Initialize TripHistory DAO
            tripHistoryDao = db.tripHistoryDao()
        }
    }

    /** Update location – called by the foreground service */
    fun updateLocation(lat: Double, lon: Double, speedKmh: Float) {
        _locationFlow.value = LocationData(lat, lon, speedKmh)
    }

    /** Save user config (e.g., speed limit) */
    suspend fun saveConfig(config: UserConfig) {
        userConfigDao.insert(config)
    }

    /** Insert a trip point into DB */
    fun insertTrip(trip: TripHistory) {
        CoroutineScope(Dispatchers.IO).launch {
            tripHistoryDao.insert(trip)
        }
    }
}

/** Simple data holder for live location */
data class LocationData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedKmh: Float = 0f
)
