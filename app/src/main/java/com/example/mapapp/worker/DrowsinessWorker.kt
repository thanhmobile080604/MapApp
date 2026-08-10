package com.example.mapapp.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mapapp.MainActivity
import com.example.mapapp.R
import com.example.mapapp.data.LocationRepository
import com.example.mapapp.data.model.AlertType
import com.example.mapapp.service.LocationForegroundService

class DrowsinessWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "drowsiness_alert_channel"
        const val NOTIFICATION_ID = 2001
    }

    private fun createChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Drowsiness Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts when user may be drowsy" }
            manager.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        createChannel()
        if (!LocationForegroundService.isTracking) return Result.success()
        if (!LocationForegroundService.drowsinessEnabled) return Result.success()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val awakeIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = "ACTION_AWAKE"
        }
        val awakePending = PendingIntent.getActivity(
            applicationContext,
            1,
            awakeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Stay Awake!")
            .setContentText("Potential drowsiness detected. Tap to confirm you are awake.")
            .setContentIntent(pending)
            .addAction(android.R.drawable.ic_media_pause, "Awake", awakePending)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
        LocationRepository.recordAlert(AlertType.DROWSINESS)
        return Result.success()
    }
}
