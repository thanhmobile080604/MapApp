package com.example.mapapp.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mapapp.viewmodel.OsmSearchResult
import com.example.mapapp.worker.DrowsinessWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object LocationHelper {

    fun checkLocationEnabled(context: Context) {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val isLocationEnabled =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                LocationManagerCompat.isLocationEnabled(locationManager)
            }

        if (!isLocationEnabled) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun getRequiredPermissions(): Array<String> {
        return buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    fun scheduleDrowsinessWorker(context: Context, enabled: Boolean) {
        if (!enabled) {
            cancelDrowsinessWorker(context)
            return
        }

        val workRequest = PeriodicWorkRequestBuilder<DrowsinessWorker>(
            15, TimeUnit.MINUTES
        )
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "DrowsinessAlertWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cancelDrowsinessWorker(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("DrowsinessAlertWork")
    }

    suspend fun searchLocations(query: String): List<OsmSearchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = URLEncoder.encode(query.trim(), "UTF-8")
                val connection =
                    URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5")
                        .openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "NaviMapViewer/2.0")
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                val results =
                    JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                List(results.length()) { index ->
                    val item = results.getJSONObject(index)
                    OsmSearchResult(
                        item.optString("display_name"),
                        item.getDouble("lat"),
                        item.getDouble("lon")
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun fetchDrivingRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> =
        withContext(Dispatchers.IO) {
            runCatching {
                val address =
                    "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "NaviMapViewer/2.0")
                connection.connectTimeout = 6_000
                connection.readTimeout = 6_000
                val coordinates =
                    JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        .getJSONArray("routes").getJSONObject(0).getJSONObject("geometry")
                        .getJSONArray("coordinates")
                List(coordinates.length()) { index ->
                    coordinates.getJSONArray(index)
                        .let { GeoPoint(it.getDouble(1), it.getDouble(0)) }
                }
            }.getOrElse { listOf(start, end) }
        }
}
