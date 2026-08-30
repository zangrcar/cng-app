package com.zangrcar.cngitaly.data.mimit

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimitLiveClientTest {
    @Test
    fun liveCngFilteringAcceptsMetanoCngAndGnc() {
        val result = parseFuels(
            fuel("Metano", 1.4, true),
            fuel("CNG", 1.5, false),
            fuel("GNC", 1.6, true)
        )

        assertEquals(listOf("Metano", "CNG", "GNC"), result.cngPrices.map { it.fuelName })
    }

    @Test
    fun liveCngFilteringRejectsLngGnlAndGpl() {
        val result = parseFuels(
            fuel("LNG", 1.4, true),
            fuel("GNL", 1.5, false),
            fuel("GPL", 0.7, true)
        )

        assertTrue(result.cngPrices.isEmpty())
    }

    @Test
    fun liveCngFilteringRejectsInvalidPrices() {
        val result = parseFuels(
            fuel("Metano", 0.0, true),
            fuel("CNG", -1.0, false),
            """{"name":"GNC","price":"NaN","isSelf":true}"""
        )

        assertTrue(result.cngPrices.isEmpty())
    }

    @Test
    fun selfAndServedLivePricesRemainSeparate() {
        val result = parseFuels(
            fuel("Metano", 1.499, true),
            fuel("Metano", 1.499, false)
        )

        assertEquals(2, result.cngPrices.size)
        assertTrue(result.cngPrices[0].isSelf)
        assertFalse(result.cngPrices[1].isSelf)
    }

    @Test
    fun h24TodayIsOpen() {
        assertEquals(LiveOpenState.OPEN, status(entry(is24Hours = true), "2026-08-31T10:00:00Z").state)
    }

    @Test
    fun explicitlyClosedTodayIsClosed() {
        assertEquals(LiveOpenState.CLOSED, status(entry(isClosed = true), "2026-08-31T10:00:00Z").state)
    }

    @Test
    fun timeInsideMorningRangeIsOpen() {
        assertEquals(LiveOpenState.OPEN, status(splitEntry(), "2026-08-31T08:00:00Z").state)
    }

    @Test
    fun timeBetweenSplitRangesIsClosed() {
        assertEquals(LiveOpenState.CLOSED, status(splitEntry(), "2026-08-31T11:30:00Z").state)
    }

    @Test
    fun timeInsideAfternoonRangeIsOpen() {
        assertEquals(LiveOpenState.OPEN, status(splitEntry(), "2026-08-31T13:30:00Z").state)
    }

    @Test
    fun missingTodayIsUnknown() {
        assertEquals(
            LiveOpenState.UNKNOWN,
            liveOpenStatus(emptyList(), Instant.parse("2026-08-31T10:00:00Z")).state
        )
    }

    @Test
    fun notCommunicatedIsUnknown() {
        assertEquals(
            LiveOpenState.UNKNOWN,
            status(entry(isNotCommunicated = true), "2026-08-31T10:00:00Z").state
        )
    }

    @Test
    fun malformedTimeIsUnknown() {
        assertEquals(
            LiveOpenState.UNKNOWN,
            status(entry(isMalformed = true), "2026-08-31T10:00:00Z").state
        )
    }

    @Test
    fun usefulPartialResponseParsesDespiteMalformedOptionalEntry() {
        val json = """
            {
              "id": 42,
              "phoneNumber": " 051 123456 ",
              "services": [{"description":"Car wash"}, null, 7],
              "fuels": [
                {"name":"Metano","price":1.499,"isSelf":true},
                "malformed"
              ],
              "orariapertura": [
                {"giornoSettimanaId":1,"flagH24":true},
                {"giornoSettimanaId":"bad"}
              ]
            }
        """.trimIndent()

        val result = MimitLiveParser.parse(42, json)

        assertEquals("051 123456", result.phoneNumber)
        assertEquals(listOf("Car wash"), result.services)
        assertEquals(1, result.cngPrices.size)
        assertEquals(1, result.openingHours.size)
    }

    @Test
    fun missingOptionalSectionsDoNotFailParsing() {
        val result = MimitLiveParser.parse(42, """{"id":42,"phoneNumber":null}""")

        assertEquals(42, result.stationId)
        assertTrue(result.services.isEmpty())
        assertTrue(result.cngPrices.isEmpty())
        assertTrue(result.openingHours.isEmpty())
    }

    private fun parseFuels(vararg fuels: String): LiveStationDetails = MimitLiveParser.parse(
        42,
        """{"id":42,"fuels":[${fuels.joinToString(",")}]}"""
    )

    private fun fuel(name: String, price: Double, isSelf: Boolean): String =
        """{"name":"$name","price":$price,"isSelf":$isSelf}"""

    private fun status(entry: OpeningHoursEntry, instant: String): LiveOpenStatus =
        liveOpenStatus(listOf(entry), Instant.parse(instant))

    private fun splitEntry() = entry(
        ranges = listOf(
            OpeningTimeRange(LocalTime.of(7, 0), LocalTime.of(12, 30)),
            OpeningTimeRange(LocalTime.of(14, 30), LocalTime.of(19, 0))
        )
    )

    private fun entry(
        is24Hours: Boolean = false,
        isClosed: Boolean = false,
        isNotCommunicated: Boolean = false,
        ranges: List<OpeningTimeRange> = emptyList(),
        isMalformed: Boolean = false
    ) = OpeningHoursEntry(
        dayOfWeek = DayOfWeek.MONDAY,
        is24Hours = is24Hours,
        isClosed = isClosed,
        isNotCommunicated = isNotCommunicated,
        isSelf = false,
        isServed = true,
        ranges = ranges,
        isMalformed = isMalformed
    )
}
