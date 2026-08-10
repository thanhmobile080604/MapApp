package com.example.mapapp.ui

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mapapp.viewmodel.MainViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    // Initialize osmdroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
    }

    // Permissions
    val locationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.ACCESS_FINE_LOCATION
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(locationPermission)
    }

    val location by viewModel.locationFlow.collectAsState()
    val config by viewModel.configFlow.collectAsState()
    var isTracking by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(title = { Text("Visual‑GNSS Navigation") })
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Map
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                        }
                    },
                    update = { mapView ->
                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                        mapView.controller.setZoom(15.0)
                        mapView.controller.setCenter(geoPoint)
                    }
                )
                Divider()
                // Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = {
                        if (isTracking) {
                            viewModel.stopTracking()
                        } else {
                            viewModel.startTracking()
                        }
                        isTracking = !isTracking
                    }) {
                        Text(if (isTracking) "Stop" else "Start")
                    }
                    Text(text = "Speed: ${"%.1f".format(location.speedKmh)} km/h")
                }
                // Speed limit slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(text = "Speed limit: ${config.speedLimitKmh.toInt()} km/h")
                    Slider(
                        value = config.speedLimitKmh.toFloat(),
                        onValueChange = { viewModel.setSpeedLimit(it) },
                        valueRange = 0f..200f,
                        steps = 19
                    )
                }
                // Drowsiness toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Drowsiness alerts")
                    Switch(
                        checked = config.drowsinessAlertEnabled,
                        onCheckedChange = { viewModel.setDrowsinessAlert(it) },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
