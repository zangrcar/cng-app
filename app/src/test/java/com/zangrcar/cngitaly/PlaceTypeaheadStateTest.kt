package com.zangrcar.cngitaly

import com.zangrcar.cngitaly.data.geocoding.normalizePlaceQuery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceTypeaheadStateTest {
    @Test fun `queries shorter than two normalized characters do not search`() {
        assertFalse(shouldSearchPlaceQuery(""))
        assertFalse(shouldSearchPlaceQuery(" a "))
        assertTrue(shouldSearchPlaceQuery("ab"))
    }

    @Test fun `stale response cannot update a newer query`() {
        val requested = normalizePlaceQuery("Gallipoli")
        assertFalse(typeaheadQueryIsCurrent("Galatina", requested))
        assertTrue(typeaheadQueryIsCurrent("  GALLIPOLI ", requested))
    }
}
