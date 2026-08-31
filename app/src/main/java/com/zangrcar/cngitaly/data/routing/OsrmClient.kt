package com.zangrcar.cngitaly.data.routing

import kotlinx.coroutines.CancellationException
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
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NoDrivingRouteException : IOException()

class OsrmClient(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
    .callTimeout(12, TimeUnit.SECONDS).build()) {
    private val rateLimitMutex = Mutex()
    private val cacheMutex = Mutex()
    private var lastRequestStartedNanos: Long? = null
    private val cache = object : LinkedHashMap<String, ParsedRoute>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ParsedRoute>?) = size > 16
    }

    suspend fun route(endpoints: List<RouteEndpoint>): RouteResult {
        require(endpoints.size >= 2)
        val key = endpoints.flatMap { listOf(it.latitude, it.longitude) }
            .joinToString(",") { String.format(Locale.ROOT, "%.5f", it) }
        cacheMutex.withLock { cache[key]?.let { return it.toResult(endpoints) } }
        rateLimitMutex.withLock {
            lastRequestStartedNanos?.let {
                val remaining = 1_000_000_000L - (System.nanoTime() - it)
                if (remaining > 0) delay((remaining + 999_999) / 1_000_000)
            }
            lastRequestStartedNanos = System.nanoTime()
        }
        val url = buildOsrmUrl(endpoints)
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", USER_AGENT).build()
        val parsed = OsrmParser.parse(client.newCall(request).awaitBody())
        cacheMutex.withLock { cache[key] = parsed }
        return parsed.toResult(endpoints)
    }

    private suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!response.isSuccessful) {
                    if (continuation.isActive) continuation.resumeWithException(IOException())
                    return@use
                }
                val body = response.body?.string()
                if (body == null) continuation.resumeWithException(IOException())
                else if (continuation.isActive) continuation.resume(body)
            }
        })
    }

    companion object {
        private const val BASE_URL = "https://router.project-osrm.org/route/v1/driving/"
        private const val USER_AGENT = "CNGItaly/1.0 (Android; com.zangrcar.cngitaly)"
    }
}

data class ParsedRoute(val points: List<GeoPoint>, val distanceMeters: Double, val durationSeconds: Double) {
    fun toResult(endpoints: List<RouteEndpoint>) =
        RouteResult(endpoints, points, distanceMeters, durationSeconds)
}

internal fun buildOsrmUrl(endpoints: List<RouteEndpoint>) =
    ("https://router.project-osrm.org/route/v1/driving/" + endpoints.joinToString(";") {
        "${it.longitude},${it.latitude}"
    }).toHttpUrl().newBuilder()
        .addQueryParameter("alternatives", "false").addQueryParameter("steps", "false")
        .addQueryParameter("overview", "full").addQueryParameter("geometries", "geojson").build()

object OsrmParser {
    fun parse(json: String): ParsedRoute {
        val root = JSONObject(json)
        val code = root.optString("code")
        if (code == "NoRoute") throw NoDrivingRouteException()
        if (code != "Ok") throw IOException()
        val route = root.optJSONArray("routes")?.optJSONObject(0) ?: throw IOException()
        val distance = route.optDouble("distance", Double.NaN)
        val duration = route.optDouble("duration", Double.NaN)
        if (!distance.isFinite() || distance < 0 || !duration.isFinite() || duration < 0) throw IOException()
        val coordinates = route.optJSONObject("geometry")?.optJSONArray("coordinates") ?: throw IOException()
        val points = buildList {
            for (index in 0 until coordinates.length()) {
                val coordinate = coordinates.optJSONArray(index) ?: continue
                val longitude = coordinate.optDouble(0, Double.NaN)
                val latitude = coordinate.optDouble(1, Double.NaN)
                if (latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) add(GeoPoint(latitude, longitude))
            }
        }
        if (points.size < 2) throw IOException()
        return ParsedRoute(points, distance, duration)
    }
}
