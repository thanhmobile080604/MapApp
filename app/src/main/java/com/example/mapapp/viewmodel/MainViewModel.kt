package com.example.mapapp.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mapapp.data.LocationData
import com.example.mapapp.data.model.UserConfig
import com.example.mapapp.service.LocationForegroundService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.example.mapapp.util.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OsmSearchResult(val displayName: String, val lat: Double, val lon: Double)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private var locationService: LocationForegroundService? = null

    private val _locationFlow = MutableStateFlow(LocationData())
    val locationFlow: StateFlow<LocationData> = _locationFlow.asStateFlow()

    private val _isTrackingFlow = MutableStateFlow(false)
    val isTrackingFlow: StateFlow<Boolean> = _isTrackingFlow.asStateFlow()

    private val _configFlow = MutableStateFlow(UserConfig())
    val configFlow: StateFlow<UserConfig> = _configFlow.asStateFlow()

    private val _isLocationEnabledFlow = MutableStateFlow(true)
    val isLocationEnabledFlow: StateFlow<Boolean> = _isLocationEnabledFlow.asStateFlow()

    // Search and Routing State
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

    fun onServiceConnected(service: LocationForegroundService) {
        this.locationService = service
        viewModelScope.launch {
            launch { service.locationFlow.collect { _locationFlow.value = it } }
            launch { service.isTrackingFlow.collect { _isTrackingFlow.value = it } }
            launch { service.configFlow.collect { _configFlow.value = it } }
            launch { service.isLocationEnabledFlow.collect { _isLocationEnabledFlow.value = it } }
        }
    }

    fun onServiceDisconnected() {
        this.locationService = null
    }

    fun startTracking() {
        val intent = Intent(getApplication(), LocationForegroundService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
        LocationHelper.scheduleDrowsinessWorker(getApplication(), configFlow.value.drowsinessAlertEnabled)
    }

    fun stopTracking() {
        val intent = Intent(getApplication(), LocationForegroundService::class.java)
        intent.action = LocationForegroundService.ACTION_STOP
        getApplication<Application>().stopService(intent)
        LocationHelper.cancelDrowsinessWorker(getApplication())
    }

    fun setSpeedLimit(speedLimitKmh: Float) {
        locationService?.setSpeedLimit(speedLimitKmh.toInt())
    }

    fun setDrowsinessAlert(enabled: Boolean) {
        locationService?.setDrowsinessEnabled(enabled)
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

    private suspend fun searchLocations(query: String): List<OsmSearchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val connection = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5")
                    .openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "NaviMapViewer/2.0")
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                val results = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                List(results.length()) { index ->
                    val item = results.getJSONObject(index)
                    OsmSearchResult(
                        item.optString("display_name"),
                        item.getDouble("lat"),
                        item.getDouble("lon")
                    )
                }
            }.getOrDefault(emptyList())
        }

    private suspend fun fetchDrivingRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> =
        withContext(Dispatchers.IO) {
            runCatching {
                val address = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "NaviMapViewer/2.0")
                connection.connectTimeout = 6_000
                connection.readTimeout = 6_000
                val coordinates = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .getJSONArray("routes").getJSONObject(0).getJSONObject("geometry")
                    .getJSONArray("coordinates")
                List(coordinates.length()) { index ->
                    coordinates.getJSONArray(index).let { GeoPoint(it.getDouble(1), it.getDouble(0)) }
                }
            }.getOrElse { listOf(start, end) }
        }

    /** Fetches a position for the map without starting persistent trip tracking. */
    fun refreshCurrentLocation() {
        val app = getApplication<Application>()
        if (!LocationHelper.hasLocationPermission(app)) return

        LocationServices.getFusedLocationProviderClient(app)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) updateLocation(location)
                else LocationServices.getFusedLocationProviderClient(app).lastLocation
                    .addOnSuccessListener { lastLocation -> lastLocation?.let(::updateLocation) }
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
}
