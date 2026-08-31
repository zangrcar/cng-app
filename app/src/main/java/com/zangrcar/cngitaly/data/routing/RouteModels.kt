package com.zangrcar.cngitaly.data.routing

data class RouteEndpoint(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean = false
)

enum class RoutePointRole { FROM, STOP, TO }

@JvmInline value class RoutePointId(val value: Long)

data class RoutePointDraft(
    val id: RoutePointId,
    val role: RoutePointRole,
    val query: String = "",
    val selectedEndpoint: RouteEndpoint? = null,
    val results: List<com.zangrcar.cngitaly.data.geocoding.PlaceSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)

object RouteDrafts {
    fun select(drafts: List<RoutePointDraft>, id: RoutePointId, endpoint: RouteEndpoint) = drafts.map {
        if (it.id == id) it.copy(query = endpoint.label, selectedEndpoint = endpoint, results = emptyList(), error = null) else it
    }
    fun removeStop(drafts: List<RoutePointDraft>, id: RoutePointId) =
        drafts.filterNot { it.id == id && it.role == RoutePointRole.STOP }
    fun moveStop(drafts: List<RoutePointDraft>, id: RoutePointId, direction: Int): List<RoutePointDraft> {
        val result = drafts.toMutableList()
        val index = result.indexOfFirst { it.id == id && it.role == RoutePointRole.STOP }
        val target = index + direction
        if (index > 0 && target in 1 until result.lastIndex) result.add(target, result.removeAt(index))
        return result
    }
    fun endpoints(drafts: List<RoutePointDraft>): List<RouteEndpoint> = drafts.mapNotNull { it.selectedEndpoint }
}

object PlaceMarkerActions {
    fun routeHere(drafts: List<RoutePointDraft>, place: com.zangrcar.cngitaly.data.geocoding.PlaceSearchResult): List<RoutePointDraft> {
        val destination = drafts.last { it.role == RoutePointRole.TO }
        return RouteDrafts.select(drafts, destination.id, RouteEndpoint(place.displayName, place.latitude, place.longitude))
    }
    fun remove(): com.zangrcar.cngitaly.data.geocoding.PlaceSearchResult? = null
}

sealed interface RouteCorridorSetting {
    data object Auto : RouteCorridorSetting
    data class Fixed(val meters: Double) : RouteCorridorSetting
}

data class GeoPoint(val latitude: Double, val longitude: Double)

data class RouteResult(
    val endpoints: List<RouteEndpoint>,
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double
) {
    val from get() = endpoints.first()
    val to get() = endpoints.last()
}
