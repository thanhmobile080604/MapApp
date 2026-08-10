package com.example.mapapp.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapapp.service.LocationForegroundService
import com.example.mapapp.data.LocationRepository
import com.example.mapapp.data.model.UserConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val locationFlow: StateFlow<com.example.mapapp.data.LocationData> =
        LocationRepository.locationFlow.stateIn(viewModelScope, SharingStarted.Eagerly, com.example.mapapp.data.LocationData())

    val isTrackingFlow: StateFlow<Boolean> =
        LocationRepository.isTrackingFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val configFlow: StateFlow<UserConfig> =
        LocationRepository.configFlow.stateIn(viewModelScope, SharingStarted.Eagerly, UserConfig())

    fun startTracking() {
        val intent = Intent(getApplication(), LocationForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    fun stopTracking() {
        val intent = Intent(getApplication(), LocationForegroundService::class.java)
        intent.action = LocationForegroundService.ACTION_STOP
        getApplication<Application>().stopService(intent)
        LocationRepository.setTracking(false)
    }

    fun setSpeedLimit(speedLimitKmh: Float) {
        LocationRepository.setSpeedLimit(speedLimitKmh.toInt())
    }

    fun setDrowsinessAlert(enabled: Boolean) {
        LocationRepository.setDrowsinessAlert(enabled)
    }

    /** Fetches a position for the map without starting persistent trip tracking. */
    fun refreshCurrentLocation() {
        val app = getApplication<Application>()
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            app, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            app, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) return

        LocationServices.getFusedLocationProviderClient(app)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) updateLocation(location)
                else LocationServices.getFusedLocationProviderClient(app).lastLocation
                    .addOnSuccessListener { lastLocation -> lastLocation?.let(::updateLocation) }
            }
    }

    private fun updateLocation(location: android.location.Location) {
        LocationRepository.updateLocation(
            location.latitude,
            location.longitude,
            location.speed.coerceAtLeast(0f) * 3.6f
        )
    }
}
