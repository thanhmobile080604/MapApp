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
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.core.app.NotificationCompat
import com.example.mapapp.MainActivity
import com.example.mapapp.R
import com.example.mapapp.db.LocationRepository
import com.example.mapapp.model.AlertType
import com.example.mapapp.model.LocationData
import com.example.mapapp.model.UserConfig
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "location_service_channel"
        const val ALERT_CHANNEL_ID = "alert_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002
        const val ACTION_STOP = "com.example.mapapp.ACTION_STOP"

        // Message types
        const val MSG_REGISTER_CLIENT = 1
        const val MSG_UNREGISTER_CLIENT = 2
        const val MSG_LOCATION_UPDATE = 3
        const val MSG_TRACKING_STATUS = 4
        const val MSG_CONFIG_UPDATE = 5
        const val MSG_SPEED_ALERT = 6
        const val MSG_SIMULATION_STATUS = 7
        const val MSG_LOCATION_ENABLED = 8

        // Commands
        const val MSG_CMD_START_TRACKING = 101
        const val MSG_CMD_STOP_TRACKING = 102
        const val MSG_CMD_START_SIMULATION = 103
        const val MSG_CMD_STOP_SIMULATION = 104
        const val MSG_CMD_DISMISS_ALERT = 105
        const val MSG_CMD_SET_SPEED_LIMIT = 106
        const val MSG_CMD_SET_DROWSY_ALERT = 107

        // Bundle Keys
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_SPEED = "speed"
        const val KEY_FIX = "fix"
        const val KEY_LIMIT = "limit"
        const val KEY_DROWSY = "drowsy"

        @Volatile
        var isTracking: Boolean = false
            private set

        @Volatile
        var drowsinessEnabled: Boolean = false
            private set
    }

    inner class LocalBinder : Binder() {
        fun getService(): LocationForegroundService = this@LocationForegroundService
        fun getMessenger(): Messenger = serviceMessenger
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var wasOverSpeedLimit = false
    private var isSpeedAlertActive = false
    private var simulationJob: Job? = null

    // Internal State
    private var currentLocation = LocationData()
    private var userConfig = UserConfig()
    private var isLocationEnabled = true
    private var isSimulationActive = false

    private val clientMessengers = mutableListOf<Messenger>()

    private val incomingHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_REGISTER_CLIENT -> {
                    clientMessengers.add(msg.replyTo)
                    // Push current state to new client
                    sendLocationUpdate(currentLocation)
                    sendTrackingStatus(isTracking)
                    sendConfigUpdate(userConfig)
                    sendLocationEnabled(isLocationEnabled)
                    sendSimulationStatus(isSimulationActive)
                }
                MSG_UNREGISTER_CLIENT -> clientMessengers.remove(msg.replyTo)
                
                MSG_CMD_START_TRACKING -> startTrackingMode()
                MSG_CMD_STOP_TRACKING -> stopTrackingMode()
                MSG_CMD_START_SIMULATION -> startSpeedSimulation()
                MSG_CMD_STOP_SIMULATION -> stopSpeedSimulation()
                MSG_CMD_DISMISS_ALERT -> dismissSpeedAlert()
                MSG_CMD_SET_SPEED_LIMIT -> {
                    val limit = msg.arg1
                    setSpeedLimit(limit)
                }
                MSG_CMD_SET_DROWSY_ALERT -> {
                    val enabled = msg.arg1 == 1
                    setDrowsinessEnabled(enabled)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val serviceMessenger = Messenger(incomingHandler)

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
        isLocationEnabled = isEnabled
        sendLocationEnabled(isEnabled)
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
        val speedKmh = if (rawSpeedKmh < 1.5f) 0f else rawSpeedKmh

        processSpeedUpdate(loc.latitude, loc.longitude, speedKmh)

        if (isTracking) {
            updateForegroundNotification(loc.latitude, loc.longitude, speedKmh)
        }
    }

    private fun processSpeedUpdate(lat: Double, lon: Double, speedKmh: Float) {
        currentLocation = LocationData(lat, lon, speedKmh, true)
        sendLocationUpdate(currentLocation)

        if (isTracking) {
            val limit = userConfig.speedLimitKmh
            val isOverSpeedLimit = speedKmh > limit
            
            if (isOverSpeedLimit && !isSpeedAlertActive) {
                isSpeedAlertActive = true
                LocationRepository.recordAlert(AlertType.SPEED)
                showSpeedAlertNotification(speedKmh, limit)
                sendSpeedAlert(speedKmh)
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

    private fun startTrackingMode() {
        if (isTracking) return
        startForegroundService()
        isTracking = true
        sendTrackingStatus(true)
    }

    private fun stopTrackingMode() {
        if (!isTracking) return
        isTracking = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        sendTrackingStatus(false)
    }

    fun startSpeedSimulation() {
        if (isSimulationActive) return
        isSimulationActive = true
        sendSimulationStatus(true)
        setSpeedLimit(10)
        
        simulationJob?.cancel()
        simulationJob = serviceScope.launch {
            val random = java.util.Random()
            while (isActive) {
                val simulatedSpeed = random.nextFloat() * 20f // 0 to 20 km/h
                processSpeedUpdate(currentLocation.latitude, currentLocation.longitude, simulatedSpeed)
                if (isTracking) {
                    updateForegroundNotification(currentLocation.latitude, currentLocation.longitude, simulatedSpeed)
                }
                delay(3000)
            }
        }
    }

    fun stopSpeedSimulation() {
        isSimulationActive = false
        sendSimulationStatus(false)
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
            stopTrackingMode()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTrackingMode()
        stopLocationUpdates()
        serviceScope.cancel()
        try {
            unregisterReceiver(gpsReceiver)
        } catch (_: Exception) {
            // Already unregistered
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun setSpeedLimit(limit: Int) {
        userConfig = userConfig.copy(speedLimitKmh = limit)
        sendConfigUpdate(userConfig)
    }

    fun setDrowsinessEnabled(enabled: Boolean) {
        userConfig = userConfig.copy(drowsinessAlertEnabled = enabled)
        drowsinessEnabled = enabled
        sendConfigUpdate(userConfig)
    }

    // --- Message Sending Helpers ---

    private fun sendLocationUpdate(data: LocationData) {
        val msg = Message.obtain(null, MSG_LOCATION_UPDATE)
        msg.data = Bundle().apply { 
            putDouble(KEY_LAT, data.latitude)
            putDouble(KEY_LON, data.longitude)
            putFloat(KEY_SPEED, data.speedKmh)
            putBoolean(KEY_FIX, data.hasFix)
        }
        broadcastMessage(msg)
    }

    private fun sendTrackingStatus(status: Boolean) {
        val msg = Message.obtain(null, MSG_TRACKING_STATUS)
        msg.arg1 = if (status) 1 else 0
        broadcastMessage(msg)
    }

    private fun sendConfigUpdate(config: UserConfig) {
        val msg = Message.obtain(null, MSG_CONFIG_UPDATE)
        msg.data = Bundle().apply { 
            putInt(KEY_LIMIT, config.speedLimitKmh)
            putBoolean(KEY_DROWSY, config.drowsinessAlertEnabled)
        }
        broadcastMessage(msg)
    }

    private fun sendSpeedAlert(speed: Float) {
        val msg = Message.obtain(null, MSG_SPEED_ALERT)
        msg.obj = speed
        broadcastMessage(msg)
    }

    private fun sendSimulationStatus(status: Boolean) {
        val msg = Message.obtain(null, MSG_SIMULATION_STATUS)
        msg.arg1 = if (status) 1 else 0
        broadcastMessage(msg)
    }

    private fun sendLocationEnabled(status: Boolean) {
        val msg = Message.obtain(null, MSG_LOCATION_ENABLED)
        msg.arg1 = if (status) 1 else 0
        broadcastMessage(msg)
    }

    private fun broadcastMessage(msg: Message) {
        val iterator = clientMessengers.iterator()
        while (iterator.hasNext()) {
            val client = iterator.next()
            try {
                val copy = Message.obtain(msg)
                client.send(copy)
            } catch (e: RemoteException) {
                iterator.remove()
            }
        }
    }
}
