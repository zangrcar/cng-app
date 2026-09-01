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

    @Test fun `move and remove affect only stops`() {
        val endpoints = listOf(endpoint("From"), endpoint("Florence"), endpoint("Bologna"), endpoint("To"))
        assertEquals(
            listOf("From", "Bologna", "Florence", "To"),
            QuickRouteActions.moveStop(endpoints, 2, -1).map { it.label }
        )
        assertEquals(
            listOf("From", "Florence", "To"),
            QuickRouteActions.removeStop(endpoints, 2).map { it.label }
        )
        assertEquals(endpoints, QuickRouteActions.removeStop(endpoints, 0))
        assertEquals(endpoints, QuickRouteActions.moveStop(endpoints, 1, -1))
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
            routeDrawerAction(original, QuickRouteActions.moveStop(original, 2, -1))
        )
    }
}
