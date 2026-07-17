package com.mototracker.domain.recording

import com.mototracker.core.format.RouteThumbnail
import com.mototracker.core.format.TraceChunkCodec
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Route
import com.mototracker.data.recording.ActiveSessionSnapshot
import com.mototracker.data.recording.decode
import com.mototracker.data.recording.encode
import com.mototracker.ui.map.TrackGeometry
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for N1: per-point elevation and timestamp enrichment of the recorded GPS track.
 *
 * Covers:
 * (a) RecordingEngine.buildPathJson round-trip preserves lat/lng/ele/t.
 * (b) TraceChunkCodec split/join is lossless with enriched-point JSON.
 * (c) Legacy lat/lng-only JSON still decodes with ele=0.0, t=null via TrackGeometry,
 *     RouteResumeSeed, and RouteThumbnail.
 * (e) B20 snapshot encode/decode round-trip with enriched points;
 *     legacy-snapshot backward-compat.
 */
class RecordingEngineTrackPointTest {

    // ── (a) buildPathJson → TrackGeometry.parsePathJsonFull round-trip ─────────

    @Test
    fun `buildPathJson emits ele and t fields for enriched points`() {
        val engine = RecordingEngine()
        engine.onLocation(
            LocationSample(lat = 52.0, lng = 21.0, speedMps = 10.0, altitudeM = 150.5, bearingDeg = 0f, timeMs = 1_700_000_001_000L),
        )
        val pathJson = engine.buildRoutePayload().pathJson
        assertTrue("should contain ele", pathJson.contains("\"ele\""))
        assertTrue("should contain t", pathJson.contains("\"t\""))
    }

    @Test
    fun `buildPathJson round-trip preserves lat lng ele t`() {
        val engine = RecordingEngine()
        engine.onLocation(
            LocationSample(lat = 50.06, lng = 19.94, speedMps = 15.0, altitudeM = 200.0, bearingDeg = 90f, timeMs = 1_700_000_001_000L),
        )
        // 50 s later → ~1.3 km in 50 s ≈ 94 km/h, well below the N2 outlier cap (300 km/h).
        engine.onLocation(
            LocationSample(lat = 50.07, lng = 19.95, speedMps = 20.0, altitudeM = 210.0, bearingDeg = 95f, timeMs = 1_700_000_051_000L),
        )
        val pathJson = engine.buildRoutePayload().pathJson
        val points = TrackGeometry.parsePathJsonFull(pathJson)

        assertEquals(2, points.size)
        assertEquals(50.06, points[0].lat, 0.0001)
        assertEquals(19.94, points[0].lng, 0.0001)
        assertEquals(200.0, points[0].ele, 0.001)
        assertEquals(1_700_000_001_000L, points[0].t)
        assertEquals(50.07, points[1].lat, 0.0001)
        assertEquals(19.95, points[1].lng, 0.0001)
        assertEquals(210.0, points[1].ele, 0.001)
        assertEquals(1_700_000_051_000L, points[1].t)
    }

    @Test
    fun `buildPathJson omits t field when timeMs produces null-like point`() {
        // Verify t is present when timeMs is a real timestamp
        val engine = RecordingEngine()
        engine.onLocation(
            LocationSample(lat = 52.0, lng = 21.0, speedMps = 10.0, altitudeM = 100.0, bearingDeg = 0f, timeMs = 1_700_000_000_000L),
        )
        val pathJson = engine.buildRoutePayload().pathJson
        val points = TrackGeometry.parsePathJsonFull(pathJson)
        assertEquals(1_700_000_000_000L, points[0].t)
    }

    // ── (b) TraceChunkCodec lossless with enriched-point JSON ──────────────────

    @Test
    fun `TraceChunkCodec round-trip preserves enriched point values`() {
        val enrichedJson = """[
            {"lat":50.0,"lng":19.0,"ele":200.0,"t":1700000001000},
            {"lat":50.1,"lng":19.1,"ele":210.0,"t":1700000002000}
        ]""".trimIndent()
        val joined = TraceChunkCodec.join(TraceChunkCodec.split(enrichedJson))!!
        val arr = JSONArray(joined)

        assertEquals(2, arr.length())
        assertEquals(200.0, arr.getJSONObject(0).getDouble("ele"), 0.001)
        assertEquals(1_700_000_001_000L, arr.getJSONObject(0).getLong("t"))
        assertEquals(210.0, arr.getJSONObject(1).getDouble("ele"), 0.001)
        assertEquals(1_700_000_002_000L, arr.getJSONObject(1).getLong("t"))
    }

    @Test
    fun `TraceChunkCodec multi-chunk enriched array is lossless`() {
        val arr = JSONArray()
        repeat(TraceChunkCodec.CHUNK_SIZE + 5) { i ->
            arr.put(
                org.json.JSONObject()
                    .put("lat", i.toDouble())
                    .put("lng", i.toDouble())
                    .put("ele", (i * 10).toDouble())
                    .put("t", 1_700_000_000_000L + i),
            )
        }
        val json = arr.toString()
        val joined = TraceChunkCodec.join(TraceChunkCodec.split(json))!!
        val result = JSONArray(joined)

        assertEquals(TraceChunkCodec.CHUNK_SIZE + 5, result.length())
        val last = result.getJSONObject(TraceChunkCodec.CHUNK_SIZE + 4)
        assertEquals((TraceChunkCodec.CHUNK_SIZE + 4).toDouble(), last.getDouble("ele") / 10.0, 0.001)
    }

    // ── (c) Legacy lat/lng-only JSON backward compat ───────────────────────────

    @Test
    fun `TrackGeometry parsePathJsonFull defaults ele to 0 and t to null for legacy JSON`() {
        val legacy = """[{"lat":52.0,"lng":21.0},{"lat":52.1,"lng":21.1}]"""
        val points = TrackGeometry.parsePathJsonFull(legacy)
        assertEquals(2, points.size)
        assertEquals(0.0, points[0].ele, 0.0)
        assertNull(points[0].t)
        assertEquals(0.0, points[1].ele, 0.0)
        assertNull(points[1].t)
    }

    @Test
    fun `RouteResumeSeed fromRoute with legacy pathJson produces TrackPoints with ele 0 and null t`() {
        val route = makeRoute(
            pathJson = """[{"lat":52.0,"lng":21.0},{"lat":52.1,"lng":21.1}]""",
        )
        val state = RouteResumeSeed.fromRoute(route)
        assertEquals(2, state.pathPoints.size)
        assertEquals(0.0, state.pathPoints[0].ele, 0.0)
        assertNull(state.pathPoints[0].t)
    }

    @Test
    fun `RouteThumbnail buildPathD parses enriched pathJson ignoring ele and t`() {
        val enriched = """[
            {"lat":50.0,"lng":19.0,"ele":200.0,"t":1700000001000},
            {"lat":50.1,"lng":19.1,"ele":210.0,"t":1700000002000},
            {"lat":50.2,"lng":19.2,"ele":220.0,"t":1700000003000}
        ]""".trimIndent()
        val dEnriched = RouteThumbnail.buildPathD(enriched)

        val legacy = """[{"lat":50.0,"lng":19.0},{"lat":50.1,"lng":19.1},{"lat":50.2,"lng":19.2}]"""
        val dLegacy = RouteThumbnail.buildPathD(legacy)

        assertTrue("enriched path should not be empty", dEnriched.isNotEmpty())
        assertEquals("enriched and legacy should produce identical SVG path", dLegacy, dEnriched)
    }

    // ── (e) B20 snapshot encode/decode with enriched points ────────────────────

    @Test
    fun `snapshot encode decode round-trip preserves enriched TrackPoints`() {
        val pts = listOf(
            TrackPoint(lat = 52.0, lng = 21.0, ele = 150.0, t = 1_700_000_001_000L),
            TrackPoint(lat = 52.1, lng = 21.1, ele = 160.0, t = 1_700_000_002_000L),
        )
        val engineState = RecordingEngineState(
            prevLat = 52.1, prevLng = 21.1, prevAlt = 160.0,
            distanceKm = 11.1, durationSec = 300L, movingSec = 280L,
            currentSpeedKmh = 80.0, maxSpeedKmh = 100.0,
            currentLeanDeg = 0.0, maxLeanDeg = 20.0,
            altitudeM = 160.0, elevGainM = 10.0, headingDeg = 45f,
            pathPoints = pts, speedOverTime = emptyList(), elevOverDist = emptyList(),
        )
        val snapshot = ActiveSessionSnapshot(engineState, 1_700_000_000_000L, "bike-1", false)

        val decoded = decode(encode(snapshot))

        assertNotNull(decoded)
        val p = decoded!!.engineState.pathPoints
        assertEquals(2, p.size)
        assertEquals(52.0, p[0].lat, 0.0001)
        assertEquals(21.0, p[0].lng, 0.0001)
        assertEquals(150.0, p[0].ele, 0.001)
        assertEquals(1_700_000_001_000L, p[0].t)
        assertEquals(52.1, p[1].lat, 0.0001)
        assertEquals(160.0, p[1].ele, 0.001)
        assertEquals(1_700_000_002_000L, p[1].t)
    }

    @Test
    fun `decode legacy snapshot with lat lng only path points defaults ele 0 and t null`() {
        // Simulate a legacy JSON encoded before N1 (no ele/t in path points)
        val legacyJson = """
            {
              "startMs": 1700000000000,
              "bikeId": null,
              "paused": false,
              "distKm": 5.0,
              "durSec": 600,
              "movingSec": 550,
              "spdKmh": 0.0,
              "maxSpdKmh": 80.0,
              "leanDeg": 0.0,
              "maxLeanDeg": 25.0,
              "altM": 100.0,
              "elevGainM": 50.0,
              "hdgDeg": 0.0,
              "path": [{"lat": 52.0, "lng": 21.0}, {"lat": 52.1, "lng": 21.1}],
              "spd": [],
              "elev": [],
              "fuelRate": 5.0,
              "tankCap": null,
              "fillAnchorKm": 0.0,
              "refuels": []
            }
        """.trimIndent()

        val decoded = decode(legacyJson)

        assertNotNull(decoded)
        val pts = decoded!!.engineState.pathPoints
        assertEquals(2, pts.size)
        assertEquals(52.0, pts[0].lat, 0.0001)
        assertEquals(0.0, pts[0].ele, 0.0)
        assertNull(pts[0].t)
        assertEquals(52.1, pts[1].lat, 0.0001)
        assertEquals(0.0, pts[1].ele, 0.0)
        assertNull(pts[1].t)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeRoute(
        pathJson: String? = null,
        km: Double = 10.0,
        durSec: Long = 600L,
    ) = Route(
        id = "route-n1",
        name = "N1 Test",
        dateEpochMs = 1_700_000_000_000L,
        bikeId = null,
        km = km,
        durSec = durSec,
        avg = km / (durSec / 3600.0),
        max = 100.0,
        lean = 20.0,
        elev = 50.0,
        fuel = 0.5,
        synced = false,
        wxJson = null,
        pathJson = pathJson,
        speedJson = null,
        elevProfileJson = null,
        notes = null,
        correctionStatus = CorrectionStatus.NONE,
        maxLeanLeftDeg = 15.0,
        maxLeanRightDeg = 20.0,
    )
}
