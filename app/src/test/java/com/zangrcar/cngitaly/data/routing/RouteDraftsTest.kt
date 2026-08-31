package com.zangrcar.cngitaly.data.routing

import com.zangrcar.cngitaly.data.geocoding.PlaceSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteDraftsTest {
    private val fromId = RoutePointId(1); private val stop1Id = RoutePointId(3)
    private val stop2Id = RoutePointId(4); private val toId = RoutePointId(2)
    private fun endpoint(name: String, coordinate: Double = 1.0) = RouteEndpoint(name, coordinate, coordinate)
    private fun drafts() = listOf(
        RoutePointDraft(fromId, RoutePointRole.FROM), RoutePointDraft(stop1Id, RoutePointRole.STOP),
        RoutePointDraft(stop2Id, RoutePointRole.STOP), RoutePointDraft(toId, RoutePointRole.TO)
    )

    @Test fun `result assigned only to From`() {
        val result = RouteDrafts.select(drafts(), fromId, endpoint("From"))
        assertEquals("From", result[0].selectedEndpoint?.label); assertNull(result.last().selectedEndpoint)
    }
    @Test fun `result assigned only to To`() {
        val result = RouteDrafts.select(drafts(), toId, endpoint("To"))
        assertNull(result.first().selectedEndpoint); assertEquals("To", result.last().selectedEndpoint?.label)
    }
    @Test fun `stop selection does not mutate endpoints`() {
        val result = RouteDrafts.select(drafts(), stop1Id, endpoint("Stop"))
        assertNull(result.first().selectedEndpoint); assertNull(result.last().selectedEndpoint)
    }
    @Test fun `removing stop preserves remaining order`() {
        assertEquals(listOf(fromId, stop2Id, toId), RouteDrafts.removeStop(drafts(), stop1Id).map { it.id })
    }
    @Test fun `reordering stops changes endpoint order`() {
        val selected = drafts().mapIndexed { i, d -> d.copy(selectedEndpoint = endpoint("$i", i.toDouble())) }
        assertEquals(listOf("0", "2", "1", "3"), RouteDrafts.endpoints(RouteDrafts.moveStop(selected, stop2Id, -1)).map { it.label })
    }
    @Test fun `Navigate to sets destination and preserves From and stops`() {
        val place = PlaceSearchResult("Gallipoli, Italy", "Gallipoli", 40.05, 17.99)
        val selected = drafts().mapIndexed { index, draft ->
            if (draft.role == RoutePointRole.TO) draft else draft.copy(selectedEndpoint = endpoint("Existing $index", index.toDouble()))
        }
        val result = PlaceMarkerActions.navigateTo(selected, place)
        assertEquals("Gallipoli, Italy", result.last().selectedEndpoint?.label)
        assertEquals("Existing 0", result.first().selectedEndpoint?.label)
        assertEquals(listOf("Existing 1", "Existing 2"), result.filter { it.role == RoutePointRole.STOP }.map { it.selectedEndpoint?.label })
    }
    @Test fun `Navigate to replaces an existing destination`() {
        val place = PlaceSearchResult("Bari, Italy", "Bari", 41.12, 16.87)
        val selected = drafts().map { it.copy(selectedEndpoint = endpoint("Old ${it.role}")) }
        assertEquals("Bari, Italy", PlaceMarkerActions.navigateTo(selected, place).last().selectedEndpoint?.label)
    }
    @Test fun `Add as stop inserts selected place immediately before destination`() {
        val place = PlaceSearchResult("Bologna, Italy", "Bologna", 44.49, 11.34)
        val selected = drafts().map { it.copy(selectedEndpoint = endpoint(it.role.name)) }
        val result = PlaceMarkerActions.addAsStop(selected, place, RoutePointId(9))
        assertEquals(listOf(RoutePointRole.FROM, RoutePointRole.STOP, RoutePointRole.STOP, RoutePointRole.STOP, RoutePointRole.TO), result.map { it.role })
        assertEquals("Bologna, Italy", result[result.lastIndex - 1].selectedEndpoint?.label)
        assertEquals("FROM", result.first().selectedEndpoint?.label)
        assertEquals("TO", result.last().selectedEndpoint?.label)
    }
    @Test fun `Add as stop without selected route keeps empty From and To`() {
        val empty = listOf(RoutePointDraft(fromId, RoutePointRole.FROM), RoutePointDraft(toId, RoutePointRole.TO))
        val place = PlaceSearchResult("Parma, Italy", "Parma", 44.8, 10.32)
        val result = PlaceMarkerActions.addAsStop(empty, place, RoutePointId(9))
        assertNull(result.first().selectedEndpoint)
        assertEquals("Parma, Italy", result[1].selectedEndpoint?.label)
        assertNull(result.last().selectedEndpoint)
    }
    @Test fun `Navigate to preserves My location origin`() {
        val myLocation = endpoint("My location").copy(isCurrentLocation = true)
        val selected = drafts().map { if (it.role == RoutePointRole.FROM) it.copy(selectedEndpoint = myLocation) else it }
        val place = PlaceSearchResult("Rome, Italy", "Rome", 41.9, 12.5)
        val result = PlaceMarkerActions.navigateTo(selected, place)
        assertEquals(myLocation, result.first().selectedEndpoint)
    }
    @Test fun `place marker Remove clears marker`() {
        assertNull(PlaceMarkerActions.remove())
    }

    @Test fun `quick Navigate creates From To request with no confirmation state`() {
        val from = endpoint("From", 1.0); val to = endpoint("To", 2.0)
        assertEquals(listOf(from, to), QuickRouteActions.navigate(from, to))
    }

    @Test fun `quick Add Stop inserts before destination and preserves existing order`() {
        val endpoints = listOf(endpoint("From"), endpoint("Florence"), endpoint("To"))
        assertEquals(
            listOf("From", "Florence", "Bologna", "To"),
            QuickRouteActions.addStop(endpoints, endpoint("Bologna")).map { it.label }
        )
    }

    @Test fun `multiple quick stops retain insertion order`() {
        val first = QuickRouteActions.addStop(listOf(endpoint("From"), endpoint("To")), endpoint("Florence"))
        val second = QuickRouteActions.addStop(first, endpoint("Bologna"))
        assertEquals(listOf("From", "Florence", "Bologna", "To"), second.map { it.label })
    }

    @Test fun `drawer reorders and removes only stops`() {
        val endpoints = listOf(endpoint("From"), endpoint("Florence"), endpoint("Bologna"), endpoint("To"))
        val moved = QuickRouteActions.moveStop(endpoints, 2, -1)
        assertEquals(listOf("From", "Bologna", "Florence", "To"), moved.map { it.label })
        assertEquals(listOf("From", "Florence", "To"), QuickRouteActions.removeStop(endpoints, 2).map { it.label })
    }

    @Test fun `drawer requests route only when endpoint order changed`() {
        val original = listOf(endpoint("From"), endpoint("One"), endpoint("Two"), endpoint("To"))
        assertEquals(RouteDrawerAction.DONE, routeDrawerAction(original, original.toList()))
        assertEquals(RouteDrawerAction.APPLY_ROUTE, routeDrawerAction(original, QuickRouteActions.moveStop(original, 2, -1)))
    }
}
