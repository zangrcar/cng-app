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
