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
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.mapapp.R
import com.example.mapapp.data.LocationRepository
import com.example.mapapp.data.model.TripHistory
import com.google.android.gms.location.*
import com.google.android.gms.location.FusedLocationProviderClient

/**
 * Foreground service that receives high‑accuracy location updates and pushes them to
 * [LocationRepository]. It shows a persistent notification with a STOP action.
 */
class LocationForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "location_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.mapapp.ACTION_STOP"
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

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
        // Intent to stop the service when user taps the action button
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
            .setSmallIcon(R.drawable.ic_launcher_foreground) // ensure drawable exists
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopPending)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {
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
        // Update live location flow
        LocationRepository.updateLocation(loc.latitude, loc.longitude, speedKmh)
        // Persist point to DB as a TripHistory entry
        val trip = TripHistory(
            latitude = loc.latitude,
            longitude = loc.longitude,
            speedKmh = speedKmh,
            maxSpeedKmh = null // can be computed later if needed
        )
        LocationRepository.insertTrip(trip)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Start service if not already foreground
        startForegroundService()
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        stopForeground(true)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
