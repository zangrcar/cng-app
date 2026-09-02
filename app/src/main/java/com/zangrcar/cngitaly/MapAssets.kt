package com.zangrcar.cngitaly

internal object MapAssets {
    const val ONLINE_STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"
    const val OFFLINE_MINIMAL_STYLE_URI = "asset://map/offline.json"
    const val OFFLINE_PMTILES_STYLE_TEMPLATE = "map/offline_pmtiles.json.template"
    const val PMTILES_URL_PLACEHOLDER = "__ITALY_PMTILES_URL__"
    const val GLYPHS_URI = "asset://map/glyphs/{fontstack}/{range}.pbf"

    val requiredGlyphAssets = listOf(
        "map/glyphs/Noto Sans Regular/0-255.pbf",
        "map/glyphs/Noto Sans Regular/8192-8447.pbf"
    )
}

internal enum class InitialMapStyle(val uri: String) {
    ONLINE_LIBERTY(MapAssets.ONLINE_STYLE_URI),
    OFFLINE_PMTILES(""),
    OFFLINE_MINIMAL(MapAssets.OFFLINE_MINIMAL_STYLE_URI)
}

internal fun initialMapStyle(
    validatedInternet: Boolean,
    hasItalyPmtiles: Boolean
): InitialMapStyle = when {
    validatedInternet -> InitialMapStyle.ONLINE_LIBERTY
    hasItalyPmtiles -> InitialMapStyle.OFFLINE_PMTILES
    else -> InitialMapStyle.OFFLINE_MINIMAL
}

internal fun mapStyleFallbackAfterLoadFailure(
    failedStyle: InitialMapStyle?,
    hasItalyPmtiles: Boolean
): InitialMapStyle? = when (failedStyle) {
    InitialMapStyle.ONLINE_LIBERTY ->
        if (hasItalyPmtiles) {
            InitialMapStyle.OFFLINE_PMTILES
        } else {
            InitialMapStyle.OFFLINE_MINIMAL
        }

    InitialMapStyle.OFFLINE_PMTILES ->
        InitialMapStyle.OFFLINE_MINIMAL

    InitialMapStyle.OFFLINE_MINIMAL,
    null -> null
}

internal data class MapStyleRequest(val style: InitialMapStyle, val generation: Long)

internal class MapStyleRequestTracker {
    var desiredStyle: InitialMapStyle? = null
        private set
    var requestedStyle: InitialMapStyle? = null
        private set
    private var generation = 0L

    fun updateDesired(style: InitialMapStyle) {
        desiredStyle = style
    }

    fun nextRequest(): MapStyleRequest? {
        val desired = desiredStyle ?: return null
        if (requestedStyle == desired) return null
        requestedStyle = desired
        return MapStyleRequest(desired, ++generation)
    }

    fun isAuthoritative(request: MapStyleRequest): Boolean =
        request.generation == generation &&
            request.style == desiredStyle &&
            request.style == requestedStyle
}
