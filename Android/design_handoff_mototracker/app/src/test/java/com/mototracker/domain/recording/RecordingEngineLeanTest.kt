package com.mototracker.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for separate max-left / max-right lean tracking in [RecordingEngine] (E7).
 *
 * All tests run on the JVM with no Android dependencies.
 */
class RecordingEngineLeanTest {

    private lateinit var engine: RecordingEngine

    @Before
    fun setUp() {
        engine = RecordingEngine(fuelLper100km = 5.0)
    }

    // ── Max-left accumulation ─────────────────────────────────────────────────

    @Test
    fun `maxLeanLeftDeg accumulates correctly for left-only samples`() {
        engine.onLean(-20.0)
        engine.onLean(-35.0)
        engine.onLean(-10.0)
        val snap = engine.snapshot()
        assertEquals(35.0, snap.maxLeanLeftDeg, 0.001)
    }

    @Test
    fun `maxLeanRightDeg accumulates correctly for right-only samples`() {
        engine.onLean(15.0)
        engine.onLean(42.0)
        engine.onLean(8.0)
        val snap = engine.snapshot()
        assertEquals(42.0, snap.maxLeanRightDeg, 0.001)
    }

    @Test
    fun `mixed lean samples track left and right independently`() {
        engine.onLean(-30.0) // left
        engine.onLean(25.0)  // right
        engine.onLean(-10.0) // left (less than current max-left)
        engine.onLean(40.0)  // right (new max-right)
        val snap = engine.snapshot()
        assertEquals(30.0, snap.maxLeanLeftDeg, 0.001)
        assertEquals(40.0, snap.maxLeanRightDeg, 0.001)
    }

    @Test
    fun `all-zero lean leaves both maxima at zero`() {
        engine.onLean(0.0)
        engine.onLean(0.0)
        val snap = engine.snapshot()
        assertEquals(0.0, snap.maxLeanLeftDeg, 0.0)
        assertEquals(0.0, snap.maxLeanRightDeg, 0.0)
    }

    @Test
    fun `abs max equals max of left and right magnitudes`() {
        engine.onLean(-38.0) // left
        engine.onLean(22.0)  // right
        val snap = engine.snapshot()
        val expectedAbsMax = maxOf(snap.maxLeanLeftDeg, snap.maxLeanRightDeg)
        assertEquals(expectedAbsMax, snap.maxLeanDeg, 0.001)
    }

    @Test
    fun `right-only session has abs max equal to maxLeanRightDeg`() {
        engine.onLean(50.0)
        val snap = engine.snapshot()
        assertEquals(snap.maxLeanRightDeg, snap.maxLeanDeg, 0.001)
        assertEquals(0.0, snap.maxLeanLeftDeg, 0.0)
    }

    @Test
    fun `left-only session has abs max equal to maxLeanLeftDeg`() {
        engine.onLean(-50.0)
        val snap = engine.snapshot()
        assertEquals(snap.maxLeanLeftDeg, snap.maxLeanDeg, 0.001)
        assertEquals(0.0, snap.maxLeanRightDeg, 0.0)
    }

    // ── Reset ────────────────────────────────────────────────────────────────

    @Test
    fun `reset zeroes both maxLeanLeftDeg and maxLeanRightDeg`() {
        engine.onLean(-40.0)
        engine.onLean(35.0)
        engine.reset()
        val snap = engine.snapshot()
        assertEquals(0.0, snap.maxLeanLeftDeg, 0.0)
        assertEquals(0.0, snap.maxLeanRightDeg, 0.0)
    }

    // ── exportState / restore round-trip ─────────────────────────────────────

    @Test
    fun `exportState includes maxLeanLeftDeg and maxLeanRightDeg`() {
        engine.onLean(-28.0)
        engine.onLean(33.0)
        val state = engine.exportState()
        assertEquals(28.0, state.maxLeanLeftDeg, 0.001)
        assertEquals(33.0, state.maxLeanRightDeg, 0.001)
    }

    @Test
    fun `restore round-trip preserves maxLeanLeftDeg and maxLeanRightDeg`() {
        engine.onLean(-28.0)
        engine.onLean(33.0)
        val state = engine.exportState()

        val restored = RecordingEngine()
        restored.restore(state)
        val snap = restored.snapshot()
        assertEquals(28.0, snap.maxLeanLeftDeg, 0.001)
        assertEquals(33.0, snap.maxLeanRightDeg, 0.001)
    }

    @Test
    fun `old snapshot without new fields defaults maxLeanLeftDeg and maxLeanRightDeg to zero`() {
        // Simulate an older RecordingEngineState that does not include the new fields
        // (they have defaulted parameter values of 0.0 for backward-compat).
        val oldState = RecordingEngineState(
            prevLat = null, prevLng = null, prevAlt = null,
            distanceKm = 50.0, durationSec = 1800L, movingSec = 1500L,
            currentSpeedKmh = 0.0, maxSpeedKmh = 120.0,
            currentLeanDeg = 0.0, maxLeanDeg = 30.0,
            // maxLeanLeftDeg and maxLeanRightDeg intentionally omitted → defaults to 0.0
            altitudeM = 200.0, elevGainM = 300.0, headingDeg = 180f,
            pathPoints = emptyList(), speedOverTime = emptyList(), elevOverDist = emptyList(),
        )
        val restored = RecordingEngine()
        restored.restore(oldState)
        val snap = restored.snapshot()
        assertEquals(0.0, snap.maxLeanLeftDeg, 0.0)
        assertEquals(0.0, snap.maxLeanRightDeg, 0.0)
    }
}
