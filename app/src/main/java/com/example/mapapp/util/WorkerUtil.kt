package com.example.mapapp.util

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mapapp.worker.DrowsinessWorker
import java.util.concurrent.TimeUnit

fun scheduleDrowsinessWorker(context: Context, enabled: Boolean) {
    if (!enabled) {
        cancelDrowsinessWorker(context)
        return
    }

    val workRequest = PeriodicWorkRequestBuilder<DrowsinessWorker>(
        15, TimeUnit.MINUTES
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "DrowsinessAlertWork",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}

fun cancelDrowsinessWorker(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork("DrowsinessAlertWork")
}
