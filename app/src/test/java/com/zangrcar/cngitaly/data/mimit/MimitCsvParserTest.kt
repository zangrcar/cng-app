package com.zangrcar.cngitaly.data.mimit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MimitCsvParserTest {
    @Test
    fun pipeDelimitedCsvWithMetadataAndBomIsParsed() {
        val result = MimitCsvParser.parseStations(
            "\uFEFFExport generated 2026-08-29\nignored metadata\n$stationHeader\n${stationRow(1)}"
        )

        assertEquals("2026-08-29", result.extractionDate)
        assertEquals(1, result.rows.single().id)
        assertEquals("Station One", result.rows.single().name)
    }

    @Test
    fun quotedPipeRemainsInsideField() {
        val csv = "$stationHeader\n${stationRow(1, name = "\"Station | One\"")}"

        assertEquals("Station | One", MimitCsvParser.parseStations(csv).rows.single().name)
    }

    @Test
    fun cngClassifierAcceptsSupportedNames() {
        listOf("Metano", "METANO SPECIALE", "CNG", "GNC", "L-GNC").forEach {
            assertTrue(it, MimitCsvParser.isCngFuelName(it))
        }
    }

    @Test
    fun cngClassifierRejectsOtherFuels() {
        listOf("GNL", "LNG", "Metano Liquido", "GPL", "Benzina", "Gasolio").forEach {
            assertFalse(it, MimitCsvParser.isCngFuelName(it))
        }
    }

    @Test
    fun validStationsAndPricesAreMerged() {
        val snapshot = MimitCsvParser.parseSnapshot(
            "$stationHeader\n${stationRow(7)}",
            "$priceHeader\n${priceRow(7, "METANO", "1", "1.499")}" 
        )

        assertEquals(listOf(7), snapshot.stations.map { it.id })
        assertEquals(7, snapshot.prices.single().stationId)
    }

    @Test
    fun selfAndServedPricesRemainDistinct() {
        val snapshot = MimitCsvParser.parseSnapshot(
            "$stationHeader\n${stationRow(7)}",
            "$priceHeader\n${priceRow(7, "METANO", "0", "1.5")}\n" +
                priceRow(7, "METANO", "1", "1.4")
        )

        assertEquals(setOf(false, true), snapshot.prices.map { it.isSelf }.toSet())
    }

    @Test
    fun malformedPricesAreSkipped() {
        val csv = "$priceHeader\n" +
            priceRow(1, "METANO", "1", "not-a-price") + "\n" +
            priceRow(1, "METANO", "2", "1.5") + "\n" +
            priceRow(1, "METANO", "0", "-1")

        assertTrue(MimitCsvParser.parsePrices(csv).rows.isEmpty())
    }

    @Test
    fun invalidCoordinatesAreSkipped() {
        val csv = "$stationHeader\n" +
            stationRow(1, latitude = "91", longitude = "12") + "\n" +
            stationRow(2, latitude = "45", longitude = "181") + "\n" +
            stationRow(3, latitude = "0", longitude = "0") + "\n" +
            stationRow(4, latitude = "bad", longitude = "12")

        assertTrue(MimitCsvParser.parseStations(csv).rows.isEmpty())
    }

    @Test
    fun stationWithoutValidCngPriceIsExcluded() {
        val snapshot = MimitCsvParser.parseSnapshot(
            "$stationHeader\n${stationRow(1)}\n${stationRow(2)}",
            "$priceHeader\n${priceRow(1, "GPL", "1", "0.7")}\n" +
                priceRow(2, "METANO", "1", "1.4")
        )

        assertEquals(listOf(2), snapshot.stations.map { it.id })
    }

    @Test
    fun stationNameUsesRequiredFallbacks() {
        val managerFallback = stationRow(1, manager = "Manager", name = "")
        val genericFallback = stationRow(2, manager = "", name = "")
        val rows = MimitCsvParser.parseStations("$stationHeader\n$managerFallback\n$genericFallback").rows

        assertEquals("Manager", rows[0].name)
        assertEquals("Station 2", rows[1].name)
    }

    @Test
    fun winterCommunicationTimeUsesRomeZone() {
        val price = MimitCsvParser.parsePrices(
            "$priceHeader\n${priceRow(1, "METANO", "1", "1.5", "15/01/2026 08:00:00")}"
        ).rows.single()

        assertEquals(Instant.parse("2026-01-15T07:00:00Z").toEpochMilli(), price.communicatedAtEpochMillis)
    }

    @Test
    fun summerCommunicationTimeUsesRomeZone() {
        val price = MimitCsvParser.parsePrices(
            "$priceHeader\n${priceRow(1, "METANO", "1", "1.5", "15/07/2026 08:00:00")}"
        ).rows.single()

        assertEquals(Instant.parse("2026-07-15T06:00:00Z").toEpochMilli(), price.communicatedAtEpochMillis)
    }

    @Test
    fun malformedCommunicationTimeBecomesNullWithoutDroppingPrice() {
        val price = MimitCsvParser.parsePrices(
            "$priceHeader\n${priceRow(1, "METANO", "1", "1.5", "bad-date")}"
        ).rows.single()

        assertNull(price.communicatedAtEpochMillis)
    }

    private fun stationRow(
        id: Int,
        manager: String = "Manager",
        name: String = "Station One",
        latitude: String = "45.0",
        longitude: String = "12.0"
    ) = "$id|$manager|Brand|Road|$name|Address|Town|TV|$latitude|$longitude"

    private fun priceRow(
        id: Int,
        fuel: String,
        self: String,
        price: String,
        date: String = "29/08/2026 08:00:00"
    ) = "$id|$fuel|$price|$self|$date"

    companion object {
        private const val stationHeader =
            "idimpianto|Gestore|Bandiera|Tipo Impianto|Nome Impianto|Indirizzo|Comune|Provincia|Latitudine|Longitudine"
        private const val priceHeader = "idimpianto|descCarburante|prezzo|isSelf|dtComu"
    }
}
