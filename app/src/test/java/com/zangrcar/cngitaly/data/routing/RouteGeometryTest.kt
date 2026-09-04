package com.zangrcar.cngitaly.data.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryTest {
    private val route = listOf(GeoPoint(45.0, 10.0), GeoPoint(45.0, 11.0))
    @Test fun `corridor clamps and scales`() {
        assertEquals(3000.0, routeCorridorMeters(50_000.0), 0.0)
        assertEquals(4000.0, routeCorridorMeters(200_000.0), 0.0)
        assertEquals(10000.0, routeCorridorMeters(800_000.0), 0.0)
    }
    @Test fun `fixed corridor choices are exact`() {
        listOf(3000.0, 5000.0, 10000.0, 20000.0).forEach { meters ->
            assertEquals(meters, routeCorridorMeters(999_999.0, RouteCorridorSetting.Fixed(meters)), 0.0)
        }
    }
    @Test fun `auto corridor retains existing calculation`() {
        assertEquals(routeCorridorMeters(200_000.0), routeCorridorMeters(200_000.0, RouteCorridorSetting.Auto), 0.0)
    }
    @Test fun `point on segment is zero`() { assertEquals(0.0, distancePointToRouteMeters(GeoPoint(45.0, 10.5), route), 0.01) }
    @Test fun `within and outside corridor`() {
        assertTrue(distancePointToRouteMeters(GeoPoint(45.02, 10.5), route) < 3000)
        assertTrue(distancePointToRouteMeters(GeoPoint(45.1, 10.5), route) > 3000)
    }
    @Test fun `distance uses segment rather than endpoints`() {
        assertTrue(distancePointToRouteMeters(GeoPoint(45.01, 10.5), route) < 1500)
    }
    @Test fun `giant bbox station far from diagonal excluded while close included`() {
        val diagonal = listOf(GeoPoint(40.0, 10.0), GeoPoint(46.0, 16.0))
        assertTrue(distancePointToRouteMeters(GeoPoint(46.0, 10.0), diagonal) > 10_000)
        assertTrue(distancePointToRouteMeters(GeoPoint(43.01, 13.0), diagonal) < 3_000)
    }

    @Test fun `straight dense route collapses to endpoints`() {
        val denseRoute = (0..120).map { index ->
            GeoPoint(45.0, 10.0 + index * 0.001)
        }

        val simplified = simplifyRouteForFiltering(denseRoute, toleranceMeters = 75.0)

        assertEquals(2, simplified.size)
        assertEquals(denseRoute.first(), simplified.first())
        assertEquals(denseRoute.last(), simplified.last())
    }

    @Test fun `significant bend is preserved`() {
        val bentRoute = listOf(
            GeoPoint(45.0, 10.0),
            GeoPoint(45.01, 10.01),
            GeoPoint(45.0, 10.02)
        )

        assertEquals(
            bentRoute,
            simplifyRouteForFiltering(bentRoute, toleranceMeters = 75.0)
        )
    }

    @Test fun `tiny deviation is removed`() {
        val nearlyStraightRoute = listOf(
            GeoPoint(45.0, 10.0),
            GeoPoint(45.00002, 10.01),
            GeoPoint(45.0, 10.02)
        )

        assertEquals(
            listOf(nearlyStraightRoute.first(), nearlyStraightRoute.last()),
            simplifyRouteForFiltering(nearlyStraightRoute, toleranceMeters = 75.0)
        )
    }

    @Test fun `short routes stay unchanged`() {
        val onePoint = listOf(GeoPoint(45.0, 10.0))
        val twoPoints = listOf(GeoPoint(45.0, 10.0), GeoPoint(45.1, 10.1))

        assertEquals(onePoint, simplifyRouteForFiltering(onePoint))
        assertEquals(twoPoints, simplifyRouteForFiltering(twoPoints))
    }

    @Test fun `corridor helper agrees with exact route classification`() {
        val fullRoute = listOf(
            GeoPoint(45.0, 10.0),
            GeoPoint(45.00002, 10.01),
            GeoPoint(45.0, 10.02),
            GeoPoint(45.01, 10.03),
            GeoPoint(45.0, 10.04)
        )
        val simplifiedRoute = simplifyRouteForFiltering(fullRoute)
        val corridor = 3_000.0
        val stations = listOf(
            GeoPoint(45.005, 10.015),
            GeoPoint(45.05, 10.02),
            GeoPoint(45.027, 10.015),
            GeoPoint(44.973, 10.025)
        )

        stations.forEach { station ->
            val exact = distancePointToRouteMeters(station, fullRoute) <= corridor
            assertEquals(
                exact,
                pointIsWithinRouteCorridor(
                    point = station,
                    fullRoutePoints = fullRoute,
                    simplifiedRoutePoints = simplifiedRoute,
                    corridorMeters = corridor
                )
            )
        }
    }
}
