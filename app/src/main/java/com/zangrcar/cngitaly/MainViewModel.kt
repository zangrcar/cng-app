package com.zangrcar.cngitaly

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zangrcar.cngitaly.data.StationRepository
import com.zangrcar.cngitaly.data.MapStation
import com.zangrcar.cngitaly.data.StationDetails
import com.zangrcar.cngitaly.data.local.CngDatabase
import com.zangrcar.cngitaly.data.local.DatasetMetaEntity
import com.zangrcar.cngitaly.data.mimit.LiveStationDetails
import com.zangrcar.cngitaly.data.mimit.MimitLiveClient
import com.zangrcar.cngitaly.data.geocoding.PhotonClient
import com.zangrcar.cngitaly.data.geocoding.PlaceSearchResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.zangrcar.cngitaly.data.routing.NoDrivingRouteException
import com.zangrcar.cngitaly.data.routing.OsrmClient
import com.zangrcar.cngitaly.data.routing.RouteEndpoint
import com.zangrcar.cngitaly.data.routing.RouteResult
import com.zangrcar.cngitaly.data.routing.RouteCorridorSetting
import com.zangrcar.cngitaly.data.routing.QuickRouteActions
import com.zangrcar.cngitaly.data.geocoding.normalizePlaceQuery

data class MainUiState(
    val metadata: DatasetMetaEntity? = null,
    val isOnline: Boolean = false,
    val isRefreshing: Boolean = false
)

data class PlaceTypeaheadState(
    val query: String = "",
    val results: List<PlaceSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = CngDatabase.getInstance(application)
    private val repository = StationRepository(database.stationDao())
    private val liveClient = MimitLiveClient()
    private val photonClient = PhotonClient()
    private val osrmClient = OsrmClient()
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val online = MutableStateFlow(hasValidatedInternet())
    val isValidatedInternetAvailable: Boolean get() = online.value
    private val refreshing = MutableStateFlow(false)
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
    private var normalSearchJob: Job? = null
    private val _normalSearch = MutableStateFlow(PlaceTypeaheadState())
    val normalSearch = _normalSearch.asStateFlow()
    private var quickSearchJob: Job? = null
    private val _quickSearch = MutableStateFlow(PlaceTypeaheadState())
    val quickSearch = _quickSearch.asStateFlow()
    private var routeJob: Job? = null
    private var routeRequestId = 0L
    private var stationProjectionJob: Job? = null
    private val stationProjectionGuard = StationProjectionGuard()
    private val _routeCorridorSetting = MutableStateFlow<RouteCorridorSetting>(RouteCorridorSetting.Auto)
    val routeCorridorSetting = _routeCorridorSetting.asStateFlow()
    private val _searchedPlaceMarker = MutableStateFlow<PlaceSearchResult?>(null)
    val searchedPlaceMarker = _searchedPlaceMarker.asStateFlow()
    private val _activeRoute = MutableStateFlow<RouteResult?>(null)
    val activeRoute = _activeRoute.asStateFlow()
    private val _isRouteLoading = MutableStateFlow(false)
    val isRouteLoading = _isRouteLoading.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    val uiState = combine(
        repository.metadata,
        online,
        refreshing
    ) { meta, isOnline, isRefreshing ->
        MainUiState(meta, isOnline, isRefreshing)
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
            else if (meta != null) loadAllStations()
        }
    }

    fun refresh(showSuccessMessage: Boolean = true) {
        if (refreshing.value) return
        if (!online.value) {
            if (showSuccessMessage) {
                _messages.tryEmit("Refresh failed. Keeping existing station data.")
            }
            return
        }
        viewModelScope.launch {
            refreshing.value = true
            try {
                repository.refresh()
                recomputeStations()
                if (showSuccessMessage) _messages.emit("Station data refreshed.")
            } catch (_: Exception) {
                _messages.emit("Refresh failed. Keeping existing station data.")
            } finally {
                refreshing.value = false
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
            } catch (exception: CancellationException) {
                throw exception
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

    fun updateNormalSearch(query: String) = updateTypeahead(query, false, debounce = true, clearResults = true)
    fun submitNormalSearch() = updateTypeahead(_normalSearch.value.query, false, debounce = false, clearResults = false)
    fun clearPlaceSearch() {
        normalSearchJob?.cancel(); normalSearchJob = null
        _normalSearch.value = PlaceTypeaheadState()
    }

    fun updateQuickSearch(query: String) = updateTypeahead(query, true, debounce = true, clearResults = true)
    fun submitQuickSearch() = updateTypeahead(_quickSearch.value.query, true, debounce = false, clearResults = false)
    fun clearQuickSearch() {
        quickSearchJob?.cancel(); quickSearchJob = null
        _quickSearch.value = PlaceTypeaheadState()
    }
    fun setQuickSearchError(message: String?) {
        _quickSearch.value = _quickSearch.value.copy(error = message, isLoading = false)
    }

    fun navigateFromPlace(result: PlaceSearchResult): Boolean = navigateFrom(
        RouteEndpoint(result.displayName, result.latitude, result.longitude)
    )

    fun navigateFrom(endpoint: RouteEndpoint): Boolean {
        val destination = _searchedPlaceMarker.value ?: return false
        return requestRoute(QuickRouteActions.navigate(
            endpoint,
            RouteEndpoint(destination.displayName, destination.latitude, destination.longitude)
        ))
    }

    fun addStopAndRecalculate(result: PlaceSearchResult): Boolean {
        val route = _activeRoute.value ?: return false
        return requestRoute(QuickRouteActions.addStop(
            route.endpoints,
            RouteEndpoint(result.displayName, result.latitude, result.longitude)
        ))
    }

    fun applyRouteEndpoints(endpoints: List<RouteEndpoint>): Boolean = requestRoute(endpoints)

    private fun requestRoute(endpoints: List<RouteEndpoint>): Boolean {
        if (endpoints.size < 2 || endpoints.size > 10) return false
        routeJob?.cancel()
        val requestId = ++routeRequestId
        if (!online.value) {
            routeJob = null
            _isRouteLoading.value = false
            _messages.tryEmit("Route unavailable. Try again.")
            return true
        }
        stationProjectionJob?.cancel()
        stationProjectionGuard.next()
        routeJob = viewModelScope.launch {
            _isRouteLoading.value = true
            try {
                val route = osrmClient.route(endpoints)
                val corridor = _routeCorridorSetting.value
                val routeStations = repository.getStationsNearRoute(route.points, route.distanceMeters, corridor)
                if (!routeRequestIsCurrent(
                        requestId = requestId,
                        currentRequestId = routeRequestId,
                        filteredCorridor = corridor,
                        currentCorridor = _routeCorridorSetting.value
                    )
                ) return@launch
                _activeRoute.value = route
                _stations.value = routeStations
                val marker = _searchedPlaceMarker.value
                if (marker != null && route.endpoints.any {
                        sameCoordinate(marker.latitude, marker.longitude, it.latitude, it.longitude)
                    }
                ) _searchedPlaceMarker.value = null
            } catch (exception: CancellationException) { throw exception }
            catch (_: NoDrivingRouteException) { _messages.emit("No driving route found.") }
            catch (_: Exception) { _messages.emit("Route unavailable. Try again.") }
            finally { if (requestId == routeRequestId) _isRouteLoading.value = false }
        }
        return true
    }

    fun setRouteCorridor(setting: RouteCorridorSetting) {
        _routeCorridorSetting.value = setting
        routeJob?.cancel()
        routeRequestId++
        routeJob = null
        _isRouteLoading.value = false
        recomputeStations()
    }

    fun setSearchedPlaceMarker(place: PlaceSearchResult?) { _searchedPlaceMarker.value = place }

    fun clearRoute() {
        routeJob?.cancel()
        routeRequestId++
        routeJob = null
        _isRouteLoading.value = false
        _activeRoute.value = null
        recomputeStations()
    }

    override fun onCleared() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        super.onCleared()
    }

    private fun updateConnectivity() {
        online.value = hasValidatedInternet()
    }

    private suspend fun loadAllStations() {
        recomputeStations()
    }

    private fun recomputeStations() {
        stationProjectionJob?.cancel()
        val generation = stationProjectionGuard.next()
        val route = _activeRoute.value
        val corridor = _routeCorridorSetting.value
        stationProjectionJob = viewModelScope.launch {
            try {
                val result = if (route == null) repository.getAllStations()
                else repository.getStationsNearRoute(route.points, route.distanceMeters, corridor)
                val modeStillMatches = if (route == null) _activeRoute.value == null
                else _activeRoute.value === route && _routeCorridorSetting.value == corridor
                if (stationProjectionGuard.isCurrent(generation) && modeStillMatches) {
                    _stations.value = result
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _messages.emit("Unable to load local station data.")
            }
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun updateTypeahead(query: String, quick: Boolean, debounce: Boolean, clearResults: Boolean) {
        val state = if (quick) _quickSearch else _normalSearch
        val oldJob = if (quick) quickSearchJob else normalSearchJob
        oldJob?.cancel()
        state.value = if (clearResults) PlaceTypeaheadState(query = query)
        else state.value.copy(query = query, isLoading = false, error = null)
        if (!shouldSearchPlaceQuery(query)) return
        if (!online.value) {
            state.value = state.value.copy(error = "Place search requires internet.")
            return
        }
        val expectedQuery = normalizePlaceQuery(query)
        val job = viewModelScope.launch {
            if (debounce) delay(TYPEAHEAD_DEBOUNCE_MILLIS)
            state.value = state.value.copy(isLoading = true)
            try {
                val countries = if (quick) emptyList() else listOf("IT")
                val results = photonClient.search(query, countries)
                if (typeaheadQueryIsCurrent(state.value.query, expectedQuery)) {
                    state.value = state.value.copy(results = results, isLoading = false)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (typeaheadQueryIsCurrent(state.value.query, expectedQuery)) {
                    state.value = state.value.copy(
                        isLoading = false,
                        error = "Place search unavailable. Try again."
                    )
                }
            }
        }
        if (quick) quickSearchJob = job else normalSearchJob = job
    }

    companion object {
        private const val TYPEAHEAD_DEBOUNCE_MILLIS = 375L
        private fun sameCoordinate(aLat: Double, aLon: Double, bLat: Double, bLon: Double) =
            kotlin.math.abs(aLat - bLat) < 0.000001 && kotlin.math.abs(aLon - bLon) < 0.000001
    }
}

internal fun shouldSearchPlaceQuery(query: String): Boolean =
    normalizePlaceQuery(query).length >= 2

internal fun typeaheadQueryIsCurrent(currentQuery: String, requestedNormalizedQuery: String): Boolean =
    normalizePlaceQuery(currentQuery) == requestedNormalizedQuery
