package com.zangrcar.cngitaly.data

import com.zangrcar.cngitaly.data.local.StationWithPrices
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class StationDetails(
    val id: Int,
    val name: String,
    val manager: String?,
    val brand: String?,
    val stationType: String?,
    val address: String?,
    val municipality: String?,
    val province: String?,
    val latitude: Double,
    val longitude: Double,
    val prices: List<StationPrice>
) {
    val formattedAddress: String
        get() = listOf(address, municipality, province)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .joinToString(", ")

    val stationTypeLabel: String?
        get() = friendlyStationType(stationType)
}

data class StationPrice(
    val fuelName: String,
    val price: Double,
    val isSelf: Boolean,
    val communicatedAtEpochMillis: Long?
) {
    val priceLabel: String
        get() = String.format(Locale.ROOT, "€%.3f/kg", price)

    val serviceLabel: String
        get() = if (isSelf) "Self" else "Served"

    val communicatedLabel: String?
        get() = communicatedAtEpochMillis?.let(::formatCommunicatedAt)
}

fun StationWithPrices.toStationDetails(): StationDetails = StationDetails(
    id = station.id,
    name = station.name,
    manager = station.manager,
    brand = station.brand,
    stationType = station.stationType,
    address = station.address,
    municipality = station.municipality,
    province = station.province,
    latitude = station.latitude,
    longitude = station.longitude,
    prices = prices
        .sortedWith(
            compareBy<com.zangrcar.cngitaly.data.local.CngPriceEntity> { it.price }
                .thenBy { if (it.isSelf) 0 else 1 }
                .thenBy { it.fuelName }
        )
        .map {
            StationPrice(
                fuelName = it.fuelName,
                price = it.price,
                isSelf = it.isSelf,
                communicatedAtEpochMillis = it.communicatedAtEpochMillis
            )
        }
)

internal fun formatCommunicatedAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.of("Europe/Rome"))
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH))

internal fun friendlyStationType(value: String?): String? {
    val trimmed = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return when (trimmed.lowercase(Locale.ROOT)) {
        "stradale" -> "Roadside"
        "autostradale" -> "Motorway"
        else -> trimmed
    }
}
