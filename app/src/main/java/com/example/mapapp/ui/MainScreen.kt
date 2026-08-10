package com.example.mapapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mapapp.util.checkLocationEnabled
import com.example.mapapp.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val cartoVoyagerTiles = XYTileSource(
    "CartoDBVoyager", 0, 19, 256, ".png", arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
    )
)

private const val USER_MARKER_ID = "user_location"
private const val DESTINATION_MARKER_ID = "destination_location"

private data class OsmSearchResult(val displayName: String, val lat: Double, val lon: Double)

private suspend fun searchLocations(query: String): List<OsmSearchResult> =
    withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val connection =
                URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5")
                    .openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "NaviMapViewer/2.0")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            val results = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
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

private suspend fun fetchDrivingRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> =
    withContext(Dispatchers.IO) {
        runCatching {
            val address =
                "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude}${end.longitude},${end.latitude}?overview=full&geometries=geojson"
            val connection = URL(address).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "NaviMapViewer/2.0")
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            val coordinates =
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .getJSONArray("routes").getJSONObject(0).getJSONObject("geometry")
                    .getJSONArray("coordinates")
            List(coordinates.length()) { index ->
                coordinates.getJSONArray(index).let { GeoPoint(it.getDouble(1), it.getDouble(0)) }
            }
        }.getOrElse { listOf(start, end) }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val location by viewModel.locationFlow.collectAsState()
    val config by viewModel.configFlow.collectAsState()
    val isTracking by viewModel.isTrackingFlow.collectAsState()
    val hasLocation = location.hasFix
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSpeedAlertDialog by remember { mutableStateOf(false) }
    var hasShownSpeedAlert by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(emptyList<OsmSearchResult>()) }
    var isSearching by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<OsmSearchResult?>(null) }
    var route by remember { mutableStateOf(emptyList<GeoPoint>()) }

    val locationPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }

    fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasLocationPermission()) {
                checkLocationEnabled(context)
                viewModel.refreshCurrentLocation()
            } else Toast.makeText(context, "Location permission denied", Toast.LENGTH_LONG).show()
        }
    val overSpeedLimit = isTracking && location.hasFix && location.speedKmh > config.speedLimitKmh.toFloat()

    LaunchedEffect(overSpeedLimit, isTracking, config.speedLimitKmh) {
        if (overSpeedLimit && !hasShownSpeedAlert) {
            showSpeedAlertDialog = true
            hasShownSpeedAlert = true
        } else if (!overSpeedLimit) {
            hasShownSpeedAlert = false
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission()) {
            checkLocationEnabled(context)
            viewModel.refreshCurrentLocation()
        } else permissionLauncher.launch(locationPermissions)
    }

    if (showSpeedAlertDialog) {
        AlertDialog(
            onDismissRequest = {
                showSpeedAlertDialog = false
                hasShownSpeedAlert = false
            },
            title = { Text("Speed limit exceeded") },
            text = { Text("You are driving faster than the selected speed limit.") },
            confirmButton = {
                TextButton(onClick = {
                    showSpeedAlertDialog = false
                    hasShownSpeedAlert = false
                }) { Text("OK") }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { CenterAlignedTopAppBar(title = { Text("Visual-GNSS Navigation") }) },
        bottomBar = {
            TrackingControls(
                isTracking = isTracking,
                speedKmh = location.speedKmh,
                speedLimitKmh = config.speedLimitKmh,
                drowsinessEnabled = config.drowsinessAlertEnabled,
                onTrackingClick = {
                    if (isTracking) {
                        viewModel.stopTracking()
                    } else if (hasLocationPermission()) {
                        viewModel.startTracking()
                    } else permissionLauncher.launch(locationPermissions)
                },
                onSpeedLimitChange = viewModel::setSpeedLimit,
                onDrowsinessChange = viewModel::setDrowsinessAlert
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapContext ->
                    MapView(mapContext).apply {
                        setTileSource(cartoVoyagerTiles)
                        setMultiTouchControls(true)
                        controller.setZoom(3.0)
                        controller.setCenter(GeoPoint(0.0, 0.0))
                        mapView = this
                    }
                },
                update = { map ->
                    val userPoint =
                        location.takeIf { hasLocation }?.let {
                            GeoPoint(it.latitude, it.longitude)
                        }
                    map.updateMarkers(userPoint, destination, route)
                    if (userPoint != null && !hasCenteredOnUser) {
                        map.controller.setCenter(userPoint)
                        map.controller.setZoom(16.0)
                        hasCenteredOnUser = true
                    }
                    map.invalidate()
                }
            )
            SearchPanel(
                searchQuery,
                results,
                isSearching,
                onQueryChange = {
                    searchQuery = it
                    if (it.isBlank()) results = emptyList()
                },
                onSearch = {
                    isSearching = true
                    scope.launch {
                        results = searchLocations(searchQuery)
                        isSearching = false
                    }
                },
                onResult = { selected ->
                    destination = selected
                    results = emptyList()
                    searchQuery = selected.displayName
                    val end = GeoPoint(selected.lat, selected.lon)
                    mapView?.controller?.animateTo(end)
                    if (hasLocation) scope.launch {
                        route =
                            fetchDrivingRoute(GeoPoint(location.latitude, location.longitude), end)
                    }
                    else Toast.makeText(context, "Waiting for current location", Toast.LENGTH_SHORT)
                        .show()
                })
            FloatingActionButton(
                onClick = {
                    if (hasLocation) {
                        mapView?.controller?.animateTo(
                            GeoPoint(
                                location.latitude,
                                location.longitude
                            )
                        )
                        mapView?.controller?.setZoom(16.0)
                    } else {
                        viewModel.refreshCurrentLocation()
                        Toast.makeText(
                            context,
                            "Getting current location…",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) { Icon(Icons.Default.MyLocation, "Current location") }
        }
    }
}

private fun MapView.updateMarkers(
    user: GeoPoint?,
    destination: OsmSearchResult?,
    route: List<GeoPoint>
) {
    overlays.filterIsInstance<Marker>().firstOrNull {
        it.id == USER_MARKER_ID
    }.also { marker ->
        if (user == null) marker?.let(overlays::remove) else (marker ?: Marker(this).apply {
            id = USER_MARKER_ID
            title = "Your location"
            setAnchor(
                Marker.ANCHOR_CENTER,
                Marker.ANCHOR_BOTTOM
            )
            overlays.add(this)
        }).position = user
    }
    overlays.filterIsInstance<Marker>().firstOrNull {
        it.id == DESTINATION_MARKER_ID
    }
        .also { marker ->
            if (destination == null) marker?.let(overlays::remove) else (marker
                ?: Marker(this).apply {
                    id = DESTINATION_MARKER_ID
                    setAnchor(
                        Marker.ANCHOR_CENTER,
                        Marker.ANCHOR_BOTTOM
                    )
                    overlays.add(this)
                }).apply {
                position = GeoPoint(destination.lat, destination.lon)
                title = destination.displayName
            }
        }
    overlays.removeAll { it is Polyline }
    if (route.isNotEmpty()) overlays.add(Polyline(this).apply {
        outlinePaint.color = Color.BLACK
        outlinePaint.strokeWidth = 10f
        setPoints(route)
    })
}

@Composable
private fun SearchPanel(
    query: String,
    results: List<OsmSearchResult>,
    loading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onResult: (OsmSearchResult) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        OutlinedTextField(
            query,
            onQueryChange,
            Modifier.fillMaxWidth(),
            placeholder = { Text("Search destination…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) IconButton({ onQueryChange("") }) {
                        Icon(
                            Icons.Default.Clear,
                            "Clear"
                        )
                    }
                    IconButton(onSearch) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            "Search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (results.isNotEmpty()) Card(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            LazyColumn(
                Modifier.heightIn(max = 200.dp)
            ) {
                items(results) { result ->
                    Text(
                        result.displayName,
                        Modifier
                            .fillMaxWidth()
                            .clickable { onResult(result) }
                            .padding(12.dp)
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun TrackingControls(
    isTracking: Boolean,
    speedKmh: Float,
    speedLimitKmh: Int,
    drowsinessEnabled: Boolean,
    onTrackingClick: () -> Unit,
    onSpeedLimitChange: (Float) -> Unit,
    onDrowsinessChange: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onTrackingClick) { Text(if (isTracking) "Stop tracking" else "Start tracking") }
            Text(
                "Speed: ${"%.1f".format(speedKmh)} km/h"
            )
        }
        Text("Speed limit: $speedLimitKmh km/h")
        Slider(speedLimitKmh.toFloat(), onSpeedLimitChange, valueRange = 0f..200f, steps = 19)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Drowsiness alerts")
            Switch(
                drowsinessEnabled,
                onDrowsinessChange,
                Modifier.padding(start = 8.dp)
            )
        }
    }
}
