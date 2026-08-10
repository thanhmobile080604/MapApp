package com.example.mapapp.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.mapapp.R
import com.example.mapapp.data.LocationRepository
import com.example.mapapp.data.model.AlertType
import com.google.android.gms.location.*
import com.google.android.gms.location.FusedLocationProviderClient

class LocationForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.mapapp.ACTION_STOP"
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var wasOverSpeedLimit = false

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
        LocationRepository.updateLocation(loc.latitude, loc.longitude, speedKmh)
        val isOverSpeedLimit = speedKmh > LocationRepository.configFlow.value.speedLimitKmh
        if (isOverSpeedLimit && !wasOverSpeedLimit) {
            LocationRepository.recordAlert(AlertType.SPEED)
        }
        wasOverSpeedLimit = isOverSpeedLimit
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            LocationRepository.setTracking(false)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundService()
        LocationRepository.setTracking(true)
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        LocationRepository.setTracking(false)
        super.onDestroy()
        stopLocationUpdates()
        stopForeground(true)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
