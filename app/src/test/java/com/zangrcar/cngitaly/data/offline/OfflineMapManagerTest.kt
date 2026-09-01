package com.zangrcar.cngitaly.data.offline

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapManagerTest {
    @Test fun `Italy map path and native PMTiles URI are canonical`() {
        val filesDir = Files.createTempDirectory("cng files ").toFile()
        try {
            val manager = OfflineMapManager(filesDir)
            val expected = File(filesDir, "maps/italy.pmtiles").canonicalFile

            assertEquals(expected, manager.italyMapFile)
            assertTrue(manager.italyMapUri().startsWith("pmtiles://file://"))
            assertEquals("pmtiles://file://${expected.toURI().rawPath}", manager.italyMapUri())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test fun `replacement metadata size and deletion track installed map`() {
        val filesDir = Files.createTempDirectory("cng-offline-map").toFile()
        try {
            val downloaded = File(filesDir, "download.tmp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val manager = OfflineMapManager(filesDir)

            manager.replaceItalyMap(downloaded, "2026-09")

            assertTrue(manager.hasItalyMap)
            assertEquals(3L, manager.italyMapSizeBytes)
            assertEquals("2026-09", manager.italyMapVersion)
            assertTrue(downloaded.exists())
            assertTrue(manager.deleteItalyMap())
            assertFalse(manager.hasItalyMap)
            assertEquals(0L, manager.italyMapSizeBytes)
            assertEquals(null, manager.italyMapVersion)
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
