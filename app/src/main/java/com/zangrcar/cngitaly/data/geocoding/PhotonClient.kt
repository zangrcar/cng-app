package com.zangrcar.cngitaly.data.geocoding

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PlaceSearchResult(
    val displayName: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

class PhotonClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val rateLimitMutex = Mutex()
    private val cacheMutex = Mutex()
    private var lastRequestStartedNanos: Long? = null
    private val cache = object : LinkedHashMap<String, List<PlaceSearchResult>>(32, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<PlaceSearchResult>>?
        ): Boolean = size > CACHE_SIZE
    }

    suspend fun search(query: String): List<PlaceSearchResult> {
        val cacheKey = normalizePlaceQuery(query)
        if (cacheKey.isEmpty()) return emptyList()
        cacheMutex.withLock { cache[cacheKey]?.let { return it } }

        rateLimitMutex.withLock {
            val previous = lastRequestStartedNanos
            if (previous != null) {
                val remaining = MIN_REQUEST_INTERVAL_NANOS - (System.nanoTime() - previous)
                if (remaining > 0) delay((remaining + 999_999) / 1_000_000)
            }
            lastRequestStartedNanos = System.nanoTime()
        }

        val language = Locale.getDefault().language
            .lowercase(Locale.ROOT)
            .takeIf { it in setOf("de", "en", "fr", "it") }
            ?: "en"
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .addQueryParameter("limit", "10")
            .addQueryParameter("countrycode", "IT")
            .addQueryParameter("lang", language)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        val results = rankPlaceResults(PhotonParser.parse(client.newCall(request).awaitBody()), query)
            .take(5)
        cacheMutex.withLock { cache[cacheKey] = results }
        return results
    }

    private suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException("Photon HTTP ${response.code}"))
                        }
                        return
                    }
                    val body = response.body?.string()
                    if (body == null) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException("Empty Photon response"))
                        }
                    } else if (continuation.isActive) {
                        continuation.resume(body)
                    }
                }
            }
        })
    }

    companion object {
        private const val BASE_URL = "https://photon.komoot.io/api"
        private const val USER_AGENT = "CNGItaly/1.0 (Android; com.zangrcar.cngitaly)"
        private const val CACHE_SIZE = 32
        private const val MIN_REQUEST_INTERVAL_NANOS = 1_000_000_000L
    }
}

object PhotonParser {
    fun parse(json: String): List<PlaceSearchResult> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        return buildList {
            for (index in 0 until features.length()) {
                val feature = features.optJSONObject(index) ?: continue
                val coordinates = feature.optJSONObject("geometry")
                    ?.optJSONArray("coordinates") ?: continue
                if (coordinates.length() < 2) continue
                val longitude = coordinates.optDouble(0, Double.NaN)
                val latitude = coordinates.optDouble(1, Double.NaN)
                if (!latitude.isFinite() || latitude !in -90.0..90.0) continue
                if (!longitude.isFinite() || longitude !in -180.0..180.0) continue
                val properties = feature.optJSONObject("properties") ?: continue
                val name = properties.cleanString("name") ?: continue
                add(
                    PlaceSearchResult(
                        displayName = buildDisplayName(name, properties),
                        name = name,
                        latitude = latitude,
                        longitude = longitude
                    )
                )
            }
        }
    }

    private fun buildDisplayName(name: String, properties: JSONObject): String {
        val street = properties.cleanString("street")
        val houseNumber = properties.cleanString("housenumber")
        val streetAddress = listOfNotNull(street, houseNumber).joinToString(" ")
            .takeIf(String::isNotBlank)
        val candidates = listOfNotNull(
            name,
            streetAddress,
            properties.cleanString("postcode"),
            properties.cleanString("city"),
            properties.cleanString("county"),
            properties.cleanString("state"),
            properties.cleanString("country")
        )
        val seen = mutableSetOf<String>()
        return candidates.filter { seen.add(normalizePlaceQuery(it)) }.joinToString(", ")
    }

    private fun JSONObject.cleanString(key: String): String? =
        (opt(key) as? String)?.trim()?.takeIf(String::isNotEmpty)
}

fun rankPlaceResults(
    results: List<PlaceSearchResult>,
    query: String
): List<PlaceSearchResult> {
    val normalizedQuery = normalizePlaceQuery(query)
    return results.withIndex()
        .sortedWith(compareBy<IndexedValue<PlaceSearchResult>> {
            matchRank(normalizePlaceQuery(it.value.name), normalizedQuery)
        }.thenBy { it.index })
        .map { it.value }
}

private fun matchRank(name: String, query: String): Int = when {
    name == query -> 0
    name.startsWith(query) || query.startsWith(name) -> 1
    name.contains(query) || query.contains(name) -> 2
    else -> 3
}

fun normalizePlaceQuery(query: String): String = Normalizer
    .normalize(query, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .trim()
    .replace(Regex("\\s+"), " ")
    .lowercase(Locale.ROOT)
