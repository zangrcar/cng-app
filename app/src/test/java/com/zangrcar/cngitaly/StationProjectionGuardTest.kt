package com.zangrcar.cngitaly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationProjectionGuardTest {
    @Test fun `new corridor invalidates stale corridor result`() {
        val guard = StationProjectionGuard()
        val auto = guard.next()
        guard.next()
        val twentyKm = guard.next()
        assertFalse(guard.isCurrent(auto))
        assertTrue(guard.isCurrent(twentyKm))
    }

    @Test fun `route clear invalidates outstanding route projection`() {
        val guard = StationProjectionGuard()
        val route = guard.next()
        val normalAfterClear = guard.next()
        assertFalse(guard.isCurrent(route))
        assertTrue(guard.isCurrent(normalAfterClear))
    }

    @Test fun `route request invalidates stale normal load`() {
        val guard = StationProjectionGuard()
        val normal = guard.next()
        val route = guard.next()
        assertFalse(guard.isCurrent(normal))
        assertTrue(guard.isCurrent(route))
    }

    @Test fun `new route request invalidates old route request`() {
        val guard = StationProjectionGuard()
        val oldRoute = guard.next()
        val newRoute = guard.next()
        assertFalse(guard.isCurrent(oldRoute))
        assertTrue(guard.isCurrent(newRoute))
    }

    @Test fun `pending route location takes priority over camera centering`() {
        assertEquals(ResolvedLocationAction.ROUTE, resolvedLocationAction(true, true))
        assertEquals(ResolvedLocationAction.CENTER, resolvedLocationAction(false, true))
        assertEquals(ResolvedLocationAction.NONE, resolvedLocationAction(false, false))
    }
}
