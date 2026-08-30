package com.zangrcar.cngitaly.ui

import com.zangrcar.cngitaly.data.mimit.LiveOpenState
import com.zangrcar.cngitaly.data.mimit.LiveOpenStatus
import com.zangrcar.cngitaly.data.mimit.OpeningHoursEntry
import com.zangrcar.cngitaly.data.mimit.OpeningTimeRange
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveHoursPresentationTest {
    @Test
    fun allSevenDaysUnknownHidesWeeklySection() {
        val entries = DayOfWeek.entries.map { day -> entry(day, isNotCommunicated = true) }

        assertFalse(shouldShowOpeningHoursSection(entries))
    }

    @Test
    fun oneValidWeekdayShowsWeeklySection() {
        val entries = DayOfWeek.entries.map { day ->
            if (day == DayOfWeek.MONDAY) {
                entry(
                    day,
                    ranges = listOf(
                        OpeningTimeRange(LocalTime.of(7, 0), LocalTime.of(19, 0))
                    )
                )
            } else {
                entry(day, isNotCommunicated = true)
            }
        }

        assertTrue(shouldShowOpeningHoursSection(entries))
    }

    @Test
    fun h24WeekdayShowsWeeklySection() {
        assertTrue(shouldShowOpeningHoursSection(listOf(entry(is24Hours = true))))
    }

    @Test
    fun explicitlyClosedWeekdayShowsWeeklySection() {
        assertTrue(shouldShowOpeningHoursSection(listOf(entry(isClosed = true))))
    }

    @Test
    fun unknownCurrentStatusIsHidden() {
        assertNull(visibleLiveStatus(LiveOpenStatus(LiveOpenState.UNKNOWN)))
    }

    @Test
    fun openCurrentStatusIsShown() {
        assertNotNull(visibleLiveStatus(LiveOpenStatus(LiveOpenState.OPEN, "Closes 19:00")))
    }

    @Test
    fun closedCurrentStatusIsShown() {
        assertNotNull(visibleLiveStatus(LiveOpenStatus(LiveOpenState.CLOSED, "Opens 06:45")))
    }

    private fun entry(
        day: DayOfWeek = DayOfWeek.MONDAY,
        is24Hours: Boolean = false,
        isClosed: Boolean = false,
        isNotCommunicated: Boolean = false,
        ranges: List<OpeningTimeRange> = emptyList()
    ) = OpeningHoursEntry(
        dayOfWeek = day,
        is24Hours = is24Hours,
        isClosed = isClosed,
        isNotCommunicated = isNotCommunicated,
        isSelf = false,
        isServed = true,
        ranges = ranges,
        isMalformed = false
    )
}
