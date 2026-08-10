package com.example.mapapp.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapapp.service.LocationForegroundService
import com.example.mapapp.data.LocationRepository
import com.example.mapapp.data.model.UserConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel that bridges the UI with the [LocationRepository] and controls the
 * foreground location service.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    // Expose location and config as StateFlows for the UI
    val locationFlow: StateFlow<com.example.mapapp.data.LocationData> =
        LocationRepository.locationFlow.stateIn(viewModelScope, SharingStarted.Eagerly, com.example.mapapp.data.LocationData())

    val configFlow: StateFlow<UserConfig> =
        LocationRepository.configFlow.stateIn(viewModelScope, SharingStarted.Eagerly, UserConfig())

    /** Start the foreground location service */
    fun startTracking() {
        val intent = Intent(getApplication(), LocationForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    /** Stop the foreground location service */
    fun stopTracking() {
        val intent = Intent(getApplication(), LocationForegroundService::class.java)
        intent.action = LocationForegroundService.ACTION_STOP
        getApplication<Application>().stopService(intent)
    }

    /** Update speed limit in the user config */
    fun setSpeedLimit(limitKmh: Float) {
        viewModelScope.launch {
            val newConfig = configFlow.value.copy(speedLimitKmh = limitKmh.toInt())
            LocationRepository.saveConfig(newConfig)
        }
    }

    /** Enable/disable drowsiness alerts */
    fun setDrowsinessAlert(enabled: Boolean) {
        viewModelScope.launch {
            val newConfig = configFlow.value.copy(drowsinessAlertEnabled = enabled)
            LocationRepository.saveConfig(newConfig)
        }
    }
}
