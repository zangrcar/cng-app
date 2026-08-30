package com.zangrcar.cngitaly.data

import com.zangrcar.cngitaly.data.local.StationWithPrices
import java.util.Locale

data class MapBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
)

data class MapStation(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val displayPrice: Double,
    val displayPriceIsSelf: Boolean
) {
    val priceLabel: String
        get() = String.format(Locale.ROOT, "€%.3f", displayPrice)
}

fun StationWithPrices.toMapStation(): MapStation? {
    val lowestPrice = prices.minByOrNull { it.price } ?: return null
    return MapStation(
        id = station.id,
        name = station.name,
        latitude = station.latitude,
        longitude = station.longitude,
        displayPrice = lowestPrice.price,
        displayPriceIsSelf = lowestPrice.isSelf
    )
}
