package com.zangrcar.cngitaly

import com.zangrcar.cngitaly.data.routing.RouteCorridorSetting

internal class StationProjectionGuard {
    private var generation = 0L

    fun next(): Long = ++generation

    fun isCurrent(candidate: Long): Boolean = candidate == generation
}

internal fun routeRequestIsCurrent(
    requestId: Long,
    currentRequestId: Long,
    filteredCorridor: RouteCorridorSetting,
    currentCorridor: RouteCorridorSetting
): Boolean = requestId == currentRequestId && filteredCorridor == currentCorridor

internal enum class ResolvedLocationAction { ROUTE, CENTER, NONE }

internal fun resolvedLocationAction(
    routeLocationRequest: Boolean,
    centerWhenLocationArrives: Boolean
): ResolvedLocationAction = when {
    routeLocationRequest -> ResolvedLocationAction.ROUTE
    centerWhenLocationArrives -> ResolvedLocationAction.CENTER
    else -> ResolvedLocationAction.NONE
}
