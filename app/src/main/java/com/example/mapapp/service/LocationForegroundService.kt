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
import com.example.mapapp.R
import com.example.mapapp.data.LocationData
import com.example.mapapp.data.LocationRepository
import com.example.mapapp.data.model.AlertType
import com.example.mapapp.data.model.UserConfig
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LocationForegroundService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): LocationForegroundService = this@LocationForegroundService
    }

    private val binder = LocalBinder()

    companion object {
        const val CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.mapapp.ACTION_STOP"
        const val MSG_HEARTBEAT = 1

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

    // Moved from Repository
    private val _locationFlow = MutableStateFlow(LocationData())
    val locationFlow: StateFlow<LocationData> = _locationFlow

    private val _isTrackingFlow = MutableStateFlow(false)
    val isTrackingFlow: StateFlow<Boolean> = _isTrackingFlow

    private val _configFlow = MutableStateFlow(UserConfig())
    val configFlow: StateFlow<UserConfig> = _configFlow

    private val _isLocationEnabledFlow = MutableStateFlow(true)
    val isLocationEnabledFlow: StateFlow<Boolean> = _isLocationEnabledFlow

    // Handler for messaging to Activity
    private var clientMessenger: Messenger? = null
    private var heartbeatJob: Job? = null

    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                checkGpsStatus()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    handleLocation(loc)
                }
            }
        }
        startHeartbeat()
        checkGpsStatus()
        registerReceiver(gpsReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
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

    fun setMessenger(messenger: Messenger?) {
        this.clientMessenger = messenger
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(5000)
                clientMessenger?.send(Message.obtain(null, MSG_HEARTBEAT))
            }
        }
    }

    private fun createChannel() {
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
        }
    }

    private fun startForegroundService() {
        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location tracking active")
            .setContentText("Tap STOP to end tracking")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopPending)
            .setOngoing(true)
            .build()

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

    private fun handleLocation(loc: Location) {
        val speedKmh = if (loc.speed >= 0) (loc.speed * 3.6f) else 0f
        _locationFlow.value = LocationData(loc.latitude, loc.longitude, speedKmh, true)
        val isOverSpeedLimit = speedKmh > _configFlow.value.speedLimitKmh
        if (isOverSpeedLimit && !wasOverSpeedLimit) {
            LocationRepository.recordAlert(AlertType.SPEED)
        }
        wasOverSpeedLimit = isOverSpeedLimit
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            _isTrackingFlow.value = false
            isTracking = false
            stopLocationUpdates()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundService()
        _isTrackingFlow.value = true
        isTracking = true
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        _isTrackingFlow.value = false
        isTracking = false
        stopLocationUpdates()
        heartbeatJob?.cancel()
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
