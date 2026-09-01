package com.zangrcar.cngitaly.data.routing

data class RouteEndpoint(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean = false
)

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
