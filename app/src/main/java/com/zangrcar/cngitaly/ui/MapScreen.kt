package com.zangrcar.cngitaly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zangrcar.cngitaly.MainUiState
import com.zangrcar.cngitaly.MainViewModel
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DataStatus { FRESH, OLD, OFFLINE }

@Composable
fun MapScreen(
    mapView: MapView,
    locationMessage: String?,
    onLocationMessageShown: () -> Unit,
    onCurrentLocationClick: () -> Unit,
    searchAreaVisible: Boolean,
    onSearchThisAreaClick: () -> Unit,
    viewModel: MainViewModel
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(locationMessage) {
        locationMessage?.let {
            snackbarHostState.showSnackbar(it)
            onLocationMessageShown()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    uiState = uiState,
                    onClose = { coroutineScope.launch { drawerState.close() } },
                    onRefresh = viewModel::refresh
                )
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
                Icon(Icons.Default.Menu, "Open menu", tint = Color.White)
            }
            DataStatusButton(
                uiState = uiState,
                onClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(statusExplanation(uiState))
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 64.dp, end = 8.dp)
            )
            if (searchAreaVisible) {
                Button(
                    onClick = onSearchThisAreaClick,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                ) {
                    Text("Search this area")
                }
            }
            FilledIconButton(
                onClick = onCurrentLocationClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.MyLocation, "Current location")
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

@Composable
private fun DrawerContent(
    uiState: MainUiState,
    onClose: () -> Unit,
    onRefresh: () -> Unit
) {
    val meta = uiState.metadata
    Column(modifier = Modifier.padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CNG Italy", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close menu")
            }
        }
        Spacer(Modifier.height(24.dp))
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
        DataStatus.OLD -> Icons.Default.Warning
        DataStatus.OFFLINE -> Icons.Default.CloudOff
    }
    val container = when (status) {
        DataStatus.FRESH -> Color(0xFF1B5E20)
        DataStatus.OLD -> Color(0xFFF9A825)
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

private fun dataStatus(state: MainUiState): DataStatus = when {
    !state.isOnline -> DataStatus.OFFLINE
    isLocalDataFresh(state) -> DataStatus.FRESH
    else -> DataStatus.OLD
}

private fun isLocalDataFresh(state: MainUiState): Boolean {
    val meta = state.metadata ?: return false
    val zone = ZoneId.of("Europe/Rome")
    val today = LocalDate.now(zone)
    val refreshedDate = Instant.ofEpochMilli(meta.lastSuccessfulRefreshEpochMillis)
        .atZone(zone).toLocalDate()
    val datasetDate = runCatching { LocalDate.parse(meta.priceDatasetDate) }.getOrNull()
        ?: return false
    return refreshedDate == today && (datasetDate == today || datasetDate == today.minusDays(1))
}

private fun statusExplanation(state: MainUiState): String {
    val meta = state.metadata
    if (!state.isOnline) {
        if (meta == null) return "Offline. No local station data."
        val age = if (isLocalDataFresh(state)) "" else "old "
        return "Offline. Using ${age}MIMIT data from ${formatDatasetDate(meta.priceDatasetDate, true)}."
    }
    if (isLocalDataFresh(state)) {
        return "Station data refreshed today. MIMIT data: ${formatDatasetDate(meta?.priceDatasetDate, true)}."
    }
    return if (meta == null) {
        "No local station data. Open the menu to refresh."
    } else {
        "Station data is old. Open the menu to refresh."
    }
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
