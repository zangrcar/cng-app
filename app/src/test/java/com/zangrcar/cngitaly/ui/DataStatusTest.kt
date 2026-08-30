package com.zangrcar.cngitaly.ui

import com.zangrcar.cngitaly.MainUiState
import com.zangrcar.cngitaly.data.local.DatasetMetaEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStatusTest {
    private val now = Instant.parse("2026-08-31T12:00:00Z")

    @Test
    fun successfulRefreshFiveMinutesAgoIsFresh() {
        assertTrue(isLocalDataFresh(state(refreshedAt = now.minus(Duration.ofMinutes(5))), now))
    }

    @Test
    fun successfulRefreshTwentyThreeHoursFiftyNineMinutesAgoIsFresh() {
        val refreshedAt = now.minus(Duration.ofHours(23)).minus(Duration.ofMinutes(59))

        assertTrue(isLocalDataFresh(state(refreshedAt = refreshedAt), now))
    }

    @Test
    fun successfulRefreshAtLeastTwentyFourHoursAgoIsStale() {
        assertFalse(isLocalDataFresh(state(refreshedAt = now.minus(Duration.ofHours(24))), now))
        assertEquals(
            DataStatus.STALE,
            dataStatus(state(refreshedAt = now.minus(Duration.ofHours(25))), now)
        )
    }

    @Test
    fun crossingMidnightDoesNotMakeDataStale() {
        val zone = ZoneId.of("Europe/Rome")
        val afterMidnight = ZonedDateTime.of(2026, 8, 31, 0, 5, 0, 0, zone).toInstant()
        val beforeMidnight = ZonedDateTime.of(2026, 8, 30, 23, 55, 0, 0, zone).toInstant()

        assertTrue(isLocalDataFresh(state(refreshedAt = beforeMidnight), afterMidnight))
    }

    @Test
    fun oldMimitDatasetDateDoesNotMakeRecentRefreshStale() {
        val state = state(
            refreshedAt = now.minus(Duration.ofMinutes(5)),
            priceDatasetDate = "2026-08-20"
        )

        assertEquals(DataStatus.FRESH, dataStatus(state, now))
    }

    @Test
    fun failedRefreshDoesNotMakeOldLastSuccessfulRefreshFresh() {
        val unchangedAfterFailedAttempt = state(refreshedAt = now.minus(Duration.ofHours(25)))

        assertFalse(isLocalDataFresh(unchangedAfterFailedAttempt, now))
        assertEquals(DataStatus.STALE, dataStatus(unchangedAfterFailedAttempt, now))
    }

    private fun state(
        refreshedAt: Instant,
        priceDatasetDate: String = "2026-08-30"
    ) = MainUiState(
        metadata = DatasetMetaEntity(
            stationDatasetDate = "2026-08-30",
            priceDatasetDate = priceDatasetDate,
            lastSuccessfulRefreshEpochMillis = refreshedAt.toEpochMilli(),
            stationCount = 1_604
        ),
        isOnline = true
    )
}
