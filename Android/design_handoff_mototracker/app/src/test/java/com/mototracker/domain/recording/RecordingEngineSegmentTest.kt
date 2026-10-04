package com.mototracker.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingEngineSegmentTest {

    private fun fix(lat: Double, timeMs: Long, altitudeM: Double = 50.0) = LocationSample(
        lat = lat, lng = 0.0, speedMps = 25.0, altitudeM = altitudeM, bearingDeg = 0f, timeMs = timeMs,
    )

    @Test
    fun `breakSegment drops the bridge to the next fix but keeps both points`() {
        val engine = RecordingEngine()
        engine.onLocation(fix(lat = 0.0, timeMs = 1_000L))
        engine.onLocation(fix(lat = 0.00025, timeMs = 2_000L))
        val before = engine.snapshot().distanceKm

        engine.breakSegment()
        // 1.1 km away 3 s later: without the break this is a teleport and would be dropped.
        engine.onLocation(fix(lat = 0.01, timeMs = 5_000L, altitudeM = 500.0))

        assertEquals(before, engine.snapshot().distanceKm, 0.0)
        assertEquals(0.0, engine.snapshot().elevGainM, 0.0)
        assertEquals(3, engine.pathPoints.size)

        engine.onLocation(fix(lat = 0.01025, timeMs = 6_000L, altitudeM = 500.0))
        assertTrue(engine.snapshot().distanceKm > before + 0.02)
    }

    @Test
    fun `exported track lists do not change when recording continues or resets`() {
        val engine = RecordingEngine()
        engine.onLocation(fix(lat = 0.0, timeMs = 1_000L))
        engine.onLocation(fix(lat = 0.00025, timeMs = 2_000L))
        val exported = engine.exportState()
        val shownTrack = engine.pathPoints

        engine.onLocation(fix(lat = 0.0005, timeMs = 3_000L))
        assertEquals(2, exported.pathPoints.size)
        assertEquals(2, exported.speedOverTime.size)
        assertEquals(2, exported.elevOverDist.size)
        assertEquals(2, shownTrack.size)
        assertEquals(3, engine.pathPoints.size)

        engine.reset()
        assertEquals(2, exported.pathPoints.size)
        assertTrue(engine.pathPoints.isEmpty())
    }

    @Test
    fun `restore from the engine's own export rebuilds the same track`() {
        val engine = RecordingEngine()
        engine.onLocation(fix(lat = 0.0, timeMs = 1_000L))
        engine.onLocation(fix(lat = 0.00025, timeMs = 2_000L))
        val exported = engine.exportState()

        engine.restore(exported)

        assertEquals(exported.pathPoints, engine.pathPoints)
        assertEquals(exported, engine.exportState())
    }
}
