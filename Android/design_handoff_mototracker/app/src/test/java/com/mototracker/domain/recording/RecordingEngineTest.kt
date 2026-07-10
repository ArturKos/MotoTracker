package com.mototracker.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class RecordingEngineTest {

    private lateinit var engine: RecordingEngine

    @Before
    fun setUp() {
        engine = RecordingEngine(fuelLper100km = 5.0)
    }

    // ── Haversine / distance ─────────────────────────────────────────────────

    @Test
    fun `identical consecutive fixes contribute zero distance`() {
        engine.onLocation(sample(lat = 52.2297, lng = 21.0122))
        val metrics = engine.onLocation(sample(lat = 52.2297, lng = 21.0122))
        assertEquals(0.0, metrics.distanceKm, 0.0001)
    }

    @Test
    fun `haversine 1 degree latitude is approximately 111 km`() {
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        val metrics = engine.onLocation(sample(lat = 1.0, lng = 0.0))
        assertEquals(111.195, metrics.distanceKm, 0.5)
    }

    @Test
    fun `distance accumulates across multiple fixes`() {
        engine.onLocation(sample(lat = 52.0, lng = 21.0))
        engine.onLocation(sample(lat = 52.1, lng = 21.0))
        val metrics = engine.onLocation(sample(lat = 52.2, lng = 21.0))
        // Two segments of ~11.1 km each
        assertEquals(22.2, metrics.distanceKm, 1.0)
    }

    // ── Speed ────────────────────────────────────────────────────────────────

    @Test
    fun `currentSpeed is converted from mps to kmh`() {
        val metrics = engine.onLocation(sample(speedMps = 10.0)) // 36 km/h
        assertEquals(36.0, metrics.currentSpeedKmh, 0.001)
    }

    @Test
    fun `maxSpeed is updated on faster sample`() {
        engine.onLocation(sample(speedMps = 5.0))  // 18 km/h
        val metrics = engine.onLocation(sample(speedMps = 30.0)) // 108 km/h
        assertEquals(108.0, metrics.maxSpeedKmh, 0.001)
    }

    @Test
    fun `maxSpeed is not reduced by a slower sample`() {
        engine.onLocation(sample(speedMps = 30.0))
        val metrics = engine.onLocation(sample(speedMps = 5.0))
        assertEquals(108.0, metrics.maxSpeedKmh, 0.001)
    }

    @Test
    fun `avgSpeed is zero when no time has elapsed`() {
        engine.onLocation(sample(speedMps = 10.0))
        assertEquals(0.0, engine.snapshot().avgSpeedKmh, 0.001)
    }

    @Test
    fun `avgSpeed equals distance over time`() {
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = 1.0, lng = 0.0)) // ~111 km
        engine.tick(3600L) // 1 hour
        val metrics = engine.snapshot()
        // avg = 111 km / 1 h ≈ 111 km/h
        assertEquals(metrics.distanceKm, metrics.avgSpeedKmh, 0.001)
    }

    // ── Elevation gain ───────────────────────────────────────────────────────

    @Test
    fun `elevation gain accumulates only for altitude increases`() {
        engine.onLocation(sample(altitudeM = 100.0))
        engine.onLocation(sample(altitudeM = 200.0)) // +100 m gain
        engine.onLocation(sample(altitudeM = 150.0)) // descend, no gain
        engine.onLocation(sample(altitudeM = 250.0)) // +100 m gain
        val metrics = engine.snapshot()
        assertEquals(200.0, metrics.elevGainM, 0.001)
    }

    @Test
    fun `altitude is updated to latest fix`() {
        engine.onLocation(sample(altitudeM = 500.0))
        val metrics = engine.onLocation(sample(altitudeM = 750.0))
        assertEquals(750.0, metrics.altitudeM, 0.001)
    }

    // ── Fuel estimation ──────────────────────────────────────────────────────

    @Test
    fun `fuel is calculated as distance times consumption rate`() {
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = 1.0, lng = 0.0)) // ~111 km
        val metrics = engine.snapshot()
        val expectedFuel = metrics.distanceKm * 5.0 / 100.0
        assertEquals(expectedFuel, metrics.fuelL, 0.001)
    }

    @Test
    fun `custom fuel rate is respected`() {
        val customEngine = RecordingEngine(fuelLper100km = 8.0)
        customEngine.onLocation(sample(lat = 0.0, lng = 0.0))
        customEngine.onLocation(sample(lat = 1.0, lng = 0.0))
        val metrics = customEngine.snapshot()
        val expectedFuel = metrics.distanceKm * 8.0 / 100.0
        assertEquals(expectedFuel, metrics.fuelL, 0.001)
    }

    // ── Lean tracking ────────────────────────────────────────────────────────

    @Test
    fun `maxLean tracks absolute maximum across both directions`() {
        engine.onLean(10.0)
        engine.onLean(-45.0)
        engine.onLean(30.0)
        assertEquals(45.0, engine.snapshot().maxLeanDeg, 0.001)
    }

    @Test
    fun `currentLean reflects most recent onLean call`() {
        engine.onLean(20.0)
        engine.onLean(-15.0)
        assertEquals(-15.0, engine.snapshot().currentLeanDeg, 0.001)
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Test
    fun `tick increments durationSec`() {
        engine.tick(5L)
        engine.tick(3L)
        assertEquals(8L, engine.snapshot().durationSec)
    }

    // ── Heading ──────────────────────────────────────────────────────────────

    @Test
    fun `heading is updated to latest fix bearing`() {
        engine.onLocation(sample(bearingDeg = 270f))
        val metrics = engine.onLocation(sample(bearingDeg = 90f))
        assertEquals(90f, metrics.headingDeg, 0.001f)
    }

    // ── Payload JSON ────────────────────────────────────────────────────────

    @Test
    fun `buildRoutePayload produces non-empty path json after location fixes`() {
        engine.onLocation(sample(lat = 52.0, lng = 21.0))
        engine.onLocation(sample(lat = 52.1, lng = 21.1))
        val result = engine.buildRoutePayload()
        assertTrue("pathJson should not be empty array", result.pathJson != "[]")
        assertTrue(result.pathJson.contains("\"lat\""))
        assertTrue(result.pathJson.contains("\"lng\""))
    }

    @Test
    fun `buildRoutePayload produces non-empty speed json after fixes`() {
        engine.onLocation(sample(speedMps = 10.0))
        val result = engine.buildRoutePayload()
        assertTrue(result.speedJson != "[]")
        assertTrue(result.speedJson.contains("\"t\""))
        assertTrue(result.speedJson.contains("\"v\""))
    }

    @Test
    fun `buildRoutePayload produces non-empty elev profile json`() {
        engine.onLocation(sample(altitudeM = 100.0))
        engine.onLocation(sample(lat = 0.1, lng = 0.0, altitudeM = 200.0))
        val result = engine.buildRoutePayload()
        assertTrue(result.elevProfileJson != "[]")
        assertTrue(result.elevProfileJson.contains("\"d\""))
        assertTrue(result.elevProfileJson.contains("\"a\""))
    }

    @Test
    fun `empty session produces empty json arrays`() {
        val result = engine.buildRoutePayload()
        assertEquals("[]", result.pathJson)
        assertEquals("[]", result.speedJson)
        assertEquals("[]", result.elevProfileJson)
    }

    // ── Reset ────────────────────────────────────────────────────────────────

    @Test
    fun `reset clears all accumulated state`() {
        engine.onLocation(sample(lat = 52.0, lng = 21.0, speedMps = 20.0, altitudeM = 500.0))
        engine.onLean(45.0)
        engine.tick(60L)
        engine.reset()

        val m = engine.snapshot()
        assertEquals(0.0, m.distanceKm, 0.0)
        assertEquals(0L, m.durationSec)
        assertEquals(0.0, m.currentSpeedKmh, 0.0)
        assertEquals(0.0, m.maxSpeedKmh, 0.0)
        assertEquals(0.0, m.maxLeanDeg, 0.0)
        assertEquals(0.0, m.elevGainM, 0.0)
        assertEquals(0.0, m.fuelL, 0.0)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun sample(
        lat: Double = 0.0,
        lng: Double = 0.0,
        speedMps: Double = 0.0,
        altitudeM: Double = 0.0,
        bearingDeg: Float = 0f,
    ) = LocationSample(
        lat = lat,
        lng = lng,
        speedMps = speedMps,
        altitudeM = altitudeM,
        bearingDeg = bearingDeg,
        timeMs = 0L,
    )
}
