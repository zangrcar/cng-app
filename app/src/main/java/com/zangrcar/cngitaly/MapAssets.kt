package com.zangrcar.cngitaly

internal object MapAssets {
    const val ONLINE_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"
    const val OFFLINE_STYLE_URI = "asset://map/offline.json"
    const val GLYPHS_URI = "asset://map/glyphs/{fontstack}/{range}.pbf"

    val requiredGlyphAssets = listOf(
        "map/glyphs/Noto Sans Regular/0-255.pbf",
        "map/glyphs/Noto Sans Regular/8192-8447.pbf"
    )
}

internal enum class InitialMapStyle(val uri: String) {
    ONLINE_LIBERTY(MapAssets.ONLINE_STYLE_URI),
    OFFLINE_ASSET(MapAssets.OFFLINE_STYLE_URI)
}

internal fun initialMapStyle(validatedInternet: Boolean): InitialMapStyle =
    if (validatedInternet) InitialMapStyle.ONLINE_LIBERTY else InitialMapStyle.OFFLINE_ASSET
