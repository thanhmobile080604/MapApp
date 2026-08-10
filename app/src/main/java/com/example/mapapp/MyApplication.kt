package com.example.mapapp

import android.app.Application
import com.example.mapapp.data.LocationRepository
import com.example.mapapp.util.scheduleDrowsinessWorker

/**
 * Application class used to initialise singletons and schedule background workers.
 */
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialise the Room database and repository
        LocationRepository.init(this)
        // Schedule the drowsiness worker (periodic background task)
        scheduleDrowsinessWorker(this)
    }
}
