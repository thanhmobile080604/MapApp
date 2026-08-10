package com.example.mapapp.util

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mapapp.worker.DrowsinessWorker
import java.util.concurrent.TimeUnit

/**
 * Schedules the [DrowsinessWorker] to run periodically.
 *
 * In production the interval should be at least 15 minutes (WorkManager's minimum).
 * For testing you can lower it to 8 seconds – WorkManager will clamp it to the minimum
 * on real devices, but the code shows the intended interval.
 */
fun scheduleDrowsinessWorker(context: Context) {
    // NOTE: WorkManager enforces a minimum interval of 15 minutes. For demo/testing you may
    // use a OneTimeWorkRequest with a short delay instead.
    val workRequest = PeriodicWorkRequestBuilder<DrowsinessWorker>(
        15, TimeUnit.MINUTES // Change to 8, TimeUnit.SECONDS for a quick test (will be clamped)
    ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "DrowsinessAlertWork",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}
