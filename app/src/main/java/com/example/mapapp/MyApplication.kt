package com.example.mapapp

import android.app.Application
import com.example.mapapp.db.LocationRepository
import org.osmdroid.config.Configuration

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = "NaviMapViewer/2.0 (Android; NavigationApp)"
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", 0))

        LocationRepository.init(this)
    }
}
