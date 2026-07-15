package com.mototracker.data.network

import com.mototracker.core.format.RouteWeather
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Local fake transport that supports both success and failure responses
// ─────────────────────────────────────────────────────────────────────────────

private class ConfigurableFakeHttpTransport : HttpTransport {
    var nextResult: Result<HttpResponse> =
        Result.success(HttpResponse(200, emptyMap(), ""))
    var lastRequest: HttpRequest? = null

    fun setSuccess(body: String, code: Int = 200) {
        nextResult = Result.success(HttpResponse(code, emptyMap(), body))
    }

    fun setFailure(e: Throwable = RuntimeException("transport error")) {
        nextResult = Result.failure(e)
    }

    override suspend fun execute(request: HttpRequest): Result<HttpResponse> {
        lastRequest = request
        return nextResult
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

class OpenMeteoWeatherClientTest {

    private val transport = ConfigurableFakeHttpTransport()
    private val client = OpenMeteoWeatherClient(transport)

    // ── URL / request parameters ──────────────────────────────────────────────

    @Test
    fun `fetch builds URL with correct latitude and longitude`() = runTest {
        transport.setSuccess(sampleJson())

        client.fetch(lat = 52.2297, lon = 21.0122)

        val url = transport.lastRequest?.url ?: ""
        assertTrue("URL should contain latitude=52.2297", url.contains("latitude=52.2297"))
        assertTrue("URL should contain longitude=21.0122", url.contains("longitude=21.0122"))
    }

    @Test
    fun `fetch URL includes all required current parameter fields`() = runTest {
        transport.setSuccess(sampleJson())

        client.fetch(50.0, 20.0)

        val url = transport.lastRequest?.url ?: ""
        assertTrue("URL should include temperature_2m", url.contains("temperature_2m"))
        assertTrue("URL should include relative_humidity_2m", url.contains("relative_humidity_2m"))
        assertTrue("URL should include precipitation", url.contains("precipitation"))
        assertTrue("URL should include timezone=auto", url.contains("timezone=auto"))
    }

    @Test
    fun `fetch uses GET method`() = runTest {
        transport.setSuccess(sampleJson())

        client.fetch(50.0, 20.0)

        assertEquals("GET", transport.lastRequest?.method)
    }

    // ── JSON parsing — happy path ─────────────────────────────────────────────

    @Test
    fun `fetch parses temperature humidity and rain correctly from sample JSON`() = runTest {
        transport.setSuccess(sampleJson(temp = 22.7, humidity = 60, precipitation = 0.0))

        val result = client.fetch(50.0, 20.0)

        assertTrue(result.isSuccess)
        val snapshot = result.getOrThrow()
        assertEquals("temperature 22.7 should round to 23", 23, snapshot.tempC)
        assertEquals(60, snapshot.humidity)
        assertFalse("rain should be false when precipitation=0", snapshot.rain)
    }

    @Test
    fun `precipitation zero maps to rain=false`() = runTest {
        transport.setSuccess(sampleJson(precipitation = 0.0))

        val snapshot = client.fetch(50.0, 20.0).getOrThrow()

        assertFalse("rain should be false when precipitation is 0", snapshot.rain)
    }

    @Test
    fun `precipitation greater than zero maps to rain=true`() = runTest {
        transport.setSuccess(sampleJson(precipitation = 1.5))

        val snapshot = client.fetch(50.0, 20.0).getOrThrow()

        assertTrue("rain should be true when precipitation > 0", snapshot.rain)
    }

    @Test
    fun `temperature rounds down when fractional part is below 0_5`() = runTest {
        transport.setSuccess(sampleJson(temp = 22.4))

        assertEquals(22, client.fetch(50.0, 20.0).getOrThrow().tempC)
    }

    @Test
    fun `temperature rounds up when fractional part is 0_5 or above`() = runTest {
        transport.setSuccess(sampleJson(temp = 22.5))

        assertEquals(23, client.fetch(50.0, 20.0).getOrThrow().tempC)
    }

    // ── Tolerant parsing — missing / null fields ──────────────────────────────

    @Test
    fun `missing current block produces null temp null humidity and rain=false`() = runTest {
        transport.setSuccess("""{"latitude":50.0,"longitude":20.0}""")

        val snapshot = client.fetch(50.0, 20.0).getOrThrow()

        assertNull("tempC should be null when current block is missing", snapshot.tempC)
        assertNull("humidity should be null when current block is missing", snapshot.humidity)
        assertFalse("rain should be false when current block is missing", snapshot.rain)
    }

    @Test
    fun `JSON-null temperature_2m field produces null tempC but humidity and rain still parse`() = runTest {
        val json = """{"current":{"temperature_2m":null,"relative_humidity_2m":60,"precipitation":0.0}}"""
        transport.setSuccess(json)

        val snapshot = client.fetch(50.0, 20.0).getOrThrow()

        assertNull("tempC should be null when temperature_2m is JSON null", snapshot.tempC)
        assertEquals(60, snapshot.humidity)
        assertFalse(snapshot.rain)
    }

    @Test
    fun `JSON-null relative_humidity_2m produces null humidity but does not fail`() = runTest {
        val json = """{"current":{"temperature_2m":20.0,"relative_humidity_2m":null,"precipitation":0.0}}"""
        transport.setSuccess(json)

        val snapshot = client.fetch(50.0, 20.0).getOrThrow()

        assertEquals(20, snapshot.tempC)
        assertNull("humidity should be null when relative_humidity_2m is JSON null", snapshot.humidity)
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    fun `non-2xx HTTP response returns Result failure`() = runTest {
        transport.setSuccess("error body", code = 500)

        val result = client.fetch(50.0, 20.0)

        assertTrue("non-2xx response should return Result.failure", result.isFailure)
    }

    @Test
    fun `transport failure propagates as Result failure`() = runTest {
        transport.setFailure(RuntimeException("connection refused"))

        val result = client.fetch(50.0, 20.0)

        assertTrue("transport failure should return Result.failure", result.isFailure)
    }

    // ── WeatherSnapshot.toWxJson round-trip ───────────────────────────────────

    @Test
    fun `toWxJson round-trips through RouteWeather parse to non-offline WeatherUi`() {
        val snapshot = WeatherSnapshot(tempC = 18, humidity = 75, rain = false)

        val ui = RouteWeather.parse(snapshot.toWxJson())

        assertFalse("WeatherUi should not be offline for a fully-populated snapshot", ui.offline)
        assertEquals("18°C", ui.tempDisplay)
        assertEquals("75%", ui.humDisplay)
        assertEquals(false, ui.rain)
    }

    @Test
    fun `toWxJson with rain=true round-trips to WeatherUi rain=true`() {
        val snapshot = WeatherSnapshot(tempC = 10, humidity = 90, rain = true)

        val ui = RouteWeather.parse(snapshot.toWxJson())

        assertFalse(ui.offline)
        assertEquals(true, ui.rain)
    }

    @Test
    fun `toWxJson with null tempC produces offline WeatherUi`() {
        val snapshot = WeatherSnapshot(tempC = null, humidity = 60, rain = true)

        val ui = RouteWeather.parse(snapshot.toWxJson())

        assertTrue("WeatherUi should be offline when tempC is null", ui.offline)
    }

    @Test
    fun `toWxJson with null humidity produces dash humDisplay but non-offline WeatherUi`() {
        val snapshot = WeatherSnapshot(tempC = 22, humidity = null, rain = false)

        val ui = RouteWeather.parse(snapshot.toWxJson())

        assertFalse(ui.offline)
        assertEquals("22°C", ui.tempDisplay)
        assertEquals("—", ui.humDisplay)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun sampleJson(
        temp: Double = 20.0,
        humidity: Int = 55,
        precipitation: Double = 0.0,
    ) = """
        {
            "current": {
                "temperature_2m": $temp,
                "relative_humidity_2m": $humidity,
                "precipitation": $precipitation
            }
        }
    """.trimIndent()
}
