package com.mototracker.data.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ---------------------------------------------------------------------------
// Queue-based FakeHttpTransport for multi-request tests
// ---------------------------------------------------------------------------

/**
 * Extended test double that enqueues multiple scripted responses and records
 * all requests, enabling chunking tests with more than one HTTP call.
 */
private class QueuingFakeHttpTransport(
    private val responses: MutableList<Result<HttpResponse>> = mutableListOf(),
) : HttpTransport {

    val requests = mutableListOf<HttpRequest>()

    fun enqueue(response: HttpResponse) {
        responses.add(Result.success(response))
    }

    fun enqueueFailure(error: Throwable = RuntimeException("transport error")) {
        responses.add(Result.failure(error))
    }

    override suspend fun execute(request: HttpRequest): Result<HttpResponse> {
        requests.add(request)
        return responses.removeFirstOrNull() ?: Result.success(
            HttpResponse(200, emptyMap(), """{"matchings":[],"tracepoints":[]}"""),
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun matchResponse(
    coordPairs: List<Pair<Double, Double>>,
    confidence: Double = 0.9,
    tracepointCount: Int = -1,
): HttpResponse {
    val coordsJson = coordPairs.joinToString(",") { (lon, lat) -> "[$lon,$lat]" }
    val matching = """{"confidence":$confidence,"geometry":{"coordinates":[$coordsJson]}}"""
    val tpCount = if (tracepointCount >= 0) tracepointCount else coordPairs.size
    val tracepoints = (0 until tpCount).joinToString(",") { """{"location":[0,0]}""" }
    val body = """{"matchings":[$matching],"tracepoints":[$tracepoints]}"""
    return HttpResponse(200, emptyMap(), body)
}

private fun emptyMatchResponse(): HttpResponse =
    HttpResponse(200, emptyMap(), """{"matchings":[],"tracepoints":[]}""")

private fun points(count: Int, baseLat: Double = 50.0, baseLon: Double = 20.0) =
    (0 until count).map { i ->
        TrackPoint(lat = baseLat + i * 0.001, lon = baseLon + i * 0.001)
    }

private fun pointsWithTimestamps(count: Int) =
    (0 until count).map { i ->
        TrackPoint(lat = 50.0 + i * 0.001, lon = 20.0 + i * 0.001, timestampSec = i * 10L)
    }

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class OsrmGpsCorrectionClientTest {

    private val transport = QueuingFakeHttpTransport()
    private val client = OsrmGpsCorrectionClient(transport)
    private val baseUrl = "http://osrm.test:5001"

    // ── request building — coord order ──────────────────────────────────────

    @Test
    fun `URL uses lon,lat order not lat,lon`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0)))

        client.match(baseUrl, listOf(TrackPoint(lat = 50.1234, lon = 20.5678)))

        val url = transport.requests.first().url
        assertTrue("URL should contain lon,lat order '20.5678,50.1234'", url.contains("20.5678,50.1234"))
    }

    @Test
    fun `multiple points are joined with semicolons`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0, 21.0 to 51.0)))

        client.match(
            baseUrl,
            listOf(
                TrackPoint(lat = 50.0, lon = 20.0),
                TrackPoint(lat = 51.0, lon = 21.0),
            ),
        )

        val url = transport.requests.first().url
        assertTrue("URL should contain ';' between coord pairs", url.contains(";"))
        assertTrue(url.contains("20.0,50.0;21.0,51.0"))
    }

    @Test
    fun `required query params are present`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0)))

        client.match(baseUrl, listOf(TrackPoint(lat = 50.0, lon = 20.0)))

        val url = transport.requests.first().url
        assertTrue(url.contains("geometries=geojson"))
        assertTrue(url.contains("overview=full"))
        assertTrue(url.contains("tidy=true"))
        assertTrue(url.contains("gaps=split"))
    }

    @Test
    fun `timestamps param is appended when points have timestamps`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0, 21.0 to 51.0)))

        client.match(baseUrl, pointsWithTimestamps(2))

        val url = transport.requests.first().url
        assertTrue("URL should contain timestamps param", url.contains("timestamps="))
        assertTrue(url.contains("0;10"))
    }

    @Test
    fun `timestamps param is absent when points have no timestamps`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0)))

        client.match(baseUrl, listOf(TrackPoint(lat = 50.0, lon = 20.0)))

        val url = transport.requests.first().url
        assertTrue("URL should not contain timestamps", !url.contains("timestamps="))
    }

    // ── chunking ─────────────────────────────────────────────────────────────

    @Test
    fun `150 points produce two requests (chunk size 100)`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0), tracepointCount = 100))
        transport.enqueue(matchResponse(listOf(21.0 to 51.0), tracepointCount = 50))

        client.match(baseUrl, points(150))

        assertEquals("Expected 2 HTTP requests for 150 points", 2, transport.requests.size)
    }

    @Test
    fun `exactly 100 points produce one request`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0), tracepointCount = 100))

        client.match(baseUrl, points(100))

        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `101 points produce two requests`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0), tracepointCount = 100))
        transport.enqueue(matchResponse(listOf(21.0 to 51.0), tracepointCount = 1))

        client.match(baseUrl, points(101))

        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `first chunk URL contains only 100 coords when input has 150`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0), tracepointCount = 100))
        transport.enqueue(matchResponse(listOf(21.0 to 51.0), tracepointCount = 50))

        client.match(baseUrl, points(150))

        val firstUrl = transport.requests[0].url
        // 100 points means 99 semicolons
        val semicolons = firstUrl.count { it == ';' }
        assertEquals("First chunk should have 99 semicolons for 100 coords", 99, semicolons)
    }

    @Test
    fun `second chunk URL contains remaining 50 coords`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0), tracepointCount = 100))
        transport.enqueue(matchResponse(listOf(21.0 to 51.0), tracepointCount = 50))

        client.match(baseUrl, points(150))

        val secondUrl = transport.requests[1].url
        val semicolons = secondUrl.substringAfter(baseUrl + "/match/v1/driving/")
            .substringBefore("?")
            .count { it == ';' }
        assertEquals("Second chunk should have 49 semicolons for 50 coords", 49, semicolons)
    }

    // ── response parsing ─────────────────────────────────────────────────────

    @Test
    fun `GeoJSON coordinates are converted from lon,lat to TrackPoint(lat,lon)`() = runTest {
        // GeoJSON [lon=20.5, lat=50.3]
        transport.enqueue(matchResponse(listOf(20.5 to 50.3)))

        val result = client.match(baseUrl, listOf(TrackPoint(lat = 50.3, lon = 20.5)))

        assertTrue(result.isSuccess)
        val matched = result.getOrThrow() as CorrectionOutcome.Matched
        val pt = matched.points.first()
        assertEquals(50.3, pt.lat, 0.0001)
        assertEquals(20.5, pt.lon, 0.0001)
    }

    @Test
    fun `confidence is extracted from matchings array`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0), confidence = 0.87))

        val result = client.match(baseUrl, listOf(TrackPoint(50.0, 20.0)))

        val matched = result.getOrThrow() as CorrectionOutcome.Matched
        assertEquals(0.87, matched.confidence, 0.001)
    }

    @Test
    fun `matchedFraction is computed from non-null tracepoints over input count`() = runTest {
        // 4 input points, 3 non-null tracepoints → fraction 0.75
        val body = """
            {
              "matchings":[{"confidence":0.9,"geometry":{"coordinates":[[20.0,50.0]]}}],
              "tracepoints":[{"location":[0,0]},{"location":[0,0]},{"location":[0,0]},null]
            }
        """.trimIndent()
        transport.enqueue(HttpResponse(200, emptyMap(), body))

        val result = client.match(baseUrl, points(4))

        val matched = result.getOrThrow() as CorrectionOutcome.Matched
        assertEquals(0.75, matched.matchedFraction, 0.001)
    }

    @Test
    fun `empty matchings array returns NoMatch`() = runTest {
        transport.enqueue(emptyMatchResponse())

        val result = client.match(baseUrl, points(5))

        assertTrue(result.isSuccess)
        assertEquals(CorrectionOutcome.NoMatch, result.getOrThrow())
    }

    @Test
    fun `non-2xx response returns Result failure`() = runTest {
        transport.enqueue(HttpResponse(500, emptyMap(), "Internal Server Error"))

        val result = client.match(baseUrl, points(3))

        assertTrue(result.isFailure)
    }

    @Test
    fun `transport failure returns Result failure`() = runTest {
        transport.enqueueFailure(RuntimeException("connection refused"))

        val result = client.match(baseUrl, points(3))

        assertTrue(result.isFailure)
    }

    @Test
    fun `empty input list returns NoMatch without making any request`() = runTest {
        val result = client.match(baseUrl, emptyList())

        assertTrue(result.isSuccess)
        assertEquals(CorrectionOutcome.NoMatch, result.getOrThrow())
        assertTrue("No HTTP calls should be made for empty input", transport.requests.isEmpty())
    }

    // ── multi-chunk stitching ─────────────────────────────────────────────────

    @Test
    fun `matched points from two chunks are stitched in order`() = runTest {
        transport.enqueue(matchResponse(listOf(20.0 to 50.0, 20.1 to 50.1), tracepointCount = 100))
        transport.enqueue(matchResponse(listOf(20.2 to 50.2), tracepointCount = 50))

        val result = client.match(baseUrl, points(150))

        val matched = result.getOrThrow() as CorrectionOutcome.Matched
        assertEquals(3, matched.points.size)
        assertEquals(50.0, matched.points[0].lat, 0.001)
        assertEquals(50.2, matched.points[2].lat, 0.001)
    }

    @Test
    fun `matchedFraction aggregates non-null tracepoints across all chunks`() = runTest {
        // chunk1: 100 input, 80 matched; chunk2: 50 input, 40 matched → 120/150 = 0.8
        transport.enqueue(matchResponse(listOf(20.0 to 50.0), tracepointCount = 80))
        transport.enqueue(matchResponse(listOf(21.0 to 51.0), tracepointCount = 40))

        val result = client.match(baseUrl, points(150))

        val matched = result.getOrThrow() as CorrectionOutcome.Matched
        assertEquals(120.0 / 150.0, matched.matchedFraction, 0.001)
    }
}
