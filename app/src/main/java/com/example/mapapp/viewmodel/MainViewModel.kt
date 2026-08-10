package com.example.mapapp.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapapp.model.LocationData
import com.example.mapapp.model.UserConfig
import com.example.mapapp.service.LocationForegroundService
import com.example.mapapp.service.LocationForegroundService.Companion.KEY_DROWSY
import com.example.mapapp.service.LocationForegroundService.Companion.KEY_FIX
import com.example.mapapp.service.LocationForegroundService.Companion.KEY_LAT
import com.example.mapapp.service.LocationForegroundService.Companion.KEY_LIMIT
import com.example.mapapp.service.LocationForegroundService.Companion.KEY_LON
import com.example.mapapp.service.LocationForegroundService.Companion.KEY_SPEED
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_CMD_DISMISS_ALERT
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_CMD_SET_DROWSY_ALERT
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_CMD_SET_SPEED_LIMIT
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_CMD_START_SIMULATION
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_CMD_START_TRACKING
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_CMD_STOP_SIMULATION
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_CMD_STOP_TRACKING
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_CONFIG_UPDATE
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_LOCATION_ENABLED
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_LOCATION_UPDATE
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_REGISTER_CLIENT
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_SIMULATION_STATUS
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_SPEED_ALERT
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_TRACKING_STATUS
import com.example.mapapp.service.LocationForegroundService.Companion.MSG_UNREGISTER_CLIENT
import com.example.mapapp.util.LocationHelper
import com.example.mapapp.util.LocationHelper.fetchDrivingRoute
import com.example.mapapp.util.LocationHelper.searchLocations
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

data class OsmSearchResult(val displayName: String, val lat: Double, val lon: Double)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private var serviceMessenger: Messenger? = null

    // UI State
    private val _locationFlow = MutableStateFlow(LocationData())
    val locationFlow: StateFlow<LocationData> = _locationFlow.asStateFlow()

    private val _isTrackingFlow = MutableStateFlow(false)
    val isTrackingFlow: StateFlow<Boolean> = _isTrackingFlow.asStateFlow()

    private val _configFlow = MutableStateFlow(UserConfig())
    val configFlow: StateFlow<UserConfig> = _configFlow.asStateFlow()

    private val _isLocationEnabledFlow = MutableStateFlow(true)
    val isLocationEnabledFlow: StateFlow<Boolean> = _isLocationEnabledFlow.asStateFlow()

    private val _isSimulationActiveFlow = MutableStateFlow(false)
    val isSimulationActiveFlow: StateFlow<Boolean> = _isSimulationActiveFlow.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<OsmSearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _destination = MutableStateFlow<OsmSearchResult?>(null)
    val destination = _destination.asStateFlow()

    private val _route = MutableStateFlow<List<GeoPoint>>(emptyList())
    val route = _route.asStateFlow()

    private val _speedAlertEvent = MutableSharedFlow<Float>()
    val speedAlertEvent = _speedAlertEvent.asSharedFlow()

    // Incoming Handler
    private val incomingHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_LOCATION_UPDATE -> {
                    val lat = msg.data.getDouble(KEY_LAT)
                    val lon = msg.data.getDouble(KEY_LON)
                    val speed = msg.data.getFloat(KEY_SPEED)
                    val fix = msg.data.getBoolean(KEY_FIX)
                    _locationFlow.value = LocationData(lat, lon, speed, fix)
                }
                MSG_TRACKING_STATUS -> _isTrackingFlow.value = msg.arg1 == 1
                MSG_CONFIG_UPDATE -> {
                    val limit = msg.data.getInt(KEY_LIMIT)
                    val drowsy = msg.data.getBoolean(KEY_DROWSY)
                    _configFlow.value = UserConfig(limit, drowsy)
                }
                MSG_LOCATION_ENABLED -> _isLocationEnabledFlow.value = msg.arg1 == 1
                MSG_SIMULATION_STATUS -> _isSimulationActiveFlow.value = msg.arg1 == 1
                MSG_SPEED_ALERT -> {
                    val speed = msg.obj as Float
                    viewModelScope.launch { _speedAlertEvent.emit(speed) }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val clientMessenger = Messenger(incomingHandler)

    fun onServiceConnected(messenger: Messenger) {
        this.serviceMessenger = messenger
        try {
            val msg = Message.obtain(null, MSG_REGISTER_CLIENT)
            msg.replyTo = clientMessenger
            messenger.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun onServiceDisconnected() {
        this.serviceMessenger = null
    }

    private fun sendCommand(what: Int, arg1: Int = 0, obj: Any? = null) {
        try {
            val msg = Message.obtain(null, what)
            msg.arg1 = arg1
            msg.obj = obj
            serviceMessenger?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun startTracking() {
        val intent = Intent(getApplication(), LocationForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
        sendCommand(MSG_CMD_START_TRACKING)
        LocationHelper.scheduleDrowsinessWorker(getApplication(), configFlow.value.drowsinessAlertEnabled)
    }

    fun stopTracking() {
        sendCommand(MSG_CMD_STOP_TRACKING)
        LocationHelper.cancelDrowsinessWorker(getApplication())
    }

    fun triggerTestSpeedAlert() {
        sendCommand(MSG_CMD_START_SIMULATION)
    }

    fun stopSpeedTest() {
        sendCommand(MSG_CMD_STOP_SIMULATION)
    }

    fun onSpeedAlertDismissed() {
        sendCommand(MSG_CMD_DISMISS_ALERT)
    }

    fun setSpeedLimit(speedLimitKmh: Float) {
        sendCommand(MSG_CMD_SET_SPEED_LIMIT, speedLimitKmh.toInt())
    }

    fun setDrowsinessAlert(enabled: Boolean) {
        sendCommand(MSG_CMD_SET_DROWSY_ALERT, if (enabled) 1 else 0)
        if (isTrackingFlow.value) {
            if (enabled) LocationHelper.scheduleDrowsinessWorker(getApplication(), true)
            else LocationHelper.cancelDrowsinessWorker(getApplication())
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
        }
    }

    fun performSearch() {
        val query = _searchQuery.value
        if (query.isBlank()) return

        viewModelScope.launch {
            _isSearching.value = true
            val results = searchLocations(query)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun selectDestination(result: OsmSearchResult) {
        _destination.value = result
        _searchResults.value = emptyList()
        _searchQuery.value = result.displayName

        val userLocation = locationFlow.value
        if (userLocation.hasFix) {
            viewModelScope.launch {
                val start = GeoPoint(userLocation.latitude, userLocation.longitude)
                val end = GeoPoint(result.lat, result.lon)
                _route.value = fetchDrivingRoute(start, end)
            }
        }
    }

    fun refreshCurrentLocation() {
        val app = getApplication<Application>()
        if (!LocationHelper.hasLocationPermission(app)) return

        LocationServices.getFusedLocationProviderClient(app)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) updateLocation(location)
                else LocationServices.getFusedLocationProviderClient(app).lastLocation
                    .addOnSuccessListener { lastLocation ->
                        lastLocation?.let(::updateLocation)
                    }
            }
    }

    private fun updateLocation(location: android.location.Location) {
        _locationFlow.value = LocationData(
            location.latitude,
            location.longitude,
            location.speed.coerceAtLeast(0f) * 3.6f,
            true
        )
    }

    override fun onCleared() {
        try {
            val msg = Message.obtain(null, MSG_UNREGISTER_CLIENT)
            msg.replyTo = clientMessenger
            serviceMessenger?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
        super.onCleared()
    }
}
