package com.mototracker.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `reset with custom consumption yields correct fuel estimate`() {
        val e = RecordingEngine()
        e.reset(fuelLper100km = 8.0)
        // Drive 100 km worth of GPS fixes
        e.onLocation(sample(lat = 0.0, lng = 0.0))
        e.onLocation(sample(lat = 0.9009, lng = 0.0)) // ~100 km
        val m = e.snapshot()
        // Expect approximately 8.0 L for 100 km at 8 L/100km
        assertEquals(8.0, m.fuelL, 1.0)
    }

    // ── Fill-to-full (E4) ────────────────────────────────────────────────────

    @Test
    fun `fillToFull resets distanceSinceFullKm to zero`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 17.0)
        // Drive ~111 km
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = 1.0, lng = 0.0))
        assertTrue("distance should be > 0 before fill", engine.snapshot().distanceSinceFullKm > 0.0)

        engine.fillToFull()

        assertEquals(0.0, engine.snapshot().distanceSinceFullKm, 0.0001)
    }

    @Test
    fun `fuelSinceFull equals consumption times distanceSinceFull divided by 100`() {
        val consumption = 6.0
        engine.reset(fuelLper100km = consumption, tankCapacityL = 20.0)
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = 1.0, lng = 0.0)) // ~111 km
        engine.fillToFull()
        engine.onLocation(sample(lat = 2.0, lng = 0.0)) // another ~111 km after fill

        val snap = engine.snapshot()
        val expectedFuelSinceFull = snap.distanceSinceFullKm * consumption / 100.0
        val expectedRemaining = (20.0 - expectedFuelSinceFull).coerceAtLeast(0.0)
        assertEquals(expectedRemaining, snap.remainingFuelL!!, 0.01)
    }

    @Test
    fun `remainingFuelL decreases as distance accumulates after fill`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 17.0)
        engine.fillToFull()
        val before = engine.snapshot().remainingFuelL!!
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = 1.0, lng = 0.0)) // drive ~111 km
        val after = engine.snapshot().remainingFuelL!!
        assertTrue("remaining fuel should decrease after driving", after < before)
    }

    @Test
    fun `remainingRangeKm math matches consumption formula`() {
        val consumption = 5.0
        val tank = 17.0
        engine.reset(fuelLper100km = consumption, tankCapacityL = tank)
        engine.fillToFull()
        // Drive half the tank's equivalent distance
        val halfRangeKm = (tank / 2.0) / consumption * 100.0
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = halfRangeKm / 111.0, lng = 0.0))

        val snap = engine.snapshot()
        val expectedRange = snap.remainingFuelL!! / consumption * 100.0
        assertEquals(expectedRange, snap.remainingRangeKm!!, 1.0)
    }

    @Test
    fun `lowFuel is false when remaining fuel is above 15 percent`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 17.0)
        engine.fillToFull()
        // Only drive a short distance — well above 15 %
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = 0.01, lng = 0.0))
        assertFalse("lowFuel should be false when far from empty", engine.snapshot().lowFuel)
    }

    @Test
    fun `lowFuel is true when remaining fuel is at or below 15 percent of capacity`() {
        val tank = 17.0
        val consumption = 5.0
        engine.reset(fuelLper100km = consumption, tankCapacityL = tank)
        engine.fillToFull()
        // Drain to just below 15 %: consume 85 % of tank → drive 85 % of range
        val drainKm = tank * 0.86 / consumption * 100.0
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = drainKm / 111.0, lng = 0.0))

        assertTrue("lowFuel should be true when remaining ≤ 15 %", engine.snapshot().lowFuel)
    }

    @Test
    fun `null tank capacity leaves remaining and range null and lowFuel false`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = null)
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = 1.0, lng = 0.0))
        val snap = engine.snapshot()
        assertNull(snap.remainingFuelL)
        assertNull(snap.remainingRangeKm)
        assertFalse(snap.lowFuel)
    }

    @Test
    fun `zero consumption with tank capacity yields null remainingRangeKm`() {
        engine.reset(fuelLper100km = 0.0, tankCapacityL = 17.0)
        engine.fillToFull()
        val snap = engine.snapshot()
        assertNull("remainingRangeKm should be null when consumption is 0 to avoid divide-by-zero", snap.remainingRangeKm)
        // remainingFuelL should still equal tank capacity (no fuel consumed)
        assertEquals(17.0, snap.remainingFuelL!!, 0.001)
    }

    @Test
    fun `exportState and restore round-trip preserves fillAnchorKm and tankCapacityL`() {
        engine.reset(fuelLper100km = 6.0, tankCapacityL = 20.0)
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = 1.0, lng = 0.0)) // ~111 km
        engine.fillToFull()
        // Drive a bit more
        engine.onLocation(sample(lat = 1.1, lng = 0.0))

        val exportedState = engine.exportState()
        assertEquals(6.0, exportedState.sessionFuelLper100km, 0.0)
        assertEquals(20.0, exportedState.tankCapacityL!!, 0.0)
        // fillAnchorKm should be ~111 km (distance when fillToFull was called)
        assertTrue("fillAnchorKm should be > 0 after fill", exportedState.fillAnchorKm > 0.0)

        val restored = RecordingEngine()
        restored.restore(exportedState)
        val restoredSnap = restored.snapshot()
        val originalSnap = engine.snapshot()

        assertEquals(originalSnap.distanceSinceFullKm, restoredSnap.distanceSinceFullKm, 0.001)
        assertEquals(originalSnap.remainingFuelL!!, restoredSnap.remainingFuelL!!, 0.001)
        assertEquals(originalSnap.remainingRangeKm!!, restoredSnap.remainingRangeKm!!, 0.001)
    }

    // ── updateFuelConfig (I2) ────────────────────────────────────────────────

    @Test
    fun `updateFuelConfig changes consumption and tankCapacityL without resetting accumulators`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 10.0)
        // Drive ~111 km, anchor to full, then drive a bit more
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = 1.0, lng = 0.0))
        engine.fillToFull()
        engine.onLocation(sample(lat = 1.1, lng = 0.0))

        val snapBefore = engine.snapshot()
        val distBefore = snapBefore.distanceKm
        val distSinceFull = snapBefore.distanceSinceFullKm
        assertTrue("distanceSinceFullKm should be > 0 after driving past fill", distSinceFull > 0.0)

        engine.updateFuelConfig(fuelLper100km = 6.0, tankCapacityL = 20.0)

        val snapAfter = engine.snapshot()
        // Accumulators untouched
        assertEquals(distBefore, snapAfter.distanceKm, 0.001)
        assertEquals(distSinceFull, snapAfter.distanceSinceFullKm, 0.001)
        // Remaining fuel reflects the new capacity and new consumption rate
        val expectedFuelUsed = distSinceFull * 6.0 / 100.0
        val expectedRemaining = (20.0 - expectedFuelUsed).coerceAtLeast(0.0)
        assertEquals(expectedRemaining, snapAfter.remainingFuelL!!, 0.01)
        assertEquals(20.0, snapAfter.tankCapacityL!!, 0.001)
    }

    @Test
    fun `updateFuelConfig with null tankCapacityL leaves remaining fields null`() {
        engine.reset(fuelLper100km = 5.0, tankCapacityL = 17.0)
        engine.onLocation(sample(lat = 0.0, lng = 0.0))
        engine.onLocation(sample(lat = 1.0, lng = 0.0))

        engine.updateFuelConfig(fuelLper100km = 5.0, tankCapacityL = null)

        val snap = engine.snapshot()
        assertNull(snap.tankCapacityL)
        assertNull(snap.remainingFuelL)
        assertNull(snap.remainingRangeKm)
        assertFalse(snap.lowFuel)
    }

    // ── Moving-time accumulation (E5) ────────────────────────────────────────

    @Test
    fun `movingSec accumulates only while speed is at or above threshold`() {
        // 3 km/h → above threshold
        engine.onLocation(sample(speedMps = 3.0 / 3.6))
        engine.tick(10L)
        assertEquals(10L, engine.snapshot().movingSec)
    }

    @Test
    fun `movingSec stays flat when speed is below threshold`() {
        // 1 km/h → below threshold
        engine.onLocation(sample(speedMps = 1.0 / 3.6))
        engine.tick(10L)
        assertEquals(0L, engine.snapshot().movingSec)
    }

    @Test
    fun `movingSec boundary at exactly 2 kmh counts as moving`() {
        engine.onLocation(sample(speedMps = RecordingEngine.MOVING_THRESHOLD_KMH / 3.6))
        engine.tick(5L)
        assertEquals(5L, engine.snapshot().movingSec)
    }

    @Test
    fun `durationSec advances regardless of speed`() {
        engine.onLocation(sample(speedMps = 0.0))
        engine.tick(7L)
        assertEquals(7L, engine.snapshot().durationSec)
        assertEquals(0L, engine.snapshot().movingSec)
    }

    @Test
    fun `onLocation below threshold appends no points and adds no distance`() {
        engine.onLocation(sample(lat = 52.0, lng = 21.0, speedMps = 0.5 / 3.6)) // 0.5 km/h
        val before = engine.buildRoutePayload()
        // Second stationary fix
        engine.onLocation(sample(lat = 52.1, lng = 21.0, speedMps = 0.3 / 3.6))
        val after = engine.buildRoutePayload()
        assertEquals("[]", before.pathJson)
        assertEquals("[]", after.pathJson)
        assertEquals(0.0, engine.snapshot().distanceKm, 0.0001)
    }

    @Test
    fun `onLocation at threshold appends point and accumulates distance`() {
        engine.onLocation(sample(lat = 52.0, lng = 21.0, speedMps = RecordingEngine.MOVING_THRESHOLD_KMH / 3.6))
        engine.onLocation(sample(lat = 52.1, lng = 21.0, speedMps = RecordingEngine.MOVING_THRESHOLD_KMH / 3.6))
        val result = engine.buildRoutePayload()
        assertTrue("pathJson should contain two points", result.pathJson != "[]")
        assertTrue(engine.snapshot().distanceKm > 0.0)
    }

    @Test
    fun `stationary fixes do not advance prevLat so distance gap is not counted later`() {
        // One moving fix to set a prevLat anchor
        engine.onLocation(sample(lat = 52.0, lng = 21.0, speedMps = 10.0))
        val distAfterFirst = engine.snapshot().distanceKm

        // Several stationary fixes — should not update prevLat
        repeat(5) {
            engine.onLocation(sample(lat = 53.0, lng = 21.0, speedMps = 0.0))
        }
        // A moving fix after a stop — prev* should still be at 52.0, so distance is measured from there
        engine.onLocation(sample(lat = 52.5, lng = 21.0, speedMps = 10.0))
        val distAfterResume = engine.snapshot().distanceKm

        // Distance from 52.0 to 52.5 ≈ 55.6 km, NOT from 53.0 (jitter)
        assertTrue("distance should have increased after moving fix", distAfterResume > distAfterFirst)
        assertEquals(55.6, distAfterResume, 1.0)
    }

    @Test
    fun `reset zeroes movingSec`() {
        engine.onLocation(sample(speedMps = 10.0))
        engine.tick(30L)
        assertTrue("movingSec should be > 0 before reset", engine.snapshot().movingSec > 0)
        engine.reset()
        assertEquals(0L, engine.snapshot().movingSec)
    }

    @Test
    fun `exportState and restore round-trip preserves movingSec`() {
        engine.onLocation(sample(speedMps = 10.0))
        engine.tick(60L)
        val originalMoving = engine.snapshot().movingSec
        assertTrue(originalMoving > 0)

        val state = engine.exportState()
        assertEquals(originalMoving, state.movingSec)

        val restored = RecordingEngine()
        restored.restore(state)
        assertEquals(originalMoving, restored.snapshot().movingSec)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun sample(
        lat: Double = 0.0,
        lng: Double = 0.0,
        speedMps: Double = 10.0,
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
