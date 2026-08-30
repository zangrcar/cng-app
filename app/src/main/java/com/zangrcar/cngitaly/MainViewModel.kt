package com.zangrcar.cngitaly

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zangrcar.cngitaly.data.StationRepository
import com.zangrcar.cngitaly.data.local.CngDatabase
import com.zangrcar.cngitaly.data.local.DatasetMetaEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val metadata: DatasetMetaEntity? = null,
    val isOnline: Boolean = false,
    val isRefreshing: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = CngDatabase.getInstance(application)
    private val repository = StationRepository(database.stationDao())
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val online = MutableStateFlow(hasValidatedInternet())
    private val refreshing = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    val uiState = combine(repository.metadata, online, refreshing) { meta, isOnline, isRefreshing ->
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
        }
    }

    fun refresh(showSuccessMessage: Boolean = true) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                repository.refresh()
                if (showSuccessMessage) _messages.emit("Station data refreshed.")
            } catch (_: Exception) {
                _messages.emit("Refresh failed. Keeping existing station data.")
            } finally {
                refreshing.value = false
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

    private fun hasValidatedInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
