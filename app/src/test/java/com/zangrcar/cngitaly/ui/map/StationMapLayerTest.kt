package com.zangrcar.cngitaly.ui.map

import com.zangrcar.cngitaly.data.MapStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point

class StationMapLayerTest {
    @Test
    fun geoJsonUsesLongitudeThenLatitudeAndIncludesStyleProperties() {
        val collection = listOf(
            MapStation(
                id = 42,
                name = "Italy station",
                latitude = 45.25,
                longitude = 11.75,
                displayPrice = 1.499,
                displayPriceIsSelf = true
            )
        ).toFeatureCollection()

        val feature = collection.features()!!.single()
        val point = feature.geometry() as Point
        assertEquals(11.75, point.longitude(), 0.0)
        assertEquals(45.25, point.latitude(), 0.0)
        assertEquals(42, feature.getNumberProperty("stationId").toInt())
        assertEquals("Italy station", feature.getStringProperty("stationName"))
        assertEquals(1.499, feature.getNumberProperty("price").toDouble(), 0.0)
        assertEquals("€1.499", feature.getStringProperty("priceLabel"))
        assertTrue(feature.getBooleanProperty("isSelf"))
        assertFalse(feature.hasProperty("point_count"))
    }
}
