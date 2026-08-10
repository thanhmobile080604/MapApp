package com.example.mapapp.ui

import android.content.ComponentName
import android.content.ServiceConnection
import android.graphics.Color
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mapapp.service.LocationForegroundService
import com.example.mapapp.util.LocationHelper
import com.example.mapapp.viewmodel.MainViewModel
import com.example.mapapp.viewmodel.OsmSearchResult
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.getValue

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val location by viewModel.locationFlow.collectAsState()
    val config by viewModel.configFlow.collectAsState()
    val isTracking by viewModel.isTrackingFlow.collectAsState()
    val isLocationEnabled by viewModel.isLocationEnabledFlow.collectAsState()
    val isSimulationActive by viewModel.isSimulationActiveFlow.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val route by viewModel.route.collectAsState()

    var showSpeedAlertDialog by remember { mutableStateOf(false) }
    var currentAlertSpeed by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        viewModel.speedAlertEvent.collect { speed ->
            currentAlertSpeed = speed
            showSpeedAlertDialog = true
        }
    }

    val hasLocation = location.hasFix
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            LocationHelper.checkLocationEnabled(context)
            viewModel.refreshCurrentLocation()
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!LocationHelper.hasLocationPermission(context)) {
            permissionLauncher.launch(LocationHelper.getRequiredPermissions())
        } else {
            LocationHelper.checkLocationEnabled(context)
            viewModel.refreshCurrentLocation()
        }
    }

    //Speed Control removed - handled by ViewModel/Service

    if (showSpeedAlertDialog) {
        AlertDialog(
            onDismissRequest = {
                showSpeedAlertDialog = false
                viewModel.onSpeedAlertDismissed()
            },
            title = { Text("Speed limit exceeded") },
            text = { Text("You are driving faster than the selected speed limit (${"%.1f".format(currentAlertSpeed)} km/h).") },
            confirmButton = {
                TextButton(onClick = {
                    showSpeedAlertDialog = false
                    viewModel.onSpeedAlertDismissed()
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
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = location.speedKmh,
                speedLimitKmh = config.speedLimitKmh,
                drowsinessEnabled = config.drowsinessAlertEnabled,
                onTrackingClick = {
                    if (isTracking) {
                        viewModel.stopTracking()
                    } else if (LocationHelper.hasLocationPermission(context)) {
                        viewModel.startTracking()
                    } else {
                        permissionLauncher.launch(LocationHelper.getRequiredPermissions())
                    }
                },
                onSpeedLimitChange = viewModel::setSpeedLimit,
                onDrowsinessChange = viewModel::setDrowsinessAlert,
                isSimulationActive = isSimulationActive,
                onTestSpeedAlert = viewModel::triggerTestSpeedAlert,
                onStopTest = viewModel::stopSpeedTest
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.weight(1f),
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
                        val userPoint = location.takeIf { hasLocation }?.let { GeoPoint(it.latitude, it.longitude) }
                        map.updateMarkers(userPoint, destination, route)
                        if (userPoint != null && !hasCenteredOnUser) {
                            map.controller.setCenter(userPoint)
                            map.controller.setZoom(16.0)
                            hasCenteredOnUser = true
                        }
                    }
                )
                if (!isLocationEnabled) {
                    LocationWarningBanner(onEnableClick = {
                        LocationHelper.checkLocationEnabled(context)
                    })
                }
            }
            SearchPanel(
                searchQuery,
                searchResults,
                isSearching,
                onQueryChange = viewModel::updateSearchQuery,
                onSearch = viewModel::performSearch,
                onResult = { selected ->
                    viewModel.selectDestination(selected)
                    mapView?.controller?.animateTo(GeoPoint(selected.lat, selected.lon))
                }
            )
            FloatingActionButton(
                onClick = {
                    val userLocation = location.takeIf { hasLocation }?.let { GeoPoint(it.latitude, it.longitude) }
                    if (userLocation != null) {
                        mapView?.controller?.animateTo(userLocation)
                        mapView?.controller?.setZoom(16.0)
                    } else {
                        viewModel.refreshCurrentLocation()
                        Toast.makeText(context, "Getting current location…", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
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
private fun LocationWarningBanner(onEnableClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Location services are disabled",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEnableClick) {
                Text("ENABLE")
            }
        }
    }
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
    latitude: Double,
    longitude: Double,
    speedKmh: Float,
    speedLimitKmh: Int,
    drowsinessEnabled: Boolean,
    onTrackingClick: () -> Unit,
    onSpeedLimitChange: (Float) -> Unit,
    onDrowsinessChange: (Boolean) -> Unit,
    isSimulationActive: Boolean,
    onTestSpeedAlert: () -> Unit,
    onStopTest: () -> Unit
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
            if (isTracking) {
                if (!isSimulationActive) {
                    OutlinedButton(onClick = onTestSpeedAlert) {
                        Text("Start Test")
                    }
                } else {
                    Button(
                        onClick = onStopTest,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Stop Test")
                    }
                }
            }
        }
        if (isTracking) {
            Text("Lat: ${"%.6f".format(latitude)}")
            Text("Lon: ${"%.6f".format(longitude)}")
            Text("Speed: ${"%.1f".format(speedKmh.coerceAtLeast(0f))} km/h")
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
