package com.zangrcar.cngitaly

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapAssetsTest {
    @Test fun `online uses remote Liberty and offline uses local minimal style`() {
        assertEquals("https://tiles.openfreemap.org/styles/liberty", MapAssets.ONLINE_STYLE_URI)
        assertEquals("asset://map/offline.json", MapAssets.OFFLINE_STYLE_URI)
        assertEquals("asset://map/glyphs/{fontstack}/{range}.pbf", MapAssets.GLYPHS_URI)
        assertEquals(InitialMapStyle.ONLINE_LIBERTY, initialMapStyle(true))
        assertEquals(InitialMapStyle.OFFLINE_ASSET, initialMapStyle(false))

        val style = assetFile("map/offline.json").readText().filterNot(Char::isWhitespace)
        assertTrue(style.contains("\"glyphs\":\"${MapAssets.GLYPHS_URI}\""))
        assertTrue(style.contains("\"sources\":{}"))
        assertFalse(style.contains("https://"))
        assertFalse(style.contains("\"sprite\""))
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
        tracker.updateDesired(InitialMapStyle.OFFLINE_ASSET)
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
