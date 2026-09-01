package com.zangrcar.cngitaly

internal enum class InitialMapStyle(val textLabelsEnabled: Boolean) {
    ONLINE_LIBERTY(true),
    OFFLINE_ASSET(false)
}

internal fun initialMapStyle(validatedInternet: Boolean): InitialMapStyle =
    if (validatedInternet) InitialMapStyle.ONLINE_LIBERTY else InitialMapStyle.OFFLINE_ASSET
