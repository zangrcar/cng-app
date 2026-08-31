package com.zangrcar.cngitaly.data.geocoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class PhotonClientTest {
    @Test fun `valid feature parses longitude latitude order`() {
        val result = PhotonParser.parse(photon(feature("Gallipoli", 18.0, 40.05))).single()
        assertEquals("Gallipoli", result.name)
        assertEquals(40.05, result.latitude, 0.0)
        assertEquals(18.0, result.longitude, 0.0)
    }

    @Test fun `malformed feature is skipped while valid remains`() {
        val json = """{"features":[{"type":"Feature","geometry":{"coordinates":["bad",40]},"properties":{"name":"Bad"}},${feature("Bari", 16.87, 41.12)}]}"""
        assertEquals(listOf("Bari"), PhotonParser.parse(json).map { it.name })
    }

    @Test fun `display name omits missing and duplicate components`() {
        val properties = """{"name":"Gallipoli","city":"Gallipoli","county":"Lecce","country":"Italy"}"""
        val json = """{"features":[{"type":"Feature","geometry":{"coordinates":[18.0,40.05]},"properties":$properties}]}"""
        assertEquals("Gallipoli, Lecce, Italy", PhotonParser.parse(json).single().displayName)
    }

    @Test fun `exact ranks before prefix`() {
        assertEquals(listOf("Gallipoli", "Gallipoli Porto"), names(rankPlaceResults(listOf(result("Gallipoli Porto"), result("Gallipoli")), "Gallipoli")))
    }

    @Test fun `prefix ranks before contains`() {
        assertEquals(listOf("Galli Marina", "Marina Galli"), names(rankPlaceResults(listOf(result("Marina Galli"), result("Galli Marina")), "Galli")))
    }

    @Test fun `contains ranks before fuzzy`() {
        assertEquals(listOf("Porto Gallipoli", "Galipoli"), names(rankPlaceResults(listOf(result("Galipoli"), result("Porto Gallipoli")), "Gallipoli")))
    }

    @Test fun `server order is preserved inside a rank`() {
        assertEquals(listOf("Gallipoli Porto", "Gallipoli Centro"), names(rankPlaceResults(listOf(result("Gallipoli Porto"), result("Gallipoli Centro")), "Gall")))
    }

    @Test fun `Gallipoli exact ranks first`() {
        assertEquals("Gallipoli", rankPlaceResults(listOf(result("Galipoli"), result("Gallipoli")), "Gallipoli").first().name)
    }

    @Test fun `typo query keeps Gallipoli as fuzzy result`() {
        assertEquals(listOf("Gallipoli"), rankPlaceResults(listOf(result("Gallipoli")), "galipoli").map { it.name })
    }

    @Test fun `normalization ignores case whitespace and diacritics`() {
        assertEquals(normalizePlaceQuery("  GÀLLIPOLI  "), normalizePlaceQuery("gallipoli"))
        assertEquals("via roma", normalizePlaceQuery(" Via   Roma "))
    }

    @Test fun `empty feature collection returns no results`() {
        assertTrue(PhotonParser.parse("""{"features":[]}""").isEmpty())
    }

    @Test fun `cache scope distinguishes Italy from global`() {
        val query = normalizePlaceQuery("Rome")
        assertEquals("rome|IT", photonCacheKey(query, listOf("IT")))
        assertEquals("rome|", photonCacheKey(query, emptyList()))
    }

    @Test fun `normalized cached query avoids duplicate HTTP request`() = runBlocking {
        var requests = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(photon(feature("Rome", 12.5, 41.9)).toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val client = PhotonClient(http)
        client.search("Rome")
        client.search("  ROME ")
        assertEquals(1, requests)
    }

    @Test fun `standalone URL is Italy only`() {
        assertEquals(listOf("IT"), buildPhotonUrl("Rome", listOf("IT"), "en").queryParameterValues("countrycode"))
    }

    @Test fun `global route URL sends no countrycode`() {
        assertTrue(buildPhotonUrl("Ljubljana", emptyList(), "en").queryParameterValues("countrycode").isEmpty())
    }

    private fun photon(feature: String) = """{"features":[$feature]}"""
    private fun feature(name: String, longitude: Double, latitude: Double) =
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$longitude,$latitude]},"properties":{"name":"$name"}}"""
    private fun result(name: String) = PlaceSearchResult(name, name, 40.0, 18.0)
    private fun names(results: List<PlaceSearchResult>) = results.map { it.name }
}
