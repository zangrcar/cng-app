package com.zangrcar.cngitaly.data.mimit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class MimitDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    suspend fun downloadSnapshot(): MimitSnapshot = withContext(Dispatchers.IO) {
        val stationsCsv = download(STATIONS_URL)
        val pricesCsv = download(PRICES_URL)
        MimitCsvParser.parseSnapshot(stationsCsv, pricesCsv)
    }

    private fun download(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("MIMIT HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty MIMIT response")
        }
    }

    companion object {
        private const val STATIONS_URL =
            "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"
        private const val PRICES_URL =
            "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
    }
}
