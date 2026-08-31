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
    fun navigateTo(drafts: List<RoutePointDraft>, place: com.zangrcar.cngitaly.data.geocoding.PlaceSearchResult): List<RoutePointDraft> {
        val destination = drafts.last { it.role == RoutePointRole.TO }
        return RouteDrafts.select(drafts, destination.id, RouteEndpoint(place.displayName, place.latitude, place.longitude))
    }

    fun addAsStop(
        drafts: List<RoutePointDraft>,
        place: com.zangrcar.cngitaly.data.geocoding.PlaceSearchResult,
        stopId: RoutePointId
    ): List<RoutePointDraft> {
        if (drafts.count { it.role == RoutePointRole.STOP } >= 8) return drafts
        val destinationIndex = drafts.indexOfLast { it.role == RoutePointRole.TO }
        val stop = RoutePointDraft(
            id = stopId,
            role = RoutePointRole.STOP,
            query = place.displayName,
            selectedEndpoint = RouteEndpoint(place.displayName, place.latitude, place.longitude)
        )
        return drafts.toMutableList().apply { add(destinationIndex, stop) }
    }

    fun remove(): com.zangrcar.cngitaly.data.geocoding.PlaceSearchResult? = null
}

object QuickRouteActions {
    fun navigate(from: RouteEndpoint, to: RouteEndpoint): List<RouteEndpoint> = listOf(from, to)

    fun addStop(endpoints: List<RouteEndpoint>, stop: RouteEndpoint): List<RouteEndpoint> {
        if (endpoints.size < 2 || endpoints.size >= 10) return endpoints
        return endpoints.toMutableList().apply { add(lastIndex, stop) }
    }

    fun moveStop(endpoints: List<RouteEndpoint>, stopIndex: Int, direction: Int): List<RouteEndpoint> {
        val target = stopIndex + direction
        if (stopIndex !in 1 until endpoints.lastIndex || target !in 1 until endpoints.lastIndex) return endpoints
        return endpoints.toMutableList().apply { add(target, removeAt(stopIndex)) }
    }

    fun removeStop(endpoints: List<RouteEndpoint>, stopIndex: Int): List<RouteEndpoint> =
        if (stopIndex in 1 until endpoints.lastIndex) endpoints.filterIndexed { index, _ -> index != stopIndex }
        else endpoints
}

enum class RouteDrawerAction { DONE, APPLY_ROUTE }

fun routeDrawerAction(
    original: List<RouteEndpoint>,
    edited: List<RouteEndpoint>
): RouteDrawerAction = if (original == edited) RouteDrawerAction.DONE else RouteDrawerAction.APPLY_ROUTE

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
