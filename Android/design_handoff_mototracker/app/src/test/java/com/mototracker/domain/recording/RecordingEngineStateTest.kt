package com.mototracker.domain.recording

import com.mototracker.data.recording.ActiveSessionSnapshot
import com.mototracker.data.recording.decode
import com.mototracker.data.recording.encode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingEngineStateTest {

    // ── exportState / restore round-trip ─────────────────────────────────────

    @Test
    fun `exportState captures all scalar fields`() {
        val engine = RecordingEngine()
        engine.onLocation(
            LocationSample(lat = 52.0, lng = 21.0, speedMps = 10.0, altitudeM = 100.0, bearingDeg = 90f, timeMs = 1000L),
        )
        engine.onLean(25.5)
        engine.tick(30L)

        val state = engine.exportState()

        assertEquals(52.0, state.prevLat!!, 0.0)
        assertEquals(21.0, state.prevLng!!, 0.0)
        assertEquals(100.0, state.prevAlt!!, 0.0)
        assertEquals(30L, state.durationSec)
        assertEquals(25.5, state.currentLeanDeg, 0.0)
        assertEquals(25.5, state.maxLeanDeg, 0.0)
        assertEquals(36.0, state.currentSpeedKmh, 0.001) // 10 m/s → 36 km/h
        assertEquals(36.0, state.maxSpeedKmh, 0.001)
        assertEquals(100.0, state.altitudeM, 0.0)
        assertEquals(90f, state.headingDeg)
    }

    @Test
    fun `exportState captures path, speed and elev lists`() {
        val engine = RecordingEngine()
        engine.onLocation(LocationSample(52.0, 21.0, 10.0, 100.0, 0f, 1000L))
        // 50 s later → ~1.3 km in 49 s ≈ 96 km/h, well below the N2 outlier cap.
        engine.onLocation(LocationSample(52.01, 21.01, 15.0, 105.0, 10f, 50_000L))

        val state = engine.exportState()

        assertEquals(2, state.pathPoints.size)
        assertEquals(2, state.speedOverTime.size)
        assertEquals(2, state.elevOverDist.size)
        assertEquals(52.0, state.pathPoints[0].lat, 0.0)
        assertEquals(52.01, state.pathPoints[1].lat, 0.0)
    }

    @Test
    fun `exportState returns independent copies of lists`() {
        val engine = RecordingEngine()
        engine.onLocation(LocationSample(52.0, 21.0, 10.0, 100.0, 0f, 1000L))
        val state = engine.exportState()

        // Adding another point after export should NOT affect the exported list.
        engine.onLocation(LocationSample(52.01, 21.01, 12.0, 102.0, 5f, 2000L))

        assertEquals(1, state.pathPoints.size)
    }

    @Test
    fun `restore preserves all scalar metrics`() {
        val original = RecordingEngine()
        original.onLocation(LocationSample(53.0, 14.0, 20.0, 200.0, 45f, 1000L))
        original.onLean(30.0)
        original.tick(60L)
        val state = original.exportState()

        val restored = RecordingEngine()
        restored.restore(state)
        val snap = restored.snapshot()

        assertEquals(state.distanceKm, snap.distanceKm, 0.0001)
        assertEquals(state.durationSec, snap.durationSec)
        assertEquals(state.currentSpeedKmh, snap.currentSpeedKmh, 0.001)
        assertEquals(state.maxSpeedKmh, snap.maxSpeedKmh, 0.001)
        assertEquals(state.currentLeanDeg, snap.currentLeanDeg, 0.0)
        assertEquals(state.maxLeanDeg, snap.maxLeanDeg, 0.0)
        assertEquals(state.altitudeM, snap.altitudeM, 0.0)
        assertEquals(state.elevGainM, snap.elevGainM, 0.0)
        assertEquals(state.headingDeg, snap.headingDeg)
    }

    @Test
    fun `restore rebuilds path and chart lists`() {
        val original = RecordingEngine()
        original.onLocation(LocationSample(53.0, 14.0, 20.0, 200.0, 0f, 1000L))
        // 50 s later → ~1.3 km in 49 s ≈ 96 km/h, well below the N2 outlier cap.
        original.onLocation(LocationSample(53.01, 14.01, 22.0, 205.0, 5f, 50_000L))
        val state = original.exportState()

        val restored = RecordingEngine()
        restored.restore(state)
        val restoredState = restored.exportState()

        assertEquals(2, restoredState.pathPoints.size)
        assertEquals(53.0, restoredState.pathPoints[0].lat, 0.0)
        assertEquals(53.01, restoredState.pathPoints[1].lat, 0.0)
        assertEquals(2, restoredState.speedOverTime.size)
        assertEquals(2, restoredState.elevOverDist.size)
    }

    @Test
    fun `restore preserves null prevLat when no location fix yet`() {
        val original = RecordingEngine()
        original.tick(5L)
        val state = original.exportState()

        assertNull(state.prevLat)
        assertNull(state.prevLng)
        assertNull(state.prevAlt)

        val restored = RecordingEngine()
        restored.restore(state)
        val restoredState = restored.exportState()

        assertNull(restoredState.prevLat)
        assertNull(restoredState.prevLng)
        assertNull(restoredState.prevAlt)
    }

    @Test
    fun `restore then tick continues accumulating correctly`() {
        val original = RecordingEngine()
        original.tick(100L)
        val state = original.exportState()

        val restored = RecordingEngine()
        restored.restore(state)
        restored.tick(50L)

        assertEquals(150L, restored.snapshot().durationSec)
    }

    // ── JSON encode / decode round-trip ──────────────────────────────────────

    @Test
    fun `encode and decode round-trip preserves all scalar fields`() {
        val engineState = RecordingEngineState(
            prevLat = 53.1234,
            prevLng = 14.5678,
            prevAlt = 150.0,
            distanceKm = 12.34,
            durationSec = 600L,
            currentSpeedKmh = 80.0,
            maxSpeedKmh = 120.0,
            currentLeanDeg = 15.0,
            maxLeanDeg = 35.0,
            altitudeM = 150.0,
            elevGainM = 250.0,
            headingDeg = 270f,
            pathPoints = listOf(TrackPoint(53.1234, 14.5678), TrackPoint(53.1300, 14.5700)),
            speedOverTime = listOf(0L to 0.0, 60L to 80.0),
            elevOverDist = listOf(0.0 to 100.0, 12.34 to 150.0),
        )
        val original = ActiveSessionSnapshot(
            engineState = engineState,
            recordingStartMs = 1_700_000_000L,
            bikeId = "bike-abc",
            paused = false,
        )

        val json = encode(original)
        val decoded = decode(json)

        assertNotNull(decoded)
        assertEquals(original.recordingStartMs, decoded!!.recordingStartMs)
        assertEquals(original.bikeId, decoded.bikeId)
        assertEquals(original.paused, decoded.paused)

        val e = decoded.engineState
        assertEquals(53.1234, e.prevLat!!, 0.0001)
        assertEquals(14.5678, e.prevLng!!, 0.0001)
        assertEquals(150.0, e.prevAlt!!, 0.0)
        assertEquals(12.34, e.distanceKm, 0.0001)
        assertEquals(600L, e.durationSec)
        assertEquals(80.0, e.currentSpeedKmh, 0.0)
        assertEquals(120.0, e.maxSpeedKmh, 0.0)
        assertEquals(15.0, e.currentLeanDeg, 0.0)
        assertEquals(35.0, e.maxLeanDeg, 0.0)
        assertEquals(150.0, e.altitudeM, 0.0)
        assertEquals(250.0, e.elevGainM, 0.0)
        assertEquals(270f, e.headingDeg)
    }

    @Test
    fun `encode and decode round-trip preserves null prevLat and null bikeId`() {
        val engineState = RecordingEngineState(
            prevLat = null, prevLng = null, prevAlt = null,
            distanceKm = 0.0, durationSec = 0L,
            currentSpeedKmh = 0.0, maxSpeedKmh = 0.0,
            currentLeanDeg = 0.0, maxLeanDeg = 0.0,
            altitudeM = 0.0, elevGainM = 0.0, headingDeg = 0f,
            pathPoints = emptyList(), speedOverTime = emptyList(), elevOverDist = emptyList(),
        )
        val original = ActiveSessionSnapshot(
            engineState = engineState,
            recordingStartMs = 0L,
            bikeId = null,
            paused = true,
        )

        val decoded = decode(encode(original))

        assertNotNull(decoded)
        assertNull(decoded!!.bikeId)
        assertNull(decoded.engineState.prevLat)
        assertNull(decoded.engineState.prevLng)
        assertNull(decoded.engineState.prevAlt)
        assertEquals(true, decoded.paused)
    }

    @Test
    fun `encode and decode round-trip preserves path points`() {
        val pts = listOf(TrackPoint(52.0, 21.0), TrackPoint(52.01, 21.01), TrackPoint(52.02, 21.02))
        val engineState = RecordingEngineState(
            prevLat = null, prevLng = null, prevAlt = null,
            distanceKm = 0.0, durationSec = 0L,
            currentSpeedKmh = 0.0, maxSpeedKmh = 0.0,
            currentLeanDeg = 0.0, maxLeanDeg = 0.0,
            altitudeM = 0.0, elevGainM = 0.0, headingDeg = 0f,
            pathPoints = pts, speedOverTime = emptyList(), elevOverDist = emptyList(),
        )
        val original = ActiveSessionSnapshot(engineState, 0L, null, false)

        val decoded = decode(encode(original))!!

        assertEquals(3, decoded.engineState.pathPoints.size)
        assertEquals(52.0, decoded.engineState.pathPoints[0].lat, 0.0)
        assertEquals(52.02, decoded.engineState.pathPoints[2].lat, 0.0)
    }

    @Test
    fun `decode returns null for malformed JSON`() {
        assertNull(decode("not json"))
        assertNull(decode("{\"broken\":"))
        assertNull(decode(""))
    }

    // ── movingSec round-trip (E5) ────────────────────────────────────────────

    @Test
    fun `exportState captures movingSec`() {
        val engine = RecordingEngine()
        engine.onLocation(LocationSample(52.0, 21.0, 10.0, 100.0, 0f, 1000L)) // 36 km/h → moving
        engine.tick(45L)
        val state = engine.exportState()
        assertEquals(45L, state.movingSec)
    }

    @Test
    fun `restore preserves movingSec`() {
        val original = RecordingEngine()
        original.onLocation(LocationSample(52.0, 21.0, 10.0, 100.0, 0f, 1000L))
        original.tick(30L)
        val state = original.exportState()

        val restored = RecordingEngine()
        restored.restore(state)
        assertEquals(state.movingSec, restored.snapshot().movingSec)
    }

    @Test
    fun `encode and decode round-trip preserves movingSec`() {
        val engineState = RecordingEngineState(
            prevLat = null, prevLng = null, prevAlt = null,
            distanceKm = 0.0, durationSec = 100L, movingSec = 75L,
            currentSpeedKmh = 0.0, maxSpeedKmh = 0.0,
            currentLeanDeg = 0.0, maxLeanDeg = 0.0,
            altitudeM = 0.0, elevGainM = 0.0, headingDeg = 0f,
            pathPoints = emptyList(), speedOverTime = emptyList(), elevOverDist = emptyList(),
        )
        val original = ActiveSessionSnapshot(engineState, 0L, null, false)

        val decoded = decode(encode(original))

        assertNotNull(decoded)
        assertEquals(75L, decoded!!.engineState.movingSec)
    }

    // ── prevTimeMs JSON round-trip (N2) ──────────────────────────────────────

    @Test
    fun `encode and decode round-trip preserves prevTimeMs`() {
        val engineState = RecordingEngineState(
            prevLat = 52.0, prevLng = 21.0, prevAlt = 100.0,
            prevTimeMs = 1_700_000_000_000L,
            distanceKm = 5.0, durationSec = 120L,
            currentSpeedKmh = 80.0, maxSpeedKmh = 100.0,
            currentLeanDeg = 0.0, maxLeanDeg = 25.0,
            altitudeM = 100.0, elevGainM = 20.0, headingDeg = 90f,
            pathPoints = emptyList(), speedOverTime = emptyList(), elevOverDist = emptyList(),
        )
        val original = ActiveSessionSnapshot(engineState, 0L, null, false)

        val decoded = decode(encode(original))

        assertNotNull(decoded)
        assertEquals(1_700_000_000_000L, decoded!!.engineState.prevTimeMs)
    }

    @Test
    fun `decode defaults prevTimeMs to null when key is absent (backward compat)`() {
        // Simulate a legacy JSON string that has no prevTimeMs field (pre-N2 snapshot).
        val legacyJson = encode(
            ActiveSessionSnapshot(
                RecordingEngineState(
                    prevLat = 52.0, prevLng = 21.0, prevAlt = 100.0,
                    prevTimeMs = 9_999_999L,
                    distanceKm = 5.0, durationSec = 120L,
                    currentSpeedKmh = 0.0, maxSpeedKmh = 60.0,
                    currentLeanDeg = 0.0, maxLeanDeg = 20.0,
                    altitudeM = 100.0, elevGainM = 10.0, headingDeg = 0f,
                    pathPoints = emptyList(), speedOverTime = emptyList(), elevOverDist = emptyList(),
                ),
                0L, null, false,
            ),
        ).let { json ->
            // Remove prevTimeMs key to simulate old (pre-N2) format.
            org.json.JSONObject(json).apply { remove("prevTimeMs") }.toString()
        }

        val decoded = decode(legacyJson)
        assertNotNull(decoded)
        assertNull(decoded!!.engineState.prevTimeMs)
    }

    @Test
    fun `decode defaults movingSec to zero when key is absent (backward compat)`() {
        // Simulate a legacy JSON string that has no movingSec field
        val legacyJson = encode(
            ActiveSessionSnapshot(
                RecordingEngineState(
                    prevLat = null, prevLng = null, prevAlt = null,
                    distanceKm = 5.0, durationSec = 120L,
                    currentSpeedKmh = 0.0, maxSpeedKmh = 60.0,
                    currentLeanDeg = 0.0, maxLeanDeg = 20.0,
                    altitudeM = 100.0, elevGainM = 10.0, headingDeg = 0f,
                    pathPoints = emptyList(), speedOverTime = emptyList(), elevOverDist = emptyList(),
                ),
                0L, null, false,
            ),
        ).let { json ->
            // Remove movingSec key to simulate old format
            org.json.JSONObject(json).apply { remove("movingSec") }.toString()
        }

        val decoded = decode(legacyJson)
        assertNotNull(decoded)
        assertEquals(0L, decoded!!.engineState.movingSec)
    }
}
