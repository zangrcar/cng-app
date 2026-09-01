package com.zangrcar.cngitaly

import com.zangrcar.cngitaly.ui.map.PlaceWaypointMapLayer
import com.zangrcar.cngitaly.ui.map.StationMapLayer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleSelectionTest {
    @Test fun `validated internet selects Liberty with text labels`() {
        val style = initialMapStyle(true)
        assertEquals(InitialMapStyle.ONLINE_LIBERTY, style)
        assertTrue(style.textLabelsEnabled)
        assertArrayEquals(
            arrayOf(PlaceWaypointMapLayer.TEXT_ID, PlaceWaypointMapLayer.CIRCLE_ID),
            PlaceWaypointMapLayer.interactionLayerIds(style.textLabelsEnabled)
        )
        assertEquals(2, StationMapLayer.stationInteractionLayerIds(style.textLabelsEnabled).size)
    }

    @Test fun `no validated internet selects local style without text labels`() {
        val style = initialMapStyle(false)
        assertEquals(InitialMapStyle.OFFLINE_ASSET, style)
        assertFalse(style.textLabelsEnabled)
        assertArrayEquals(
            arrayOf(PlaceWaypointMapLayer.CIRCLE_ID),
            PlaceWaypointMapLayer.interactionLayerIds(style.textLabelsEnabled)
        )
        assertEquals(1, StationMapLayer.stationInteractionLayerIds(style.textLabelsEnabled).size)
    }
}
