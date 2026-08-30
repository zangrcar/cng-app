package com.zangrcar.cngitaly.data.mimit

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class MimitStation(
    val id: Int,
    val manager: String?,
    val brand: String?,
    val stationType: String?,
    val name: String,
    val address: String?,
    val municipality: String?,
    val province: String?,
    val latitude: Double,
    val longitude: Double
)

data class MimitPrice(
    val stationId: Int,
    val fuelName: String,
    val isSelf: Boolean,
    val price: Double,
    val communicatedAtEpochMillis: Long?
)

data class ParsedCsv<T>(val extractionDate: String?, val rows: List<T>)

data class MimitSnapshot(
    val stationDatasetDate: String?,
    val priceDatasetDate: String?,
    val stations: List<MimitStation>,
    val prices: List<MimitPrice>
)

object MimitCsvParser {
    private val extractionDateRegex = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b")
    private val communicatedAtFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
    private val romeZone = ZoneId.of("Europe/Rome")

    fun parseStations(csv: String): ParsedCsv<MimitStation> {
        val table = readTable(csv)
        val stations = table.rows.mapNotNull { values ->
            val id = values.value(table.header, "idimpianto")?.toIntOrNull() ?: return@mapNotNull null
            if (id <= 0) return@mapNotNull null
            val latitude = values.value(table.header, "latitudine")?.toDoubleOrNull()
                ?: return@mapNotNull null
            val longitude = values.value(table.header, "longitudine")?.toDoubleOrNull()
                ?: return@mapNotNull null
            if (!validCoordinates(latitude, longitude)) return@mapNotNull null

            val manager = values.value(table.header, "gestore").clean()
            val suppliedName = values.value(table.header, "nomeimpianto").clean()
            MimitStation(
                id = id,
                manager = manager,
                brand = values.value(table.header, "bandiera").clean(),
                stationType = values.value(table.header, "tipoimpianto").clean(),
                name = suppliedName ?: manager ?: "Station $id",
                address = values.value(table.header, "indirizzo").clean(),
                municipality = values.value(table.header, "comune").clean(),
                province = values.value(table.header, "provincia").clean(),
                latitude = latitude,
                longitude = longitude
            )
        }
        return ParsedCsv(table.extractionDate, stations)
    }

    fun parsePrices(csv: String): ParsedCsv<MimitPrice> {
        val table = readTable(csv)
        val prices = table.rows.mapNotNull { values ->
            val stationId = values.value(table.header, "idimpianto")?.toIntOrNull()
                ?: return@mapNotNull null
            if (stationId <= 0) return@mapNotNull null
            val fuelName = values.value(table.header, "desccarburante").clean()
                ?: return@mapNotNull null
            if (!isCngFuelName(fuelName)) return@mapNotNull null
            val price = values.value(table.header, "prezzo")?.toDoubleOrNull()
                ?: return@mapNotNull null
            if (!price.isFinite() || price <= 0.0) return@mapNotNull null
            val selfValue = values.value(table.header, "isself")?.trim()
            val isSelf = when (selfValue) {
                "0" -> false
                "1" -> true
                else -> return@mapNotNull null
            }
            MimitPrice(
                stationId = stationId,
                fuelName = fuelName,
                isSelf = isSelf,
                price = price,
                communicatedAtEpochMillis = parseCommunicatedAt(
                    values.value(table.header, "dtcomu")
                )
            )
        }
        return ParsedCsv(table.extractionDate, prices)
    }

    fun parseSnapshot(stationsCsv: String, pricesCsv: String): MimitSnapshot {
        val stationResult = parseStations(stationsCsv)
        val priceResult = parsePrices(pricesCsv)
        val stationsById = stationResult.rows.associateBy { it.id }
        val validPrices = priceResult.rows
            .filter { it.stationId in stationsById }
            .associateBy { Triple(it.stationId, it.fuelName, it.isSelf) }
            .values
            .toList()
        val stationIdsWithPrices = validPrices.asSequence().map { it.stationId }.toSet()
        val usableStations = stationsById.values.filter { it.id in stationIdsWithPrices }
        return MimitSnapshot(
            stationDatasetDate = stationResult.extractionDate,
            priceDatasetDate = priceResult.extractionDate,
            stations = usableStations,
            prices = validPrices,
        )
    }

    fun isCngFuelName(name: String): Boolean {
        val normalized = name.trim().uppercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
        if (
            normalized.contains("METANO LIQUIDO") ||
            Regex("(^|[^A-Z])(GNL|LNG|GPL|BENZINA|GASOLIO)([^A-Z]|$)").containsMatchIn(normalized)
        ) return false
        return normalized.contains("METANO") ||
            Regex("(^|[^A-Z])(CNG|GNC)([^A-Z]|$)").containsMatchIn(normalized) ||
            normalized.contains("L-GNC")
    }

    private fun parseCommunicatedAt(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalDateTime.parse(text, communicatedAtFormatter)
                .atZone(romeZone)
                .toInstant()
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)

    private data class CsvTable(
        val extractionDate: String?,
        val header: Map<String, Int>,
        val rows: List<List<String>>
    )

    private fun readTable(csv: String): CsvTable {
        val lines = csv.removePrefix("\uFEFF").lineSequence().toList()
        val headerIndex = lines.indexOfFirst { line ->
            parsePipeLine(line).any { normalizeHeader(it) == "idimpianto" }
        }
        require(headerIndex >= 0) { "MIMIT CSV header not found" }
        val headerValues = parsePipeLine(lines[headerIndex])
        val header = headerValues.mapIndexed { index, name -> normalizeHeader(name) to index }.toMap()
        val extractionDate = lines.take(headerIndex)
            .firstNotNullOfOrNull { extractionDateRegex.find(it)?.value }
        val rows = lines.drop(headerIndex + 1)
            .filter { it.isNotBlank() }
            .map(::parsePipeLine)
        return CsvTable(extractionDate, header, rows)
    }

    private fun parsePipeLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == '|' && !quoted -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        result += current.toString()
        return result
    }

    private fun normalizeHeader(value: String): String =
        value.removePrefix("\uFEFF").trim().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun List<String>.value(header: Map<String, Int>, name: String): String? =
        header[name]?.let(::getOrNull)

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
