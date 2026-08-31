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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.zangrcar.cngitaly.data.routing.NoDrivingRouteException
import com.zangrcar.cngitaly.data.routing.OsrmClient
import com.zangrcar.cngitaly.data.routing.RouteEndpoint
import com.zangrcar.cngitaly.data.routing.RouteResult
import com.zangrcar.cngitaly.data.routing.RoutePointDraft
import com.zangrcar.cngitaly.data.routing.RoutePointId
import com.zangrcar.cngitaly.data.routing.RoutePointRole
import com.zangrcar.cngitaly.data.routing.RouteCorridorSetting
import com.zangrcar.cngitaly.data.routing.RouteDrafts
import com.zangrcar.cngitaly.data.routing.PlaceMarkerActions

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
    private val photonClient = PhotonClient()
    private val osrmClient = OsrmClient()
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
    private var placeSearchJob: Job? = null
    private val _placeResults = MutableStateFlow<List<PlaceSearchResult>>(emptyList())
    val placeResults = _placeResults.asStateFlow()
    private val _isPlaceSearchLoading = MutableStateFlow(false)
    val isPlaceSearchLoading = _isPlaceSearchLoading.asStateFlow()
    private val _placeSearchError = MutableStateFlow<String?>(null)
    val placeSearchError = _placeSearchError.asStateFlow()
    private val _hasSubmittedPlaceSearch = MutableStateFlow(false)
    val hasSubmittedPlaceSearch = _hasSubmittedPlaceSearch.asStateFlow()
    private var lastSearchedBounds: MapBounds? = null
    private var routeJob: Job? = null
    private val routeSearchJobs = mutableMapOf<RoutePointId, Job>()
    private var nextRoutePointId = 3L
    private val _routeDrafts = MutableStateFlow(initialRouteDrafts())
    val routeDrafts = _routeDrafts.asStateFlow()
    private val _routeCorridorSetting = MutableStateFlow<RouteCorridorSetting>(RouteCorridorSetting.Auto)
    val routeCorridorSetting = _routeCorridorSetting.asStateFlow()
    private val _searchedPlaceMarker = MutableStateFlow<PlaceSearchResult?>(null)
    val searchedPlaceMarker = _searchedPlaceMarker.asStateFlow()
    private val _activeRoute = MutableStateFlow<RouteResult?>(null)
    val activeRoute = _activeRoute.asStateFlow()
    private val _isRouteLoading = MutableStateFlow(false)
    val isRouteLoading = _isRouteLoading.asStateFlow()
    private val _routeError = MutableStateFlow<String?>(null)
    val routeError = _routeError.asStateFlow()

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
                val route = _activeRoute.value
                val searchedBounds = lastSearchedBounds
                if (route != null) {
                    _stations.value = repository.getStationsNearRoute(route.points, route.distanceMeters, _routeCorridorSetting.value)
                } else if (searchedBounds != null) {
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
        if (_activeRoute.value != null) return
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

    fun searchPlaces(query: String) {
        val submitted = query.trim()
        if (submitted.isEmpty()) return
        placeSearchJob?.cancel()
        _placeResults.value = emptyList()
        _placeSearchError.value = null
        _hasSubmittedPlaceSearch.value = true
        if (!online.value) {
            _isPlaceSearchLoading.value = false
            _placeSearchError.value = "Place search requires internet."
            return
        }
        placeSearchJob = viewModelScope.launch {
            _isPlaceSearchLoading.value = true
            try {
                _placeResults.value = photonClient.search(submitted)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _placeSearchError.value = "Place search unavailable. Try again."
            } finally {
                _isPlaceSearchLoading.value = false
            }
        }
    }

    fun clearPlaceSearch() {
        placeSearchJob?.cancel()
        placeSearchJob = null
        _placeResults.value = emptyList()
        _placeSearchError.value = null
        _isPlaceSearchLoading.value = false
        _hasSubmittedPlaceSearch.value = false
    }

    fun editRoutePoint(id: RoutePointId, query: String) = updateDraft(id) {
        it.copy(query = query, selectedEndpoint = null, results = emptyList(), error = null)
    }

    fun searchRouteEndpoint(id: RoutePointId) {
        val query = _routeDrafts.value.firstOrNull { it.id == id }?.query.orEmpty()
        val submitted = query.trim()
        if (submitted.isEmpty()) return
        routeSearchJobs.remove(id)?.cancel()
        updateDraft(id) { it.copy(results = emptyList(), error = null) }
        if (!online.value) {
            updateDraft(id) { it.copy(error = "Place search requires internet.") }
            return
        }
        routeSearchJobs[id] = viewModelScope.launch {
            updateDraft(id) { it.copy(isSearching = true) }
            try {
                val results = photonClient.search(submitted, emptyList())
                updateDraft(id) { it.copy(results = results) }
            }
            catch (exception: CancellationException) { throw exception }
            catch (_: Exception) { updateDraft(id) { it.copy(error = "Place search unavailable. Try again.") } }
            finally { updateDraft(id) { it.copy(isSearching = false) } }
        }
    }

    fun selectRouteEndpoint(id: RoutePointId, result: PlaceSearchResult) {
        val endpoint = RouteEndpoint(result.displayName, result.latitude, result.longitude)
        routeSearchJobs.remove(id)?.cancel()
        _routeDrafts.value = RouteDrafts.select(_routeDrafts.value, id, endpoint)
    }

    fun useCurrentLocation(endpoint: RouteEndpoint) {
        val from = _routeDrafts.value.first { it.role == RoutePointRole.FROM }
        updateDraft(from.id) { it.copy(query = "My location", selectedEndpoint = endpoint.copy(label = "My location", isCurrentLocation = true), results = emptyList(), error = null) }
    }

    fun setRoutePointError(id: RoutePointId, message: String?) = updateDraft(id) { it.copy(error = message) }
    fun setCurrentLocationError(message: String?) {
        _routeDrafts.value.firstOrNull { it.role == RoutePointRole.FROM }?.let { setRoutePointError(it.id, message) }
    }

    fun addRouteStop() {
        if (_routeDrafts.value.count { it.role == RoutePointRole.STOP } >= 8) return
        val drafts = _routeDrafts.value.toMutableList()
        drafts.add(drafts.lastIndex, RoutePointDraft(RoutePointId(nextRoutePointId++), RoutePointRole.STOP))
        _routeDrafts.value = drafts
    }

    fun removeRouteStop(id: RoutePointId) {
        routeSearchJobs.remove(id)?.cancel()
        _routeDrafts.value = RouteDrafts.removeStop(_routeDrafts.value, id)
    }

    fun moveRouteStop(id: RoutePointId, direction: Int) {
        _routeDrafts.value = RouteDrafts.moveStop(_routeDrafts.value, id, direction)
    }

    fun dismissRouteSheet() {
        routeSearchJobs.values.forEach(Job::cancel); routeSearchJobs.clear()
        _routeDrafts.value = _routeDrafts.value.map { it.copy(results = emptyList(), isSearching = false, error = null) }
    }

    fun findRoute(): Boolean {
        val endpoints = RouteDrafts.endpoints(_routeDrafts.value)
        if (endpoints.size != _routeDrafts.value.size) return false
        routeJob?.cancel()
        _routeError.value = null
        if (!online.value) {
            _messages.tryEmit("Route unavailable. Try again.")
            return true
        }
        routeJob = viewModelScope.launch {
            _isRouteLoading.value = true
            try {
                val route = osrmClient.route(endpoints)
                val routeStations = repository.getStationsNearRoute(route.points, route.distanceMeters, _routeCorridorSetting.value)
                _activeRoute.value = route
                _stations.value = routeStations
                val marker = _searchedPlaceMarker.value
                if (marker != null && sameCoordinate(marker.latitude, marker.longitude, route.to.latitude, route.to.longitude)) _searchedPlaceMarker.value = null
            } catch (exception: CancellationException) { throw exception }
            catch (_: NoDrivingRouteException) { _messages.emit("No driving route found.") }
            catch (_: Exception) { _messages.emit("Route unavailable. Try again.") }
            finally { _isRouteLoading.value = false }
        }
        return true
    }

    fun setRouteCorridor(setting: RouteCorridorSetting) {
        _routeCorridorSetting.value = setting
        _activeRoute.value?.let { route -> viewModelScope.launch {
            _stations.value = repository.getStationsNearRoute(route.points, route.distanceMeters, setting)
        } }
    }

    fun setSearchedPlaceMarker(place: PlaceSearchResult?) { _searchedPlaceMarker.value = place }
    fun routeToSearchedPlace() {
        val place = _searchedPlaceMarker.value ?: return
        _routeDrafts.value = PlaceMarkerActions.routeHere(_routeDrafts.value, place)
    }

    fun clearRoute() {
        routeJob?.cancel()
        routeJob = null
        _activeRoute.value = null
        routeSearchJobs.values.forEach(Job::cancel); routeSearchJobs.clear()
        _routeDrafts.value = initialRouteDrafts()
        _routeError.value = null
        viewportSearchRequestChannel.trySend(Unit)
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

    private fun updateDraft(id: RoutePointId, transform: (RoutePointDraft) -> RoutePointDraft) {
        _routeDrafts.value = _routeDrafts.value.map { if (it.id == id) transform(it) else it }
    }

    companion object {
        private fun initialRouteDrafts() = listOf(
            RoutePointDraft(RoutePointId(1), RoutePointRole.FROM),
            RoutePointDraft(RoutePointId(2), RoutePointRole.TO)
        )
        private fun sameCoordinate(aLat: Double, aLon: Double, bLat: Double, bLon: Double) =
            kotlin.math.abs(aLat - bLat) < 0.000001 && kotlin.math.abs(aLon - bLon) < 0.000001
    }
}
