package com.example.mapapp.data

import android.content.Context
import androidx.room.Room
import com.example.mapapp.data.dao.AlertHistoryDao
import com.example.mapapp.data.model.AlertHistory
import com.example.mapapp.data.model.AlertType
import com.example.mapapp.data.model.UserConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object LocationRepository {
    private lateinit var db: AppDatabase
    private lateinit var alertHistoryDao: AlertHistoryDao

    private val _locationFlow = MutableStateFlow(LocationData())
    val locationFlow: StateFlow<LocationData> = _locationFlow

    private val _isTrackingFlow = MutableStateFlow(false)
    val isTrackingFlow: StateFlow<Boolean> = _isTrackingFlow

    private val _configFlow = MutableStateFlow(UserConfig())
    val configFlow: StateFlow<UserConfig> = _configFlow

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

    fun updateLocation(lat: Double, lon: Double, speedKmh: Float) {
        _locationFlow.value = LocationData(lat, lon, speedKmh, hasFix = true)
    }

    fun setTracking(isTracking: Boolean) {
        _isTrackingFlow.value = isTracking
    }

    fun setSpeedLimit(speedLimitKmh: Int) {
        _configFlow.value = _configFlow.value.copy(speedLimitKmh = speedLimitKmh)
    }

    fun setDrowsinessAlert(enabled: Boolean) {
        _configFlow.value = _configFlow.value.copy(drowsinessAlertEnabled = enabled)
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
    /** True only after a real device location has been received. */
    val hasFix: Boolean = false
)
