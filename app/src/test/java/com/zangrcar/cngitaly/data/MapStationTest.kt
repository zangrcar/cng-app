package com.zangrcar.cngitaly.data

import com.zangrcar.cngitaly.data.local.CngPriceEntity
import com.zangrcar.cngitaly.data.local.StationEntity
import com.zangrcar.cngitaly.data.local.StationWithPrices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStationTest {
    @Test
    fun oneServedPriceIsSelected() {
        val result = relation(price(1.499, false)).toMapStation()!!

        assertEquals(1.499, result.displayPrice, 0.0)
        assertFalse(result.displayPriceIsSelf)
    }

    @Test
    fun oneSelfPriceIsSelected() {
        val result = relation(price(1.499, true)).toMapStation()!!

        assertEquals(1.499, result.displayPrice, 0.0)
        assertTrue(result.displayPriceIsSelf)
    }

    @Test
    fun lowerOfSelfAndServedPricesIsSelected() {
        val result = relation(price(1.599, false), price(1.499, true)).toMapStation()!!

        assertEquals(1.499, result.displayPrice, 0.0)
        assertTrue(result.displayPriceIsSelf)
    }

    @Test
    fun lowestOfMultipleCngPricesIsSelected() {
        val result = relation(
            price(1.699, false, "METANO SPECIALE"),
            price(1.459, false, "METANO"),
            price(1.499, true, "CNG")
        ).toMapStation()!!

        assertEquals(1.459, result.displayPrice, 0.0)
        assertFalse(result.displayPriceIsSelf)
    }

    @Test
    fun priceLabelAlwaysKeepsThreeDecimalPlaces() {
        assertEquals("€1.500", relation(price(1.5, true)).toMapStation()!!.priceLabel)
        assertEquals("€1.499", relation(price(1.499, true)).toMapStation()!!.priceLabel)
    }

    private fun relation(vararg prices: CngPriceEntity) = StationWithPrices(
        station = StationEntity(
            id = 7,
            manager = null,
            brand = null,
            stationType = null,
            name = "Test station",
            address = null,
            municipality = null,
            province = null,
            latitude = 45.0,
            longitude = 12.0
        ),
        prices = prices.toList()
    )

    private fun price(value: Double, self: Boolean, fuel: String = "METANO") = CngPriceEntity(
        stationId = 7,
        fuelName = fuel,
        isSelf = self,
        price = value,
        communicatedAtEpochMillis = null
    )
}
