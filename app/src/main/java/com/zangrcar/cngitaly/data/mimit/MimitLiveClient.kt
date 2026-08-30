package com.zangrcar.cngitaly.data.mimit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit

data class LiveStationDetails(
    val stationId: Int,
    val phoneNumber: String?,
    val email: String?,
    val website: String?,
    val services: List<String>,
    val cngPrices: List<LiveCngPrice>,
    val openingHours: List<OpeningHoursEntry>
)

data class LiveCngPrice(
    val fuelName: String,
    val price: Double,
    val isSelf: Boolean,
    val validityDate: String?,
    val insertDate: String?
) {
    val priceLabel: String
        get() = String.format(Locale.ROOT, "€%.3f/kg", price)

    val serviceLabel: String
        get() = if (isSelf) "Self" else "Served"
}

data class OpeningTimeRange(val opens: LocalTime, val closes: LocalTime) {
    fun contains(time: LocalTime): Boolean = if (closes > opens) {
        time >= opens && time < closes
    } else {
        time >= opens || time < closes
    }
}

data class OpeningHoursEntry(
    val dayOfWeek: DayOfWeek,
    val is24Hours: Boolean,
    val isClosed: Boolean,
    val isNotCommunicated: Boolean,
    val isSelf: Boolean,
    val isServed: Boolean,
    val ranges: List<OpeningTimeRange>,
    val isMalformed: Boolean
)

enum class LiveOpenState { OPEN, CLOSED, UNKNOWN }

data class LiveOpenStatus(val state: LiveOpenState, val detail: String? = null)

class MimitLiveClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    suspend fun getStationDetails(stationId: Int): LiveStationDetails =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/$stationId")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("MIMIT live HTTP ${response.code}")
                val json = response.body?.string() ?: throw IOException("Empty MIMIT live response")
                MimitLiveParser.parse(stationId, json)
            }
        }

    companion object {
        private const val BASE_URL =
            "https://carburanti.mise.gov.it/ospzApi/registry/servicearea"
    }
}

object MimitLiveParser {
    fun parse(requestedStationId: Int, json: String): LiveStationDetails {
        val root = JSONObject(json)
        val responseId = root.optionalInt("id")
        require(responseId == null || responseId == requestedStationId) {
            "MIMIT live station ID mismatch"
        }
        return LiveStationDetails(
            stationId = requestedStationId,
            phoneNumber = root.cleanString("phoneNumber"),
            email = root.cleanString("email"),
            website = root.cleanString("website"),
            services = parseServices(root.optJSONArray("services")),
            cngPrices = parseFuels(root.optJSONArray("fuels")),
            openingHours = parseOpeningHours(root.optJSONArray("orariapertura"))
        )
    }

    private fun parseServices(array: JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val value = array.opt(index)
            val description = when (value) {
                is String -> value.clean()
                is JSONObject -> sequenceOf("description", "descrizione", "name", "nome")
                    .mapNotNull { key -> value.cleanString(key) }
                    .firstOrNull()
                else -> null
            }
            if (description != null) add(description)
        }
    }.distinct()

    private fun parseFuels(array: JSONArray?): List<LiveCngPrice> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val fuel = array.optJSONObject(index) ?: continue
            val nestedPrices = fuel.optJSONArray("prices") ?: fuel.optJSONArray("prezzi")
            if (nestedPrices != null) {
                for (priceIndex in 0 until nestedPrices.length()) {
                    parsePrice(nestedPrices.optJSONObject(priceIndex), fuel)?.let(::add)
                }
            } else {
                parsePrice(fuel, null)?.let(::add)
            }
        }
    }.sortedWith(compareBy<LiveCngPrice> { it.price }.thenBy { if (it.isSelf) 0 else 1 })

    private fun parsePrice(price: JSONObject?, parentFuel: JSONObject?): LiveCngPrice? {
        price ?: return null
        val fuelName = sequenceOf("fuelName", "name", "description", "descCarburante", "carburante")
            .mapNotNull { price.cleanString(it) ?: parentFuel?.cleanString(it) }
            .firstOrNull() ?: return null
        if (!MimitCsvParser.isCngFuelName(fuelName)) return null
        val value = price.optionalDouble("price") ?: price.optionalDouble("prezzo") ?: return null
        if (!value.isFinite() || value <= 0.0) return null
        val isSelf = price.optionalBoolean("isSelf")
            ?: price.optionalBoolean("self")
            ?: price.optionalBoolean("flagSelf")
            ?: return null
        return LiveCngPrice(
            fuelName = fuelName,
            price = value,
            isSelf = isSelf,
            validityDate = parseDate(
                price.cleanString("validityDate") ?: price.cleanString("dataValidita")
            ),
            insertDate = parseDate(
                price.cleanString("insertDate") ?: price.cleanString("dataInserimento")
            )
        )
    }

    private fun parseOpeningHours(array: JSONArray?): List<OpeningHoursEntry> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            runCatching { parseOpeningEntry(item) }.getOrNull()?.let(::add)
        }
    }.distinctBy { it.dayOfWeek }

    private fun parseOpeningEntry(item: JSONObject): OpeningHoursEntry? {
        val dayId = item.optionalInt("giornoSettimanaId") ?: return null
        val day = dayId.toDayOfWeek() ?: return null
        val is24Hours = item.optionalBoolean("flagH24") == true
        val isClosed = item.optionalBoolean("flagChiusura") == true
        val isNotCommunicated = item.optionalBoolean("flagNonComunicato") == true
        val continuous = item.optionalBoolean("flagOrarioContinuato") == true
        val pairs = if (continuous) {
            listOf("oraAperturaOrarioContinuato" to "oraChiusuraOrarioContinuato")
        } else {
            listOf(
                "oraAperturaMattina" to "oraChiusuraMattina",
                "oraAperturaPomeriggio" to "oraChiusuraPomeriggio"
            )
        }
        var malformed = false
        val ranges = pairs.mapNotNull { (openKey, closeKey) ->
            val openText = item.cleanString(openKey)
            val closeText = item.cleanString(closeKey)
            if (openText == null && closeText == null) return@mapNotNull null
            val opens = openText?.let(::parseTime)
            val closes = closeText?.let(::parseTime)
            if (opens == null || closes == null || opens == closes) {
                malformed = true
                null
            } else {
                OpeningTimeRange(opens, closes)
            }
        }
        return OpeningHoursEntry(
            dayOfWeek = day,
            is24Hours = is24Hours,
            isClosed = isClosed,
            isNotCommunicated = isNotCommunicated,
            isSelf = item.optionalBoolean("flagSelf") == true,
            isServed = item.optionalBoolean("flagServito") == true,
            ranges = ranges,
            isMalformed = malformed || (is24Hours && isClosed)
        )
    }

    private fun parseDate(value: String?): String? {
        val text = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val parseable = sequenceOf<() -> Any>(
            { Instant.parse(text) },
            { OffsetDateTime.parse(text) },
            { LocalDateTime.parse(text) },
            { LocalDate.parse(text) }
        ).any { parser -> runCatching(parser).isSuccess }
        return text.takeIf { parseable }
    }

    private fun parseTime(value: String): LocalTime? {
        val normalized = value.trim().take(5)
        return runCatching { LocalTime.parse(normalized) }.getOrNull()
    }

    private fun Int.toDayOfWeek(): DayOfWeek? = when (this) {
        1 -> DayOfWeek.MONDAY
        2 -> DayOfWeek.TUESDAY
        3 -> DayOfWeek.WEDNESDAY
        4 -> DayOfWeek.THURSDAY
        5 -> DayOfWeek.FRIDAY
        6 -> DayOfWeek.SATURDAY
        7 -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun JSONObject.cleanString(key: String): String? =
        if (!has(key) || isNull(key)) null else (opt(key) as? String)?.clean()

    private fun JSONObject.optionalInt(key: String): Int? = when (val value = opt(key)) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }

    private fun JSONObject.optionalDouble(key: String): Double? = when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.trim().replace(',', '.').toDoubleOrNull()
        else -> null
    }

    private fun JSONObject.optionalBoolean(key: String): Boolean? = when (val value = opt(key)) {
        is Boolean -> value
        is Number -> when (value.toInt()) { 0 -> false; 1 -> true; else -> null }
        is String -> when (value.trim().lowercase(Locale.ROOT)) {
            "true", "1", "s", "si", "yes" -> true
            "false", "0", "n", "no" -> false
            else -> null
        }
        else -> null
    }

    private fun String.clean(): String? = trim().takeIf(String::isNotEmpty)
}

fun liveOpenStatus(
    openingHours: List<OpeningHoursEntry>,
    now: Instant = Instant.now()
): LiveOpenStatus {
    val romeNow = now.atZone(ZoneId.of("Europe/Rome"))
    val entry = openingHours.firstOrNull { it.dayOfWeek == romeNow.dayOfWeek }
        ?: return LiveOpenStatus(LiveOpenState.UNKNOWN)
    if (entry.isNotCommunicated || entry.isMalformed) {
        return LiveOpenStatus(LiveOpenState.UNKNOWN)
    }
    if (entry.is24Hours) return LiveOpenStatus(LiveOpenState.OPEN, "24 hours")
    if (entry.isClosed) return LiveOpenStatus(LiveOpenState.CLOSED)
    if (entry.ranges.isEmpty()) return LiveOpenStatus(LiveOpenState.UNKNOWN)
    val active = entry.ranges.firstOrNull { it.contains(romeNow.toLocalTime()) }
    return if (active != null) {
        LiveOpenStatus(LiveOpenState.OPEN, "Closes ${formatTime(active.closes)}")
    } else {
        val next = entry.ranges.firstOrNull { it.opens > romeNow.toLocalTime() }
        LiveOpenStatus(
            LiveOpenState.CLOSED,
            next?.let { "Opens ${formatTime(it.opens)}" }
        )
    }
}

fun openingHoursLabel(entry: OpeningHoursEntry?): String = when {
    entry == null || entry.isNotCommunicated || entry.isMalformed -> "Unknown"
    entry.is24Hours -> "24 hours"
    entry.isClosed -> "Closed"
    entry.ranges.isEmpty() -> "Unknown"
    else -> entry.ranges.joinToString(", ") {
        "${formatTime(it.opens)}–${formatTime(it.closes)}"
    }
}

private fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)
