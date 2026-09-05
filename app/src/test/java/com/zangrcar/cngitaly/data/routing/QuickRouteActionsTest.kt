package com.zangrcar.cngitaly.data.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickRouteActionsTest {
    private fun endpoint(name: String, coordinate: Double = 1.0) =
        RouteEndpoint(name, coordinate, coordinate)

    @Test fun `navigate creates ordered From To endpoints`() {
        val from = endpoint("From", 1.0)
        val to = endpoint("To", 2.0)
        assertEquals(listOf(from, to), QuickRouteActions.navigate(from, to))
    }

    @Test fun `add stop inserts before destination and preserves order`() {
        val endpoints = listOf(endpoint("From"), endpoint("Florence"), endpoint("To"))
        assertEquals(
            listOf("From", "Florence", "Bologna", "To"),
            QuickRouteActions.addStop(endpoints, endpoint("Bologna")).map { it.label }
        )
    }

    @Test fun `multiple stops retain insertion order`() {
        val first = QuickRouteActions.addStop(listOf(endpoint("From"), endpoint("To")), endpoint("Florence"))
        val second = QuickRouteActions.addStop(first, endpoint("Bologna"))
        assertEquals(listOf("From", "Florence", "Bologna", "To"), second.map { it.label })
    }

    @Test fun `remove affects only intermediate stops`() {
        val endpoints = listOf(endpoint("From"), endpoint("Florence"), endpoint("Bologna"), endpoint("To"))
        assertEquals(
            listOf("From", "Florence", "To"),
            QuickRouteActions.removeStop(endpoints, 2).map { it.label }
        )
        assertEquals(endpoints, QuickRouteActions.removeStop(endpoints, 0))
        assertEquals(endpoints, QuickRouteActions.removeStop(endpoints, endpoints.lastIndex))
    }

    @Test fun `intermediate stop can become origin`() {
        val endpoints = listOf(endpoint("From"), endpoint("Florence"), endpoint("To"))
        assertEquals(
            listOf("Florence", "From", "To"),
            QuickRouteActions.moveEndpoint(endpoints, 1, 0).map { it.label }
        )
    }

    @Test fun `intermediate stop can become destination`() {
        val endpoints = listOf(endpoint("From"), endpoint("Florence"), endpoint("To"))
        assertEquals(
            listOf("From", "To", "Florence"),
            QuickRouteActions.moveEndpoint(endpoints, 1, 2).map { it.label }
        )
    }

    @Test fun `origin can become destination`() {
        val endpoints = listOf(endpoint("From"), endpoint("Florence"), endpoint("To"))
        assertEquals(
            listOf("Florence", "To", "From"),
            QuickRouteActions.moveEndpoint(endpoints, 0, 2).map { it.label }
        )
    }

    @Test fun `destination can become origin`() {
        val endpoints = listOf(endpoint("From"), endpoint("Florence"), endpoint("To"))
        assertEquals(
            listOf("To", "From", "Florence"),
            QuickRouteActions.moveEndpoint(endpoints, 2, 0).map { it.label }
        )
    }

    @Test fun `two endpoint route can reverse`() {
        val endpoints = listOf(endpoint("From"), endpoint("To"))
        assertEquals(
            listOf("To", "From"),
            QuickRouteActions.moveEndpoint(endpoints, 0, 1).map { it.label }
        )
    }

    @Test fun `current location flag travels with moved endpoint`() {
        val currentLocation = RouteEndpoint("My location", 1.0, 1.0, isCurrentLocation = true)
        val endpoints = listOf(currentLocation, endpoint("Florence"), endpoint("To"))
        val moved = QuickRouteActions.moveEndpoint(endpoints, 0, 2)

        assertEquals(currentLocation, moved.last())
        assertEquals(true, moved.last().isCurrentLocation)
    }

    @Test fun `invalid endpoint indexes return unchanged list`() {
        val endpoints = listOf(endpoint("From"), endpoint("To"))
        assertEquals(endpoints, QuickRouteActions.moveEndpoint(endpoints, -1, 0))
        assertEquals(endpoints, QuickRouteActions.moveEndpoint(endpoints, 0, 2))
    }

    @Test fun `same endpoint index returns unchanged list`() {
        val endpoints = listOf(endpoint("From"), endpoint("Florence"), endpoint("To"))
        assertEquals(endpoints, QuickRouteActions.moveEndpoint(endpoints, 1, 1))
    }

    @Test fun `maximum endpoint count rejects another stop`() {
        val endpoints = (0 until 10).map { endpoint("Point $it", it.toDouble()) }
        assertEquals(endpoints, QuickRouteActions.addStop(endpoints, endpoint("Extra")))
    }

    @Test fun `drawer applies only changed endpoint order`() {
        val original = listOf(endpoint("From"), endpoint("One"), endpoint("Two"), endpoint("To"))
        assertEquals(RouteDrawerAction.DONE, routeDrawerAction(original, original.toList()))
        assertEquals(
            RouteDrawerAction.APPLY_ROUTE,
            routeDrawerAction(original, QuickRouteActions.moveEndpoint(original, 2, 1))
        )
    }
}
