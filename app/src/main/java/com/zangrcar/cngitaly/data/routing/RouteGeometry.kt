package com.zangrcar.cngitaly.data.routing

import com.zangrcar.cngitaly.data.MapBounds
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

private const val EARTH_RADIUS_METERS = 6_371_000.0
internal const val ROUTE_FILTER_SIMPLIFICATION_TOLERANCE_METERS = 75.0

fun routeCorridorMeters(routeDistanceMeters: Double): Double =
    (routeDistanceMeters * 0.02).coerceIn(3_000.0, 10_000.0)

fun routeCorridorMeters(routeDistanceMeters: Double, setting: RouteCorridorSetting): Double =
    when (setting) {
        RouteCorridorSetting.Auto -> routeCorridorMeters(routeDistanceMeters)
        is RouteCorridorSetting.Fixed -> setting.meters
    }

fun expandedRouteBounds(points: List<GeoPoint>, corridorMeters: Double): MapBounds {
    require(points.isNotEmpty())
    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }
    val latitudePadding = Math.toDegrees(corridorMeters / EARTH_RADIUS_METERS)
    val leastCosine = max(0.01, minOf(cos(Math.toRadians(north)), cos(Math.toRadians(south))))
    val longitudePadding = latitudePadding / leastCosine
    return MapBounds(
        north = (north + latitudePadding).coerceAtMost(90.0),
        south = (south - latitudePadding).coerceAtLeast(-90.0),
        east = (east + longitudePadding).coerceAtMost(180.0),
        west = (west - longitudePadding).coerceAtLeast(-180.0)
    )
}

fun distancePointToRouteMeters(point: GeoPoint, routePoints: List<GeoPoint>): Double {
    require(routePoints.size >= 2)

    var minimumDistance = Double.POSITIVE_INFINITY
    for (index in 0 until routePoints.lastIndex) {
        val distance = distancePointToSegmentMeters(
            point,
            routePoints[index],
            routePoints[index + 1]
        )
        if (distance == 0.0) return 0.0
        if (distance < minimumDistance) minimumDistance = distance
    }
    return minimumDistance
}

internal fun simplifyRouteForFiltering(
    points: List<GeoPoint>,
    toleranceMeters: Double = ROUTE_FILTER_SIMPLIFICATION_TOLERANCE_METERS
): List<GeoPoint> {
    require(toleranceMeters >= 0.0)
    if (points.size <= 2 || toleranceMeters == 0.0) return points

    val retained = BooleanArray(points.size)
    retained[0] = true
    retained[points.lastIndex] = true
    val ranges = ArrayDeque<Pair<Int, Int>>()
    ranges.addLast(0 to points.lastIndex)

    while (ranges.isNotEmpty()) {
        val (startIndex, endIndex) = ranges.removeLast()
        var farthestIndex = -1
        var maximumDistance = 0.0

        for (index in startIndex + 1 until endIndex) {
            val distance = distancePointToSegmentMeters(
                points[index],
                points[startIndex],
                points[endIndex]
            )
            if (distance > maximumDistance) {
                maximumDistance = distance
                farthestIndex = index
            }
        }

        if (farthestIndex >= 0 && maximumDistance > toleranceMeters) {
            retained[farthestIndex] = true
            ranges.addLast(startIndex to farthestIndex)
            ranges.addLast(farthestIndex to endIndex)
        }
    }

    return points.filterIndexed { index, _ -> retained[index] }
}

internal fun pointIsWithinRouteCorridor(
    point: GeoPoint,
    fullRoutePoints: List<GeoPoint>,
    simplifiedRoutePoints: List<GeoPoint>,
    corridorMeters: Double,
    simplificationToleranceMeters: Double = ROUTE_FILTER_SIMPLIFICATION_TOLERANCE_METERS
): Boolean {
    require(fullRoutePoints.size >= 2)
    require(simplifiedRoutePoints.size >= 2)
    require(corridorMeters >= 0.0)
    require(simplificationToleranceMeters >= 0.0)

    val approximateDistance = distancePointToRouteMeters(point, simplifiedRoutePoints)
    val definitelyInsideThreshold =
        (corridorMeters - simplificationToleranceMeters).coerceAtLeast(0.0)
    val definitelyOutsideThreshold = corridorMeters + simplificationToleranceMeters

    if (approximateDistance <= definitelyInsideThreshold) return true
    if (approximateDistance > definitelyOutsideThreshold) return false

    return distancePointToRouteMeters(point, fullRoutePoints) <= corridorMeters
}

internal fun distancePointToSegmentMeters(
    point: GeoPoint,
    start: GeoPoint,
    end: GeoPoint
): Double {
    val referenceLatitude = Math.toRadians((point.latitude + start.latitude + end.latitude) / 3.0)
    fun xy(value: GeoPoint): Pair<Double, Double> =
        Math.toRadians(value.longitude) * EARTH_RADIUS_METERS * cos(referenceLatitude) to
            Math.toRadians(value.latitude) * EARTH_RADIUS_METERS
    val (px, py) = xy(point)
    val (ax, ay) = xy(start)
    val (bx, by) = xy(end)
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0.0) 0.0 else
        (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}
