package com.zangrcar.cngitaly.data

import com.zangrcar.cngitaly.data.local.CngPriceEntity
import com.zangrcar.cngitaly.data.local.StationEntity
import com.zangrcar.cngitaly.data.local.StationWithPrices
import java.time.ZonedDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StationDetailsTest {
    @Test
    fun stationWithOnePriceMapsAllLocalDetails() {
        val details = relation(price = listOf(price(1.5, isSelf = false))).toStationDetails()

        assertEquals(7, details.id)
        assertEquals("Test station", details.name)
        assertEquals("Manager", details.manager)
        assertEquals("Brand", details.brand)
        assertEquals("Stradale", details.stationType)
        assertEquals("Roadside", details.stationTypeLabel)
        assertEquals("Via Roma 1, Bologna, BO", details.formattedAddress)
        assertEquals("€1.500/kg", details.prices.single().priceLabel)
        assertEquals("Served", details.prices.single().serviceLabel)
    }

    @Test
    fun selfAndServedPricesRemainSeparateAndSelfWinsEqualPriceTie() {
        val details = relation(
            price = listOf(
                price(1.499, isSelf = false),
                price(1.499, isSelf = true)
            )
        ).toStationDetails()

        assertEquals(2, details.prices.size)
        assertEquals("Self", details.prices[0].serviceLabel)
        assertEquals("Served", details.prices[1].serviceLabel)
    }

    @Test
    fun pricesAreSortedAscending() {
        val details = relation(
            price = listOf(price(1.799, false), price(1.399, true), price(1.599, false))
        ).toStationDetails()

        assertEquals(listOf(1.399, 1.599, 1.799), details.prices.map { it.price })
    }

    @Test
    fun missingAddressFieldsNeverRenderNull() {
        val details = relation(
            station = station(address = null, municipality = "Bologna", province = null),
            price = listOf(price(1.5, true))
        ).toStationDetails()

        assertEquals("Bologna", details.formattedAddress)
        assertFalse(details.formattedAddress.contains("null"))
    }

    @Test
    fun communicatedTimeIsFormattedInEuropeRome() {
        val instant = ZonedDateTime.of(
            2026, 1, 15, 12, 30, 0, 0, ZoneId.of("UTC")
        ).toInstant()
        val details = relation(
            price = listOf(price(1.5, true, instant.toEpochMilli()))
        ).toStationDetails()

        assertEquals("15 Jan 2026, 13:30", details.prices.single().communicatedLabel)
    }

    @Test
    fun officialStationTypesHaveFriendlyEnglishLabels() {
        assertEquals("Roadside", friendlyStationType("Stradale"))
        assertEquals("Motorway", friendlyStationType("Autostradale"))
    }

    @Test
    fun stationTypeMatchingIgnoresCaseAndWhitespace() {
        assertEquals("Roadside", friendlyStationType("  sTrAdAlE "))
        assertEquals("Motorway", friendlyStationType(" AUTOSTRADALE "))
    }

    @Test
    fun unknownStationTypeUsesTrimmedSourceValue() {
        assertEquals("Future type", friendlyStationType("  Future type  "))
    }

    @Test
    fun nullAndBlankStationTypesAreOmitted() {
        assertEquals(null, friendlyStationType(null))
        assertEquals(null, friendlyStationType("   "))
    }

    private fun relation(
        station: StationEntity = station(),
        price: List<CngPriceEntity>
    ) = StationWithPrices(station, price)

    private fun station(
        address: String? = "Via Roma 1",
        municipality: String? = "Bologna",
        province: String? = "BO"
    ) = StationEntity(
        id = 7,
        manager = "Manager",
        brand = "Brand",
        stationType = "Stradale",
        name = "Test station",
        address = address,
        municipality = municipality,
        province = province,
        latitude = 44.5,
        longitude = 11.3
    )

    private fun price(
        value: Double,
        isSelf: Boolean,
        communicatedAt: Long? = null
    ) = CngPriceEntity(
        stationId = 7,
        fuelName = "METANO",
        isSelf = isSelf,
        price = value,
        communicatedAtEpochMillis = communicatedAt
    )
}
