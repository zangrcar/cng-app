package com.zangrcar.cngitaly

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class MapAssetsTest {
    @Test fun `map mode selection prefers online then installed PMTiles then minimal`() {
        assertEquals("https://tiles.openfreemap.org/styles/liberty", MapAssets.ONLINE_STYLE_URI)
        assertEquals("asset://map/offline.json", MapAssets.OFFLINE_MINIMAL_STYLE_URI)
        assertEquals("asset://map/glyphs/{fontstack}/{range}.pbf", MapAssets.GLYPHS_URI)
        assertEquals(InitialMapStyle.ONLINE_LIBERTY, initialMapStyle(true, false))
        assertEquals(InitialMapStyle.ONLINE_LIBERTY, initialMapStyle(true, true))
        assertEquals(InitialMapStyle.OFFLINE_PMTILES, initialMapStyle(false, true))
        assertEquals(InitialMapStyle.OFFLINE_MINIMAL, initialMapStyle(false, false))

        val style = assetFile("map/offline.json").readText().filterNot(Char::isWhitespace)
        assertTrue(style.contains("\"glyphs\":\"${MapAssets.GLYPHS_URI}\""))
        assertTrue(style.contains("\"sources\":{}"))
        assertFalse(style.contains("https://"))
        assertFalse(style.contains("\"sprite\""))
    }

    @Test fun `PMTiles template uses local vector source and Protomaps schema`() {
        val style = assetFile(MapAssets.OFFLINE_PMTILES_STYLE_TEMPLATE).readText()
        assertTrue(style.contains("\"url\": \"${MapAssets.PMTILES_URL_PLACEHOLDER}\""))
        assertTrue(style.contains("\"type\": \"vector\""))
        listOf("earth", "water", "roads", "boundaries", "places").forEach { sourceLayer ->
            assertTrue(style.contains("\"source-layer\": \"$sourceLayer\""))
        }
        val runtimeStyle = style.replace(
            MapAssets.PMTILES_URL_PLACEHOLDER,
            "pmtiles://file:///data/user/0/com.zangrcar.cngitaly/files/maps/italy.pmtiles"
        )
        assertEquals(8, JSONObject(runtimeStyle).getInt("version"))
    }

    @Test fun `required app glyph ranges are bundled and nonempty`() {
        MapAssets.requiredGlyphAssets.forEach { path ->
            val glyph = assetFile(path)
            assertTrue("Missing glyph asset: $path", glyph.isFile)
            assertTrue("Empty glyph asset: $path", glyph.length() > 0L)
        }
    }

    @Test fun `latest desired style wins and stale callback is rejected`() {
        val tracker = MapStyleRequestTracker()
        tracker.updateDesired(InitialMapStyle.OFFLINE_MINIMAL)
        val offline = tracker.nextRequest()!!
        tracker.updateDesired(InitialMapStyle.ONLINE_LIBERTY)
        val online = tracker.nextRequest()!!

        assertFalse(tracker.isAuthoritative(offline))
        assertTrue(tracker.isAuthoritative(online))
    }

    @Test fun `repeated desired style does not create duplicate request`() {
        val tracker = MapStyleRequestTracker()
        tracker.updateDesired(InitialMapStyle.ONLINE_LIBERTY)
        assertEquals(InitialMapStyle.ONLINE_LIBERTY, tracker.nextRequest()!!.style)
        tracker.updateDesired(InitialMapStyle.ONLINE_LIBERTY)
        assertEquals(null, tracker.nextRequest())
    }

    private fun assetFile(relativePath: String) = File("src/main/assets", relativePath)
}
