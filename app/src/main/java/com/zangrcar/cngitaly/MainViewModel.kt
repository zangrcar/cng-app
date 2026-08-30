package com.zangrcar.cngitaly

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zangrcar.cngitaly.data.StationRepository
import com.zangrcar.cngitaly.data.MapBounds
import com.zangrcar.cngitaly.data.MapStation
import com.zangrcar.cngitaly.data.StationDetails
import com.zangrcar.cngitaly.data.local.CngDatabase
import com.zangrcar.cngitaly.data.local.DatasetMetaEntity
import com.zangrcar.cngitaly.data.mimit.LiveStationDetails
import com.zangrcar.cngitaly.data.mimit.MimitLiveClient
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

data class MainUiState(
    val metadata: DatasetMetaEntity? = null,
    val isOnline: Boolean = false,
    val isRefreshing: Boolean = false,
    val isStationSearchRunning: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = CngDatabase.getInstance(application)
    private val repository = StationRepository(database.stationDao())
    private val liveClient = MimitLiveClient()
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val online = MutableStateFlow(hasValidatedInternet())
    private val refreshing = MutableStateFlow(false)
    private val stationSearchRunning = MutableStateFlow(false)
    private val _stations = MutableStateFlow<List<MapStation>>(emptyList())
    val stations = _stations.asStateFlow()
    private val _selectedStation = MutableStateFlow<StationDetails?>(null)
    val selectedStation = _selectedStation.asStateFlow()
    private val _isStationDetailsLoading = MutableStateFlow(false)
    val isStationDetailsLoading = _isStationDetailsLoading.asStateFlow()
    private val _liveStationDetails = MutableStateFlow<LiveStationDetails?>(null)
    val liveStationDetails = _liveStationDetails.asStateFlow()
    private val _isLiveDetailsLoading = MutableStateFlow(false)
    val isLiveDetailsLoading = _isLiveDetailsLoading.asStateFlow()
    private var stationDetailsJob: Job? = null
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
                if (!repository.hasLocalData()) {
                    if (userInitiated) {
                        _messages.emit("No local station data. Refresh it from the menu.")
                    }
                    return@launch
                }
                val stations = repository.getStationsInBounds(bounds)
                _stations.value = stations
                lastSearchedBounds = bounds
            } catch (_: Exception) {
                _messages.emit("Unable to load local station data.")
            } finally {
                stationSearchRunning.value = false
            }
        }
    }

    fun selectStation(stationId: Int) {
        stationDetailsJob?.cancel()
        _selectedStation.value = null
        _liveStationDetails.value = null
        _isLiveDetailsLoading.value = false
        stationDetailsJob = viewModelScope.launch {
            _isStationDetailsLoading.value = true
            try {
                val details = repository.getStationDetails(stationId)
                if (details == null) {
                    _messages.emit("Station details are unavailable.")
                    return@launch
                }
                _selectedStation.value = details
                _isStationDetailsLoading.value = false

                if (online.value) {
                    _isLiveDetailsLoading.value = true
                    try {
                        val liveDetails = liveClient.getStationDetails(stationId)
                        if (_selectedStation.value?.id == stationId) {
                            _liveStationDetails.value = liveDetails
                        }
                    } catch (exception: Exception) {
                        if (exception is CancellationException) throw exception
                        // Live MIMIT enrichment is best-effort; local details remain authoritative.
                    } finally {
                        if (_selectedStation.value?.id == stationId) {
                            _isLiveDetailsLoading.value = false
                        }
                    }
                }
            } catch (_: Exception) {
                _messages.emit("Unable to load station details.")
            } finally {
                _isStationDetailsLoading.value = false
            }
        }
    }

    fun clearSelectedStation() {
        stationDetailsJob?.cancel()
        stationDetailsJob = null
        _isStationDetailsLoading.value = false
        _isLiveDetailsLoading.value = false
        _selectedStation.value = null
        _liveStationDetails.value = null
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
            val stations = repository.getStationsInBounds(bounds)
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
}
