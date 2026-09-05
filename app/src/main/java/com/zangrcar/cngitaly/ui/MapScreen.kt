package com.zangrcar.cngitaly.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zangrcar.cngitaly.MainUiState
import com.zangrcar.cngitaly.MainViewModel
import com.zangrcar.cngitaly.data.StationDetails
import com.zangrcar.cngitaly.data.StationPrice
import com.zangrcar.cngitaly.data.mimit.LiveCngPrice
import com.zangrcar.cngitaly.data.mimit.LiveOpenState
import com.zangrcar.cngitaly.data.mimit.LiveOpenStatus
import com.zangrcar.cngitaly.data.mimit.LiveStationDetails
import com.zangrcar.cngitaly.data.mimit.OpeningHoursEntry
import com.zangrcar.cngitaly.data.mimit.liveOpenStatus
import com.zangrcar.cngitaly.data.mimit.openingHoursLabel
import com.zangrcar.cngitaly.data.geocoding.PlaceSearchResult
import com.zangrcar.cngitaly.data.routing.RouteEndpoint
import com.zangrcar.cngitaly.data.routing.RouteResult
import com.zangrcar.cngitaly.data.routing.RouteCorridorSetting
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapView
import java.time.DayOfWeek
import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

internal enum class DataStatus { FRESH, STALE, NO_DATA, OFFLINE }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MapScreen(
    mapView: MapView,
    locationMessage: String?,
    onLocationMessageShown: () -> Unit,
    onCurrentLocationClick: () -> Unit,
    onPlaceSelected: (PlaceSearchResult) -> Unit,
    onUseNavigateLocation: () -> Unit,
    onCancelNavigateLocationRequest: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: MainViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val selectedStation = viewModel.selectedStation.collectAsStateWithLifecycle().value
    val isStationDetailsLoading =
        viewModel.isStationDetailsLoading.collectAsStateWithLifecycle().value
    val liveStationDetails = viewModel.liveStationDetails.collectAsStateWithLifecycle().value
    val isLiveDetailsLoading =
        viewModel.isLiveDetailsLoading.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    var showPlaceSearch by remember { mutableStateOf(false) }
    var showNavigate by remember { mutableStateOf(false) }
    var showAddStop by remember { mutableStateOf(false) }
    val normalSearch = viewModel.normalSearch.collectAsStateWithLifecycle().value
    val quickSearch = viewModel.quickSearch.collectAsStateWithLifecycle().value
    val closePlaceSearch: () -> Unit = {
        showPlaceSearch = false
        viewModel.clearPlaceSearch()
    }
    val activeRoute = viewModel.activeRoute.collectAsStateWithLifecycle().value
    val searchedPlace = viewModel.searchedPlaceMarker.collectAsStateWithLifecycle().value
    val corridorSetting = viewModel.routeCorridorSetting.collectAsStateWithLifecycle().value
    val isRouteLoading = viewModel.isRouteLoading.collectAsStateWithLifecycle().value
    val closeQuickSheets: () -> Unit = {
        if (showNavigate) onCancelNavigateLocationRequest()
        showNavigate = false
        showAddStop = false
        viewModel.clearQuickSearch()
    }

    LaunchedEffect(locationMessage) {
        locationMessage?.let {
            snackbarHostState.showSnackbar(it)
            onLocationMessageShown()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(isRouteLoading) {
        if (isRouteLoading && showNavigate) closeQuickSheets()
    }
    BackHandler(
        enabled = drawerState.isOpen && selectedStation == null && !isStationDetailsLoading &&
            !showPlaceSearch && !showNavigate && !showAddStop
    ) {
        coroutineScope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    uiState = uiState,
                    activeRoute = activeRoute,
                    corridorSetting = corridorSetting,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onClose = { coroutineScope.launch { drawerState.close() } },
                    onRefresh = viewModel::refresh,
                    onCorridorChange = viewModel::setRouteCorridor,
                    onApplyRoute = { endpoints ->
                        if (viewModel.applyRouteEndpoints(endpoints)) {
                            coroutineScope.launch { drawerState.close() }
                        }
                    }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                MapControlButton(
                    onClick = { coroutineScope.launch { drawerState.open() } },
                    icon = Icons.Default.Menu,
                    contentDescription = "Open menu",
                    isDarkTheme = isDarkTheme
                )
                Spacer(Modifier.height(8.dp))
                DataStatusButton(
                    uiState = uiState,
                    onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(statusExplanation(uiState))
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                MapControlButton(
                    onClick = {
                        viewModel.clearSelectedStation()
                        viewModel.clearPlaceSearch()
                        showPlaceSearch = true
                    },
                    icon = Icons.Default.Search,
                    contentDescription = "Search place",
                    isDarkTheme = isDarkTheme
                )
                if (activeRoute != null) {
                    Spacer(Modifier.height(8.dp))
                    MapControlButton(
                        onClick = {
                            viewModel.clearSelectedStation()
                            viewModel.clearQuickSearch()
                            showAddStop = true
                        },
                        icon = Icons.Default.Add,
                        contentDescription = "Add stop",
                        isDarkTheme = isDarkTheme
                    )
                }
            }
            if (activeRoute != null) {
                RouteSummary(
                    route = activeRoute,
                    onClear = viewModel::clearRoute,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
                )
            }
            if (isRouteLoading) {
                Surface(Modifier.align(Alignment.Center), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp)); Text("Calculating route…")
                    }
                }
            }
            MapControlButton(
                onClick = onCurrentLocationClick,
                icon = Icons.Default.MyLocation,
                contentDescription = "Current location",
                isDarkTheme = isDarkTheme,
                iconColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
            if (searchedPlace != null) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.clearQuickSearch()
                        showNavigate = true
                    },
                    icon = {
                        Icon(
                            Icons.Default.Directions,
                            null,
                            tint = if (isDarkTheme) Color.White else LocalContentColor.current
                        )
                    },
                    text = { Text("Navigate") },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp)
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

    if (selectedStation != null || isStationDetailsLoading) {
        ModalBottomSheet(onDismissRequest = viewModel::clearSelectedStation) {
            StationDetailsContent(
                station = selectedStation,
                isLoading = isStationDetailsLoading,
                liveDetails = liveStationDetails,
                isLiveLoading = isLiveDetailsLoading,
                uiState = uiState,
                onOpenInGoogleMaps = { station ->
                    if (!openInGoogleMaps(context, station)) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("No app can open this location.")
                        }
                    }
                }
            )
        }
    }

    if (showPlaceSearch) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                dismissOnBackPress = false,
                decorFitsSystemWindows = false
            )
        ) {
            FullScreenPlaceSearch(
                state = normalSearch,
                onQueryChange = viewModel::updateNormalSearch,
                onSubmit = viewModel::submitNormalSearch,
                onDismiss = closePlaceSearch,
                onResultSelected = { result ->
                    closePlaceSearch()
                    onPlaceSelected(result)
                }
            )
        }
    }
    if (showNavigate && searchedPlace != null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                dismissOnBackPress = false,
                decorFitsSystemWindows = false
            )
        ) {
            FullScreenNavigate(
                destination = searchedPlace,
                state = quickSearch,
                onQueryChange = viewModel::updateQuickSearch,
                onSubmit = viewModel::submitQuickSearch,
                onUseLocation = onUseNavigateLocation,
                onResultSelected = { result ->
                    if (viewModel.navigateFromPlace(result)) closeQuickSheets()
                },
                onDismiss = closeQuickSheets
            )
        }
    }
    if (showAddStop && activeRoute != null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                dismissOnBackPress = false,
                decorFitsSystemWindows = false
            )
        ) {
            FullScreenPlaceSearch(
                state = quickSearch,
                onQueryChange = viewModel::updateQuickSearch,
                onSubmit = viewModel::submitQuickSearch,
                onDismiss = closeQuickSheets,
                onResultSelected = { result ->
                    if (viewModel.addStopAndRecalculate(result)) closeQuickSheets()
                },
                placeholder = "Search stop to add"
            )
        }
    }
}

@Composable
private fun FullScreenNavigate(
    destination: PlaceSearchResult,
    state: com.zangrcar.cngitaly.PlaceTypeaheadState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onUseLocation: () -> Unit,
    onResultSelected: (PlaceSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val destinationPrimary =
        destination.name.takeIf { it.isNotBlank() } ?: destination.displayName

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(250)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    BackHandler(enabled = !imeVisible) { onDismiss() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text("Navigate", style = MaterialTheme.typography.titleLarge)
            }
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier
                        .padding(top = 28.dp)
                        .size(width = 24.dp, height = 108.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 98.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        placeholder = { Text("Choose starting point") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = if (state.query.isNotEmpty()) {
                            {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Close, "Clear search")
                                }
                            }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (com.zangrcar.cngitaly.shouldSearchPlaceQuery(state.query)) {
                                    onSubmit()
                                    keyboard?.hide()
                                }
                            }
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                onUseLocation()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(12.dp))
                        Text("Your location", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp, start = 7.dp, end = 7.dp)
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = destinationPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (
                        destination.displayName.isNotBlank() &&
                        !destination.displayName.equals(destinationPrimary, ignoreCase = true)
                    ) {
                        Text(
                            text = destination.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(state.results) { index, result ->
                    val primary = result.name.takeIf { it.isNotBlank() } ?: result.displayName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                onResultSelected(result)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = primary,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (
                                result.displayName.isNotBlank() &&
                                !result.displayName.equals(primary, ignoreCase = true)
                            ) {
                                Text(
                                    text = result.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (index < state.results.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Text(
                text = "Search data © OpenStreetMap contributors · Geocoding: Photon",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FullScreenPlaceSearch(
    state: com.zangrcar.cngitaly.PlaceTypeaheadState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onResultSelected: (PlaceSearchResult) -> Unit,
    placeholder: String = "Search Italian place or address"
) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(250)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    BackHandler(enabled = !imeVisible) { onDismiss() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    placeholder = { Text(placeholder) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = if (state.query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Close, "Clear search")
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (com.zangrcar.cngitaly.shouldSearchPlaceQuery(state.query)) {
                                onSubmit()
                                keyboard?.hide()
                            }
                        }
                    )
                )
            }
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(state.results) { index, result ->
                    val primary = result.name.takeIf { it.isNotBlank() } ?: result.displayName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                onResultSelected(result)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = primary,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (
                                result.displayName.isNotBlank() &&
                                !result.displayName.equals(primary, ignoreCase = true)
                            ) {
                                Text(
                                    text = result.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (index < state.results.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Text(
                text = "Search data © OpenStreetMap contributors · Geocoding: Photon",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MapControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    iconColor: Color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = if (isDarkTheme) Color(0xFF234A6B) else MaterialTheme.colorScheme.surface,
        contentColor = iconColor,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription)
        }
    }
}

@Composable
private fun RouteSummary(route: RouteResult, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val kilometers = (route.distanceMeters / 1000.0).toInt()
    val totalMinutes = (route.durationSeconds / 60.0).toInt()
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$kilometers km · ${totalMinutes / 60} h ${totalMinutes % 60} min",
                Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
            )
            Spacer(Modifier.size(8.dp))
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Clear route")
            }
        }
    }
}

private fun corridorLabel(setting: RouteCorridorSetting) = when (setting) {
    RouteCorridorSetting.Auto -> "Auto"
    is RouteCorridorSetting.Fixed -> "${(setting.meters / 1000).toInt()} km"
}

@Composable
private fun StationDetailsContent(
    station: StationDetails?,
    isLoading: Boolean,
    liveDetails: LiveStationDetails?,
    isLiveLoading: Boolean,
    uiState: MainUiState,
    onOpenInGoogleMaps: (StationDetails) -> Unit
) {
    if (isLoading || station == null) {
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(station.name, style = MaterialTheme.typography.headlineSmall)
        if (station.formattedAddress.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(station.formattedAddress, style = MaterialTheme.typography.bodyLarge)
        }
        StationInfo(station)
        LiveStatus(liveDetails, isLiveLoading)

        Spacer(Modifier.height(24.dp))
        Text("CNG PRICES", style = MaterialTheme.typography.labelMedium)
        if (liveDetails?.cngPrices?.isNotEmpty() == true) {
            Text("Live MIMIT", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        if (liveDetails?.cngPrices?.isNotEmpty() == true) {
            liveDetails.cngPrices.forEach { price ->
                LiveStationPriceRow(price)
                Spacer(Modifier.height(8.dp))
            }
        } else {
            station.prices.forEach { price ->
                StationPriceRow(price)
                Spacer(Modifier.height(8.dp))
            }
        }

        OpeningHoursSection(liveDetails)
        LiveContactSection(liveDetails)

        Spacer(Modifier.height(16.dp))
        Text("SOURCE", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Text("Source: MIMIT", style = MaterialTheme.typography.bodyMedium)
        Text(
            "MIMIT price data: ${formatDatasetDate(uiState.metadata?.priceDatasetDate, false)}",
            style = MaterialTheme.typography.bodyMedium
        )
        if (!isLocalDataFresh(uiState)) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Local station data is more than 24 hours old.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onOpenInGoogleMaps(station) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open in Google Maps")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LiveStatus(liveDetails: LiveStationDetails?, isLoading: Boolean) {
    if (isLoading) {
        Spacer(Modifier.height(16.dp))
        Text("Checking live details…", style = MaterialTheme.typography.bodySmall)
        return
    }
    val status = visibleLiveStatus(liveDetails?.let { liveOpenStatus(it.openingHours) }) ?: return
    Spacer(Modifier.height(16.dp))
    val label = when (status.state) {
        LiveOpenState.OPEN -> "Open now"
        LiveOpenState.CLOSED -> "Closed"
        LiveOpenState.UNKNOWN -> return
    }
    val color = when (status.state) {
        LiveOpenState.OPEN -> MaterialTheme.colorScheme.primary
        LiveOpenState.CLOSED -> MaterialTheme.colorScheme.error
        LiveOpenState.UNKNOWN -> return
    }
    Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    status.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun OpeningHoursSection(liveDetails: LiveStationDetails?) {
    if (!shouldShowOpeningHoursSection(liveDetails?.openingHours.orEmpty())) return
    Spacer(Modifier.height(16.dp))
    Text("OPENING HOURS", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(6.dp))
    DayOfWeek.entries.forEach { day ->
        val entry = liveDetails?.openingHours?.firstOrNull { it.dayOfWeek == day }
        DataRow(day.displayName(), openingHoursLabel(entry))
    }
}

internal fun visibleLiveStatus(status: LiveOpenStatus?): LiveOpenStatus? =
    status?.takeIf { it.state != LiveOpenState.UNKNOWN }

internal fun shouldShowOpeningHoursSection(
    entries: List<OpeningHoursEntry>
): Boolean = entries.any { entry ->
    !entry.isNotCommunicated && !entry.isMalformed &&
        (entry.is24Hours || entry.isClosed || entry.ranges.isNotEmpty())
}

@Composable
private fun LiveContactSection(liveDetails: LiveStationDetails?) {
    liveDetails ?: return
    val contacts = listOf(
        "Phone" to liveDetails.phoneNumber,
        "Website" to liveDetails.website,
        "Email" to liveDetails.email
    ).mapNotNull { (label, value) ->
        value?.trim()?.takeIf(String::isNotEmpty)?.let { label to it }
    }
    if (contacts.isEmpty() && liveDetails.services.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Text("LIVE DETAILS", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(6.dp))
    contacts.forEach { (label, value) -> DataRow("$label:", value) }
    if (liveDetails.services.isNotEmpty()) {
        Text(
            "Services: ${liveDetails.services.joinToString(", ")}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun DayOfWeek.displayName(): String =
    name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

@Composable
private fun StationInfo(station: StationDetails) {
    val items = listOf(
        "Brand" to station.brand,
        "Manager" to station.manager,
        "Type" to station.stationTypeLabel
    ).mapNotNull { (label, value) ->
        value?.trim()?.takeIf(String::isNotEmpty)?.let { label to it }
    }
    if (items.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    items.forEach { (label, value) -> DataRow("$label:", value) }
}

@Composable
private fun StationPriceRow(price: StationPrice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(price.priceLabel, style = MaterialTheme.typography.titleMedium)
            Text(price.serviceLabel, style = MaterialTheme.typography.labelLarge)
        }
        Text(price.fuelName, style = MaterialTheme.typography.bodySmall)
        price.communicatedLabel?.let {
            Text("Communicated $it", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LiveStationPriceRow(price: LiveCngPrice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(price.priceLabel, style = MaterialTheme.typography.titleMedium)
            Text(price.serviceLabel, style = MaterialTheme.typography.labelLarge)
        }
        Text(price.fuelName, style = MaterialTheme.typography.bodySmall)
        price.validityDate?.let {
            Text("Valid $it", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun openInGoogleMaps(context: Context, station: StationDetails): Boolean {
    val coordinates = "${station.latitude},${station.longitude}"
    val googleMapsIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=$coordinates")
    ).setPackage("com.google.android.apps.maps")
    if (runCatching { context.startActivity(googleMapsIntent) }.isSuccess) return true

    val fallbackIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/search/?api=1&query=$coordinates")
    )
    return runCatching { context.startActivity(fallbackIntent) }.isSuccess
}

@Composable
private fun DrawerContent(
    uiState: MainUiState,
    activeRoute: RouteResult?,
    corridorSetting: RouteCorridorSetting,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onCorridorChange: (RouteCorridorSetting) -> Unit,
    onApplyRoute: (List<RouteEndpoint>) -> Unit
) {
    val meta = uiState.metadata
    var routeDraft by remember(activeRoute?.endpoints) {
        mutableStateOf(activeRoute?.endpoints.orEmpty())
    }
    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CNG Italy", style = MaterialTheme.typography.headlineSmall)
            Row {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (isDarkTheme) {
                            Icons.Default.LightMode
                        } else {
                            Icons.Default.DarkMode
                        },
                        contentDescription = if (isDarkTheme) {
                            "Switch to light mode"
                        } else {
                            "Switch to dark mode"
                        }
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close menu")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (activeRoute != null) {
            Text("ROUTE", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(12.dp))
            RouteDrawerEditor(
                endpoints = routeDraft,
                originalEndpoints = activeRoute.endpoints,
                corridorSetting = corridorSetting,
                onEndpointsChange = { routeDraft = it },
                onCorridorChange = onCorridorChange,
                onApply = { onApplyRoute(routeDraft) },
                onDone = onClose
            )
            Spacer(Modifier.height(24.dp))
        }
        Text("DATA", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
        Text("Station data", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        DataRow("Last refresh:", formatRefresh(meta?.lastSuccessfulRefreshEpochMillis))
        DataRow("MIMIT price data:", formatDatasetDate(meta?.priceDatasetDate, false))
        DataRow("Stations:", meta?.stationCount?.let(::formatCount) ?: "0")
        DataRow("Connection:", if (uiState.isOnline) "Online" else "Offline")
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRefresh,
            enabled = !uiState.isRefreshing
        ) {
            if (uiState.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text("Refreshing…", modifier = Modifier.padding(start = 8.dp))
            } else {
                Text("Refresh station data")
            }
        }
    }
}

@Composable
private fun RouteDrawerEditor(
    endpoints: List<RouteEndpoint>,
    originalEndpoints: List<RouteEndpoint>,
    corridorSetting: RouteCorridorSetting,
    onEndpointsChange: (List<RouteEndpoint>) -> Unit,
    onCorridorChange: (RouteCorridorSetting) -> Unit,
    onApply: () -> Unit,
    onDone: () -> Unit
) {
    var corridorExpanded by remember { mutableStateOf(false) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val rowCenters = remember { mutableStateMapOf<Int, Float>() }
    val currentEndpoints by rememberUpdatedState(endpoints)
    val currentOnEndpointsChange by rememberUpdatedState(onEndpointsChange)

    endpoints.forEachIndexed { index, endpoint ->
        val role = when (index) {
            0 -> "From"
            endpoints.lastIndex -> "To"
            else -> "Stop $index"
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    rowCenters[index] =
                        coordinates.positionInParent().y + coordinates.size.height / 2f
                }
                .graphicsLayer {
                    translationY = if (draggingIndex == index) dragOffsetY else 0f
                }
                .zIndex(if (draggingIndex == index) 1f else 0f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoutePositionMarker(index = index, lastIndex = endpoints.lastIndex)
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = role,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = endpoint.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (index in 1 until endpoints.lastIndex) {
                    IconButton(onClick = {
                        onEndpointsChange(
                            com.zangrcar.cngitaly.data.routing.QuickRouteActions.removeStop(
                                endpoints,
                                index
                            )
                        )
                    }) {
                        Icon(Icons.Default.Delete, "Remove stop")
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y

                                val fromIndex = draggingIndex ?: return@detectDragGestures
                                val fromCenter =
                                    rowCenters[fromIndex] ?: return@detectDragGestures
                                val draggedCenter = fromCenter + dragOffsetY
                                val toIndex = rowCenters
                                    .filterKeys { it in currentEndpoints.indices }
                                    .minByOrNull { (_, center) ->
                                        abs(center - draggedCenter)
                                    }
                                    ?.key
                                    ?: return@detectDragGestures

                                if (toIndex != fromIndex) {
                                    val toCenter =
                                        rowCenters[toIndex] ?: return@detectDragGestures
                                    dragOffsetY -= toCenter - fromCenter
                                    draggingIndex = toIndex
                                    currentOnEndpointsChange(
                                        com.zangrcar.cngitaly.data.routing.QuickRouteActions
                                            .moveEndpoint(
                                                currentEndpoints,
                                                fromIndex,
                                                toIndex
                                            )
                                    )
                                }
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffsetY = 0f
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffsetY = 0f
                            }
                        )
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DragHandle, "Drag to reorder route point")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Text("Show stations within", style = MaterialTheme.typography.labelMedium)
    Box {
        OutlinedButton(onClick = { corridorExpanded = true }) { Text(corridorLabel(corridorSetting)); Text(" ▾") }
        DropdownMenu(expanded = corridorExpanded, onDismissRequest = { corridorExpanded = false }) {
            listOf(
                RouteCorridorSetting.Auto,
                RouteCorridorSetting.Fixed(3000.0),
                RouteCorridorSetting.Fixed(5000.0),
                RouteCorridorSetting.Fixed(10000.0),
                RouteCorridorSetting.Fixed(20000.0)
            ).forEach { option ->
                DropdownMenuItem(text = { Text(corridorLabel(option)) }, onClick = {
                    onCorridorChange(option); corridorExpanded = false
                })
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    val action = com.zangrcar.cngitaly.data.routing.routeDrawerAction(originalEndpoints, endpoints)
    Button(
        onClick = if (action == com.zangrcar.cngitaly.data.routing.RouteDrawerAction.DONE) onDone else onApply,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (action == com.zangrcar.cngitaly.data.routing.RouteDrawerAction.DONE) "Done" else "Apply changes") }
}

@Composable
private fun RoutePositionMarker(index: Int, lastIndex: Int) {
    when (index) {
        0 -> Box(
            modifier = Modifier
                .size(12.dp)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
        )

        lastIndex -> Box(
            modifier = Modifier
                .size(12.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )

        else -> Box(
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DataStatusButton(
    uiState: MainUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = dataStatus(uiState)
    val icon = when (status) {
        DataStatus.FRESH -> Icons.Default.CheckCircle
        DataStatus.STALE, DataStatus.NO_DATA -> Icons.Default.Warning
        DataStatus.OFFLINE -> Icons.Default.CloudOff
    }
    val container = when (status) {
        DataStatus.FRESH -> Color(0xFF1B5E20)
        DataStatus.STALE, DataStatus.NO_DATA -> Color(0xFFF9A825)
        DataStatus.OFFLINE -> Color(0xFF424242)
    }
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = Color.White
        )
    ) {
        Icon(icon, "Station data status")
    }
}

internal fun dataStatus(state: MainUiState, now: Instant = Instant.now()): DataStatus = when {
    !state.isOnline -> DataStatus.OFFLINE
    !hasUsableLocalSnapshot(state) -> DataStatus.NO_DATA
    isLocalDataFresh(state, now) -> DataStatus.FRESH
    else -> DataStatus.STALE
}

internal fun isLocalDataFresh(state: MainUiState, now: Instant = Instant.now()): Boolean {
    val meta = state.metadata ?: return false
    if (meta.stationCount <= 0) return false
    val age = Duration.between(
        Instant.ofEpochMilli(meta.lastSuccessfulRefreshEpochMillis),
        now
    )
    return !age.isNegative && age < Duration.ofHours(24)
}

private fun statusExplanation(state: MainUiState): String {
    val meta = state.metadata
    val prefix = if (state.isOnline) "" else "Offline. "
    if (!hasUsableLocalSnapshot(state) || meta == null) return "${prefix}No local station data."
    return "${prefix}Station data refreshed ${formatRelativeAge(meta.lastSuccessfulRefreshEpochMillis)} ago. " +
        "MIMIT price snapshot: ${formatDatasetDate(meta.priceDatasetDate, true)}."
}

private fun hasUsableLocalSnapshot(state: MainUiState): Boolean =
    state.metadata?.stationCount?.let { it > 0 } == true

internal fun formatRelativeAge(epochMillis: Long, now: Instant = Instant.now()): String {
    val duration = Duration.between(Instant.ofEpochMilli(epochMillis), now)
    if (duration.isNegative || duration.toMinutes() < 1) return "less than a minute"
    val minutes = duration.toMinutes()
    if (minutes < 60) return "$minutes minute${if (minutes == 1L) "" else "s"}"
    val hours = duration.toHours()
    if (hours < 24) return "$hours hour${if (hours == 1L) "" else "s"}"
    val days = duration.toDays()
    return "$days day${if (days == 1L) "" else "s"}"
}

private fun formatRefresh(epochMillis: Long?): String {
    if (epochMillis == null) return "Never"
    val zone = ZoneId.of("Europe/Rome")
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    return if (dateTime.toLocalDate() == LocalDate.now(zone)) {
        "Today, ${dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    } else {
        dateTime.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH))
    }
}

private fun formatDatasetDate(value: String?, short: Boolean): String {
    val date = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return "No local data"
    val pattern = if (short) "d MMM" else "d MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
}

private fun formatCount(count: Int): String =
    java.text.NumberFormat.getIntegerInstance(Locale.ITALY).format(count)
