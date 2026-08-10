package com.example.mapapp.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.example.mapapp.MainActivity
import com.example.mapapp.R
import com.example.mapapp.data.LocationData
import com.example.mapapp.data.LocationRepository
import com.example.mapapp.data.model.AlertType
import com.example.mapapp.data.model.UserConfig
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Random

class LocationForegroundService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): LocationForegroundService = this@LocationForegroundService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "location_service_channel"
        const val ALERT_CHANNEL_ID = "alert_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002
        const val ACTION_STOP = "com.example.mapapp.ACTION_STOP"

        @Volatile
        var isTracking: Boolean = false
            private set

        @Volatile
        var drowsinessEnabled: Boolean = false
            private set
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var wasOverSpeedLimit = false
    private var isSpeedAlertActive = false
    private var simulationJob: Job? = null

    // Moved from Repository
    private val _locationFlow = MutableStateFlow(LocationData())
    val locationFlow: StateFlow<LocationData> = _locationFlow

    private val _isTrackingFlow = MutableStateFlow(false)
    val isTrackingFlow: StateFlow<Boolean> = _isTrackingFlow

    private val _configFlow = MutableStateFlow(UserConfig())
    val configFlow: StateFlow<UserConfig> = _configFlow

    private val _isLocationEnabledFlow = MutableStateFlow(true)
    val isLocationEnabledFlow: StateFlow<Boolean> = _isLocationEnabledFlow

    private val _speedAlertFlow = MutableSharedFlow<Float>()
    val speedAlertFlow: SharedFlow<Float> = _speedAlertFlow

    private val _isSimulationActiveFlow = MutableStateFlow(false)
    val isSimulationActiveFlow: StateFlow<Boolean> = _isSimulationActiveFlow

    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                checkGpsStatus()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    handleLocation(loc)
                }
            }
        }
        checkGpsStatus()
        registerReceiver(gpsReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        startLocationUpdates()
    }

    private fun checkGpsStatus() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            androidx.core.location.LocationManagerCompat.isLocationEnabled(lm)
        }
        _isLocationEnabledFlow.value = isEnabled
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Location Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Provides foreground location updates" }
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                val alertChannel = NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Speed limit and other alerts" }
                manager.createNotificationChannel(alertChannel)
            }
        }
    }

    private fun handleLocation(loc: Location) {
        val rawSpeedKmh = if (loc.speed >= 0) (loc.speed * 3.6f) else 0f
        // Filter out GPS jitter noise when stationary
        val speedKmh = if (rawSpeedKmh < 1.5f) 0f else rawSpeedKmh

        processSpeedUpdate(loc.latitude, loc.longitude, speedKmh)

        if (_isTrackingFlow.value) {
            updateForegroundNotification(loc.latitude, loc.longitude, speedKmh)
        }
    }

    private fun processSpeedUpdate(lat: Double, lon: Double, speedKmh: Float) {
        _locationFlow.value = LocationData(lat, lon, speedKmh, true)

        if (_isTrackingFlow.value) {
            val limit = _configFlow.value.speedLimitKmh
            val isOverSpeedLimit = speedKmh > limit
            
            if (isOverSpeedLimit && !isSpeedAlertActive) {
                isSpeedAlertActive = true
                LocationRepository.recordAlert(AlertType.SPEED)
                showSpeedAlertNotification(speedKmh, limit)
                serviceScope.launch {
                    _speedAlertFlow.emit(speedKmh)
                }
            }
            wasOverSpeedLimit = isOverSpeedLimit
        }
    }

    private fun updateForegroundNotification(lat: Double, lon: Double, speedKmh: Float) {
        val notification = createNotification(
            "Tracking Active",
            "Lat: ${"%.4f".format(lat)}, Lon: ${"%.4f".format(lon)}, Speed: ${"%.1f".format(speedKmh)} km/h"
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(title: String, content: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundService() {
        val notification = createNotification("Tracking Started", "Monitoring location...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startLocationUpdates() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .setMaxUpdateDelayMillis(10_000L)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun stopLocationUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    fun startSpeedSimulation() {
        if (_isSimulationActiveFlow.value) return
        _isSimulationActiveFlow.value = true
        setSpeedLimit(10)
        
        simulationJob?.cancel()
        simulationJob = serviceScope.launch {
            val random = java.util.Random()
            while (isActive) {
                val simulatedSpeed = random.nextFloat() * 20f // 0 to 20 km/h
                val currentLoc = _locationFlow.value
                processSpeedUpdate(currentLoc.latitude, currentLoc.longitude, simulatedSpeed)
                if (_isTrackingFlow.value) {
                    updateForegroundNotification(currentLoc.latitude, currentLoc.longitude, simulatedSpeed)
                }
                delay(3000)
            }
        }
    }

    fun stopSpeedSimulation() {
        _isSimulationActiveFlow.value = false
        simulationJob?.cancel()
        isSpeedAlertActive = false
    }

    fun dismissSpeedAlert() {
        isSpeedAlertActive = false
    }

    private fun showSpeedAlertNotification(currentSpeed: Float, limit: Int) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Speed Limit Exceeded!")
            .setContentText("Your speed: ${"%.1f".format(currentSpeed)} km/h. Limit: $limit km/h")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            _isTrackingFlow.value = false
            isTracking = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            // Note: Service remains running for updates if bound
            return START_NOT_STICKY
        }
        startForegroundService()
        _isTrackingFlow.value = true
        isTracking = true
        // Location updates already started in onCreate
        return START_STICKY
    }

    override fun onDestroy() {
        _isTrackingFlow.value = false
        isTracking = false
        stopLocationUpdates()
        serviceScope.cancel()
        try {
            unregisterReceiver(gpsReceiver)
        } catch (_: Exception) {
            // Already unregistered
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun setSpeedLimit(limit: Int) {
        _configFlow.value = _configFlow.value.copy(speedLimitKmh = limit)
    }

    fun setDrowsinessEnabled(enabled: Boolean) {
        _configFlow.value = _configFlow.value.copy(drowsinessAlertEnabled = enabled)
        drowsinessEnabled = enabled
    }
}
