package com.zangrcar.cngitaly

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zangrcar.cngitaly.data.StationRepository
import com.zangrcar.cngitaly.data.MapBounds
import com.zangrcar.cngitaly.data.MapStation
import com.zangrcar.cngitaly.data.local.CngDatabase
import com.zangrcar.cngitaly.data.local.DatasetMetaEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class MainUiState(
    val metadata: DatasetMetaEntity? = null,
    val isOnline: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStationSearchRunning: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = CngDatabase.getInstance(application)
    private val repository = StationRepository(database.stationDao())
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val online = MutableStateFlow(hasValidatedInternet())
    private val refreshing = MutableStateFlow(false)
    private val stationSearchRunning = MutableStateFlow(false)
    private val _stations = MutableStateFlow<List<MapStation>>(emptyList())
    val stations = _stations.asStateFlow()
    private var lastSearchedBounds: MapBounds? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private val viewportSearchRequestChannel = Channel<Unit>(Channel.BUFFERED)
    val viewportSearchRequests = viewportSearchRequestChannel.receiveAsFlow()

    val uiState = combine(
        repository.metadata,
        online,
        refreshing,
        stationSearchRunning
    ) { meta, isOnline, isRefreshing, isSearching ->
        MainUiState(meta, isOnline, isRefreshing, isSearching)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateConnectivity()
        override fun onLost(network: Network) = updateConnectivity()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            updateConnectivity()
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        viewModelScope.launch {
            val (meta, isOnline) = combine(repository.metadata, online) { value, connected ->
                value to connected
            }.first { (value, connected) -> value != null || connected }
            if (meta == null && isOnline) refresh(showSuccessMessage = false)
        }
    }

    fun refresh(showSuccessMessage: Boolean = true) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                repository.refresh()
                val searchedBounds = lastSearchedBounds
                if (searchedBounds != null) {
                    searchStationsInternal(searchedBounds)
                } else {
                    viewportSearchRequestChannel.send(Unit)
                }
                if (showSuccessMessage) _messages.emit("Station data refreshed.")
            } catch (_: Exception) {
                _messages.emit("Refresh failed. Keeping existing station data.")
            } finally {
                refreshing.value = false
            }
        }
    }

    fun searchStations(bounds: MapBounds, userInitiated: Boolean = false) {
        if (stationSearchRunning.value) return
        stationSearchRunning.value = true
        viewModelScope.launch {
            try {
                logBounds(bounds)
                if (!repository.hasLocalData()) {
                    if (userInitiated) {
                        _messages.emit("No local station data. Refresh it from the menu.")
                    }
                    return@launch
                }
                val (metadataCount, storedCount) = repository.getStoredStationCounts()
                val stations = repository.getStationsInBounds(bounds)
                Log.d(LOG_TAG, "Room data: metadataStations=$metadataCount, storedStations=$storedCount")
                Log.d(LOG_TAG, "Mapped result: stations=${stations.size}")
                _stations.value = stations
                lastSearchedBounds = bounds
            } catch (_: Exception) {
                _messages.emit("Unable to load local station data.")
            } finally {
                stationSearchRunning.value = false
            }
        }
    }

    override fun onCleared() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        super.onCleared()
    }

    private fun updateConnectivity() {
        online.value = hasValidatedInternet()
    }

    private suspend fun searchStationsInternal(bounds: MapBounds) {
        stationSearchRunning.value = true
        try {
            logBounds(bounds)
            val (metadataCount, storedCount) = repository.getStoredStationCounts()
            val stations = repository.getStationsInBounds(bounds)
            Log.d(LOG_TAG, "Room data: metadataStations=$metadataCount, storedStations=$storedCount")
            Log.d(LOG_TAG, "Mapped result: stations=${stations.size}")
            _stations.value = stations
            lastSearchedBounds = bounds
        } catch (_: Exception) {
            _messages.emit("Unable to load local station data.")
        } finally {
            stationSearchRunning.value = false
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }


    private fun logBounds(bounds: MapBounds) {
        Log.d(
            LOG_TAG,
            "bounds: north=${bounds.north}, south=${bounds.south}, " +
                "east=${bounds.east}, west=${bounds.west}"
        )
    }

    companion object {
        private const val LOG_TAG = "CngMap"
    }
}
