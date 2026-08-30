package com.zangrcar.cngitaly

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import com.zangrcar.cngitaly.ui.theme.CNGItalyTheme
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

class MainActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var loadedStyle: Style? = null
    private var hasCenteredOnLocation = false
    private var centerWhenLocationArrives = false
    private var requestingCenterLocation = false
    private var snackbarMessage by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            enableLocationAndCenter(showWaiting = true)
        } else {
            snackbarMessage = "Location permission is needed to show your current position."
        }
    }

    private val centerLocationCallback = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult?) {
            val location = result?.lastLocation
            if (location != null) {
                requestingCenterLocation = false
                map?.locationComponent?.locationEngine?.removeLocationUpdates(this)
                map?.locationComponent?.forceLocationUpdate(location)
                if (centerWhenLocationArrives) centerMapOn(location)
            } else {
                requestLocationUpdate()
            }
        }

        override fun onFailure(exception: Exception) {
            requestingCenterLocation = false
            snackbarMessage = "Waiting for location…"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasCenteredOnLocation = savedInstanceState?.getBoolean(HAS_CENTERED_KEY) == true
        MapLibre.getInstance(this)
        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            this.map = map
            map.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                loadedStyle = style
                if (hasForegroundLocationPermission()) {
                    enableLocationAndCenter(showWaiting = false)
                }
            }
            map.uiSettings.isLogoEnabled = true
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isCompassEnabled = true

            val margin = (8 * resources.displayMetrics.density).toInt()
            ViewCompat.setOnApplyWindowInsetsListener(mapView) { _, insets ->
                val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                map.uiSettings.setCompassMargins(
                    margin,
                    statusBarTop + margin,
                    margin,
                    margin
                )
                insets
            }
            ViewCompat.requestApplyInsets(mapView)

            if (savedInstanceState == null) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(42.5, 12.5))
                    .zoom(5.5)
                    .build()
            }
        }
        enableEdgeToEdge()
        setContent {
            CNGItalyTheme {
                MapScreen(
                    mapView = mapView,
                    snackbarMessage = snackbarMessage,
                    onSnackbarShown = { snackbarMessage = null },
                    onCurrentLocationClick = ::onCurrentLocationClick
                )
            }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(HAS_CENTERED_KEY, hasCenteredOnLocation)
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        map?.locationComponent?.locationEngine?.removeLocationUpdates(centerLocationCallback)
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun onCurrentLocationClick() {
        if (hasForegroundLocationPermission()) {
            enableLocationAndCenter(showWaiting = true, forceCenter = true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasForegroundLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun enableLocationAndCenter(showWaiting: Boolean, forceCenter: Boolean = false) {
        val map = map ?: return
        val style = loadedStyle ?: return
        if (!hasForegroundLocationPermission()) return

        try {
            val locationComponent = map.locationComponent
            if (!locationComponent.isLocationComponentActivated) {
                locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, style)
                        .useDefaultLocationEngine(true)
                        .build()
                )
            }
            locationComponent.isLocationComponentEnabled = true
            locationComponent.renderMode = RenderMode.NORMAL
            locationComponent.cameraMode = CameraMode.NONE

            if (forceCenter || !hasCenteredOnLocation) {
                centerWhenLocationArrives = true
                val location = locationComponent.lastKnownLocation
                if (location != null) {
                    centerMapOn(location)
                } else {
                    if (showWaiting) snackbarMessage = "Waiting for location…"
                    requestingCenterLocation = false
                    locationComponent.locationEngine?.getLastLocation(centerLocationCallback)
                }
            }
        } catch (_: SecurityException) {
            snackbarMessage = "Location permission is needed to show your current position."
        }
    }

    private fun requestLocationUpdate() {
        if (requestingCenterLocation || !hasForegroundLocationPermission()) return
        val engine = map?.locationComponent?.locationEngine ?: return
        requestingCenterLocation = true
        try {
            engine.requestLocationUpdates(
                LocationEngineRequest.Builder(1_000L)
                    .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                    .build(),
                centerLocationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            requestingCenterLocation = false
        }
    }

    private fun centerMapOn(location: Location) {
        centerWhenLocationArrives = false
        hasCenteredOnLocation = true
        val map = map ?: return
        map.locationComponent.cameraMode = CameraMode.NONE
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                10.5
            ),
            700
        )
    }

    companion object {
        private const val HAS_CENTERED_KEY = "has_centered_on_location"
    }
}

@Composable
private fun MapScreen(
    mapView: MapView,
    snackbarMessage: String?,
    onSnackbarShown: () -> Unit,
    onCurrentLocationClick: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            onSnackbarShown()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "CNG Italy", style = MaterialTheme.typography.headlineSmall)
                        IconButton(onClick = { coroutineScope.launch { drawerState.close() } }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close menu")
                        }
                    }
                    Text(text = "Map ready", modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            IconButton(
                onClick = { coroutineScope.launch { drawerState.open() } },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .size(48.dp)
                    .background(Color.Black, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open menu",
                    tint = Color.White
                )
            }
            FilledIconButton(
                onClick = onCurrentLocationClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Current location"
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 72.dp)
            )
        }
    }
}
