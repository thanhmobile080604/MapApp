package com.example.mapapp.util

import android.content.Context
import android.content.Context.*
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.location.LocationManagerCompat

fun checkLocationEnabled(context: Context) {

    val locationManager =
        context.getSystemService(LOCATION_SERVICE) as LocationManager

    val isLocationEnabled =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            LocationManagerCompat.isLocationEnabled(locationManager)
        }

    if (!isLocationEnabled) {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        context.startActivity(intent)
    }
}