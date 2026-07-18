package com.mototracker.domain.recording

import com.mototracker.domain.stats.LeanHistogram
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for lean-angle histogram integration in [RecordingEngine] (Q1).
 *
 * Tests: tick increments correct bucket, reset clears histogram,
 * exportState/restore round-trip preserves histogram, buildRoutePayload encodes JSON.
 */
class RecordingEngineLeanHistogramTest {

    private lateinit var engine: RecordingEngine

    @Before
    fun setUp() {
        engine = RecordingEngine(fuelLper100km = 5.0)
    }

    // ── tick increments correct bucket ────────────────────────────────────────

    @Test
    fun `tick increments bucket 0 when lean is near-zero`() {
        engine.onLean(5.0) // 0–10° → bucket 0
        engine.tick(3L)
        val state = engine.exportState()
        assertEquals(3, state.leanBucketCounts[0])
    }

    @Test
    fun `tick increments bucket 1 when lean is exactly 10 degrees`() {
        engine.onLean(10.0) // boundary: 10° → bucket 1 (not 0)
        engine.tick(2L)
        val state = engine.exportState()
        assertEquals(2, state.leanBucketCounts[1])
        assertEquals(0, state.leanBucketCounts[0])
    }

    @Test
    fun `tick increments bucket 2 for 20-degree lean`() {
        engine.onLean(25.0) // 20–30° → bucket 2
        engine.tick(5L)
        val state = engine.exportState()
        assertEquals(5, state.leanBucketCounts[2])
    }

    @Test
    fun `tick increments bucket 4 for lean over 40 degrees`() {
        engine.onLean(45.0) // 40°+ → bucket 4
        engine.tick(10L)
        val state = engine.exportState()
        assertEquals(10, state.leanBucketCounts[4])
    }

    @Test
    fun `tick uses absolute value of negative lean angle`() {
        engine.onLean(-35.0) // abs = 35° → 30–40° → bucket 3
        engine.tick(4L)
        val state = engine.exportState()
        assertEquals(4, state.leanBucketCounts[3])
    }

    @Test
    fun `multiple ticks accumulate correctly in the same bucket`() {
        engine.onLean(5.0) // bucket 0
        engine.tick(2L)
        engine.tick(3L)
        val state = engine.exportState()
        assertEquals(5, state.leanBucketCounts[0])
    }

    @Test
    fun `ticks after onLean changes switch buckets`() {
        engine.onLean(5.0)   // bucket 0
        engine.tick(2L)
        engine.onLean(25.0)  // bucket 2
        engine.tick(3L)
        val state = engine.exportState()
        assertEquals(2, state.leanBucketCounts[0])
        assertEquals(3, state.leanBucketCounts[2])
    }

    // ── reset clears histogram ────────────────────────────────────────────────

    @Test
    fun `reset clears lean bucket counts to zero`() {
        engine.onLean(45.0)
        engine.tick(100L)
        engine.reset()
        val state = engine.exportState()
        assertArrayEquals(IntArray(5), state.leanBucketCounts.toIntArray())
    }

    // ── exportState / restore round-trip ─────────────────────────────────────

    @Test
    fun `exportState includes lean bucket counts`() {
        engine.onLean(15.0) // bucket 1
        engine.tick(7L)
        val state = engine.exportState()
        assertEquals(7, state.leanBucketCounts[1])
    }

    @Test
    fun `restore round-trip preserves lean bucket counts`() {
        engine.onLean(25.0) // bucket 2
        engine.tick(5L)
        val exported = engine.exportState()

        val engine2 = RecordingEngine()
        engine2.restore(exported)
        val state2 = engine2.exportState()

        assertEquals(5, state2.leanBucketCounts[2])
        for (i in listOf(0, 1, 3, 4)) assertEquals(0, state2.leanBucketCounts[i])
    }

    @Test
    fun `restore with empty leanBucketCounts defaults to zeros`() {
        val state = RecordingEngineState(
            prevLat = null, prevLng = null, prevAlt = null,
            distanceKm = 0.0, durationSec = 0L, currentSpeedKmh = 0.0,
            maxSpeedKmh = 0.0, currentLeanDeg = 0.0, maxLeanDeg = 0.0,
            altitudeM = 0.0, elevGainM = 0.0, headingDeg = 0f,
            pathPoints = emptyList(), speedOverTime = emptyList(), elevOverDist = emptyList(),
            // leanBucketCounts defaults to List(5){0} per backward-compat default
        )
        engine.restore(state)
        val exported = engine.exportState()
        assertArrayEquals(IntArray(5), exported.leanBucketCounts.toIntArray())
    }

    // ── buildRoutePayload encodes leanHistogramJson ───────────────────────────

    @Test
    fun `buildRoutePayload leanHistogramJson is valid JSON`() {
        engine.onLean(5.0)  // bucket 0
        engine.tick(3L)
        engine.onLean(35.0) // bucket 3
        engine.tick(2L)
        val result = engine.buildRoutePayload()
        assertNotNull(result.leanHistogramJson)
        val decoded = LeanHistogram.decode(result.leanHistogramJson)
        assertNotNull(decoded)
        assertEquals(5, decoded!!.size)
        assertEquals(3, decoded[0])
        assertEquals(2, decoded[3])
    }

    @Test
    fun `buildRoutePayload after reset emits all-zero histogram`() {
        engine.onLean(45.0)
        engine.tick(10L)
        engine.reset()
        val result = engine.buildRoutePayload()
        val decoded = LeanHistogram.decode(result.leanHistogramJson)!!
        assertArrayEquals(IntArray(5), decoded)
    }
}
