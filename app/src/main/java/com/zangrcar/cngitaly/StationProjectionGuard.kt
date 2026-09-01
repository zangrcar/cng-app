package com.zangrcar.cngitaly

internal class StationProjectionGuard {
    private var generation = 0L

    fun next(): Long = ++generation

    fun isCurrent(candidate: Long): Boolean = candidate == generation
}

internal enum class ResolvedLocationAction { ROUTE, CENTER, NONE }

internal fun resolvedLocationAction(
    routeLocationRequest: Boolean,
    centerWhenLocationArrives: Boolean
): ResolvedLocationAction = when {
    routeLocationRequest -> ResolvedLocationAction.ROUTE
    centerWhenLocationArrives -> ResolvedLocationAction.CENTER
    else -> ResolvedLocationAction.NONE
}
