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
    @Test fun `place marker Route here prefills destination`() {
        val place = PlaceSearchResult("Gallipoli, Italy", "Gallipoli", 40.05, 17.99)
        val result = PlaceMarkerActions.routeHere(drafts(), place)
        assertEquals("Gallipoli, Italy", result.last().selectedEndpoint?.label)
        assertNull(result.first().selectedEndpoint)
    }
    @Test fun `place marker Remove clears marker`() {
        assertNull(PlaceMarkerActions.remove())
    }
}
