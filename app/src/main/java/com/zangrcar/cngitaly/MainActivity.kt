package com.zangrcar.cngitaly

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zangrcar.cngitaly.data.MapBounds
import com.zangrcar.cngitaly.ui.MapScreen
import com.zangrcar.cngitaly.ui.map.StationMapLayer
import com.zangrcar.cngitaly.ui.theme.CNGItalyTheme
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
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private var loadedStyle: Style? = null
    private var hasCenteredOnLocation = false
    private var centerWhenLocationArrives = false
    private var requestingCenterLocation = false
    private var locationMessage by mutableStateOf<String?>(null)
    private var searchAreaVisible by mutableStateOf(false)
    private var userGestureMovement = false
    private var stationMapLayer: StationMapLayer? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            enableLocationAndCenter(showWaiting = true, forceCenter = true)
        } else {
            locationMessage = "Location permission is needed to show your current position."
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
            locationMessage = "Waiting for location…"
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
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    userGestureMovement = true
                }
            }
            map.addOnCameraIdleListener {
                if (userGestureMovement) {
                    userGestureMovement = false
                    searchAreaVisible = true
                }
            }
            map.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                loadedStyle = style
                stationMapLayer?.destroy()
                stationMapLayer = StationMapLayer(
                    map = map,
                    style = style,
                    onStationSelected = mainViewModel::selectStation
                ).also {
                    val stations = mainViewModel.stations.value
                    if (!it.update(stations) && stations.isNotEmpty()) searchAreaVisible = true
                }
                if (hasForegroundLocationPermission()) {
                    val willCenterOnLocation = !hasCenteredOnLocation
                    enableLocationAndCenter(showWaiting = false)
                    if (!willCenterOnLocation) mapView.post { searchCurrentViewport(false) }
                } else {
                    mapView.post { searchCurrentViewport(false) }
                }
            }
            map.uiSettings.isLogoEnabled = true
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isCompassEnabled = true

            val margin = (8 * resources.displayMetrics.density).toInt()
            ViewCompat.setOnApplyWindowInsetsListener(mapView) { _, insets ->
                val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                map.uiSettings.setCompassMargins(margin, statusBarTop + margin, margin, margin)
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
        lifecycleScope.launch {
            mainViewModel.stations.collect { stations ->
                val layer = stationMapLayer
                if (layer != null && !layer.update(stations) && stations.isNotEmpty()) {
                    searchAreaVisible = true
                }
            }
        }
        lifecycleScope.launch {
            mainViewModel.viewportSearchRequests.collect {
                mapView.post { searchCurrentViewport(false) }
            }
        }
        enableEdgeToEdge()
        setContent {
            CNGItalyTheme {
                MapScreen(
                    mapView = mapView,
                    locationMessage = locationMessage,
                    onLocationMessageShown = { locationMessage = null },
                    onCurrentLocationClick = ::onCurrentLocationClick,
                    searchAreaVisible = searchAreaVisible,
                    onSearchThisAreaClick = { searchCurrentViewport(true) },
                    viewModel = mainViewModel
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
        stationMapLayer?.destroy()
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
                    if (showWaiting) locationMessage = "Waiting for location…"
                    requestingCenterLocation = false
                    locationComponent.locationEngine?.getLastLocation(centerLocationCallback)
                }
            }
        } catch (_: SecurityException) {
            locationMessage = "Location permission is needed to show your current position."
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
        searchAreaVisible = false
        map.locationComponent.cameraMode = CameraMode.NONE
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),
                10.5
            ),
            700,
            object : MapLibreMap.CancelableCallback {
                override fun onFinish() {
                    searchCurrentViewport(false)
                }

                override fun onCancel() = Unit
            }
        )
    }

    private fun searchCurrentViewport(userInitiated: Boolean) {
        val map = map ?: return
        if (loadedStyle == null) return
        if (mapView.width == 0 || mapView.height == 0) {
            mapView.post { searchCurrentViewport(userInitiated) }
            return
        }
        val bounds = map.projection.visibleRegion.latLngBounds
        mainViewModel.searchStations(
            MapBounds(
                north = bounds.latitudeNorth,
                south = bounds.latitudeSouth,
                east = bounds.longitudeEast,
                west = bounds.longitudeWest
            ),
            userInitiated = userInitiated
        )
        searchAreaVisible = false
    }

    companion object {
        private const val HAS_CENTERED_KEY = "has_centered_on_location"
    }
}
