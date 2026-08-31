package com.zangrcar.cngitaly.data.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class OsrmClientTest {
    private fun endpoint(name: String, lat: Double, lon: Double) = RouteEndpoint(name, lat, lon)
    @Test fun `OSRM URL preserves A B order`() { assertEquals("10.0,40.0;20.0,50.0", buildOsrmUrl(listOf(endpoint("A",40.0,10.0), endpoint("B",50.0,20.0))).encodedPathSegments.last()) }
    @Test fun `OSRM URL preserves one stop order`() { assertEquals("10.0,40.0;15.0,45.0;20.0,50.0", buildOsrmUrl(listOf(endpoint("A",40.0,10.0), endpoint("S",45.0,15.0), endpoint("B",50.0,20.0))).encodedPathSegments.last()) }
    @Test fun `OSRM URL preserves two stop order`() { assertEquals("10.0,40.0;14.0,44.0;16.0,46.0;20.0,50.0", buildOsrmUrl(listOf(endpoint("A",40.0,10.0), endpoint("S1",44.0,14.0), endpoint("S2",46.0,16.0), endpoint("B",50.0,20.0))).encodedPathSegments.last()) }
    @Test fun `valid route parses coordinate order distance and duration`() {
        val result = OsrmParser.parse(ok("[[12.5,41.9],[13.0,42.0]]", 1234.0, 456.0))
        assertEquals(41.9, result.points.first().latitude, 0.0)
        assertEquals(12.5, result.points.first().longitude, 0.0)
        assertEquals(1234.0, result.distanceMeters, 0.0)
        assertEquals(456.0, result.durationSeconds, 0.0)
    }
    @Test fun `malformed point is skipped when two valid remain`() {
        assertEquals(2, OsrmParser.parse(ok("[[12,41],[\"bad\",42],[13,42]]", 1.0, 1.0)).points.size)
    }
    @Test fun `fewer than two points rejected`() {
        assertThrows(IOException::class.java) { OsrmParser.parse(ok("[[12,41]]", 1.0, 1.0)) }
    }
    @Test fun `NoRoute is clean exception`() {
        assertThrows(NoDrivingRouteException::class.java) { OsrmParser.parse("""{"code":"NoRoute"}""") }
    }
    @Test fun `empty routes rejected`() {
        assertThrows(IOException::class.java) { OsrmParser.parse("""{"code":"Ok","routes":[]}""") }
    }
    private fun ok(points: String, distance: Double, duration: Double) =
        """{"code":"Ok","routes":[{"distance":$distance,"duration":$duration,"geometry":{"coordinates":$points}}]}"""
}
