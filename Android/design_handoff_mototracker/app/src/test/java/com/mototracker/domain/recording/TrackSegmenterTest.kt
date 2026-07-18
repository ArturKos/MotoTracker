package com.mototracker.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackSegmenterTest {

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `empty list returns empty list`() {
        val result = TrackSegmenter.split(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single point returns one segment containing that point`() {
        val pt = TrackPoint(lat = 52.0, lng = 21.0, ele = 0.0, t = 1000L)
        val result = TrackSegmenter.split(listOf(pt))
        assertEquals(1, result.size)
        assertEquals(1, result[0].size)
        assertEquals(pt, result[0][0])
    }

    // ── No-gap cases ─────────────────────────────────────────────────────────

    @Test
    fun `contiguous points with sub-threshold gaps form one segment`() {
        val gapSec = RecordingEngine.GPS_GAP_SEC
        val points = listOf(
            TrackPoint(52.0, 21.0, t = 0L),
            TrackPoint(52.1, 21.0, t = (gapSec * 1000 - 1).toLong()),  // just below threshold
            TrackPoint(52.2, 21.0, t = (gapSec * 2000 - 2).toLong()),
        )
        val result = TrackSegmenter.split(points)
        assertEquals("should be one segment", 1, result.size)
        assertEquals(3, result[0].size)
    }

    @Test
    fun `all points at same timestamp form one segment`() {
        val points = List(5) { TrackPoint(52.0 + it * 0.1, 21.0, t = 1000L) }
        val result = TrackSegmenter.split(points)
        assertEquals(1, result.size)
        assertEquals(5, result[0].size)
    }

    // ── Single gap ───────────────────────────────────────────────────────────

    @Test
    fun `one gap above threshold splits into two segments`() {
        val gapMs = ((RecordingEngine.GPS_GAP_SEC + 5.0) * 1000).toLong()
        val points = listOf(
            TrackPoint(52.0, 21.0, t = 0L),
            TrackPoint(52.1, 21.0, t = 10_000L),              // 10 s — contiguous
            TrackPoint(52.5, 21.0, t = 10_000L + gapMs),      // gap — new segment
            TrackPoint(52.6, 21.0, t = 10_000L + gapMs + 5_000L), // 5 s — contiguous
        )
        val result = TrackSegmenter.split(points)
        assertEquals("should be two segments", 2, result.size)
        assertEquals(2, result[0].size)
        assertEquals(2, result[1].size)
    }

    @Test
    fun `gap at boundary exactly equal to gapSec does not split (boundary exclusive)`() {
        val exactGapMs = (RecordingEngine.GPS_GAP_SEC * 1000).toLong()
        val points = listOf(
            TrackPoint(52.0, 21.0, t = 0L),
            TrackPoint(52.1, 21.0, t = exactGapMs),
        )
        // dt = GPS_GAP_SEC exactly → not strictly greater → no split
        val result = TrackSegmenter.split(points)
        assertEquals("boundary-equal gap should not split", 1, result.size)
    }

    @Test
    fun `gap just above threshold splits`() {
        val overGapMs = ((RecordingEngine.GPS_GAP_SEC + 0.001) * 1000).toLong()
        val points = listOf(
            TrackPoint(52.0, 21.0, t = 0L),
            TrackPoint(52.1, 21.0, t = overGapMs),
        )
        val result = TrackSegmenter.split(points)
        assertEquals("just-above-threshold gap should split", 2, result.size)
    }

    // ── Multiple gaps ────────────────────────────────────────────────────────

    @Test
    fun `two gaps produce three segments`() {
        val gapMs = ((RecordingEngine.GPS_GAP_SEC + 10.0) * 1000).toLong()
        val points = listOf(
            TrackPoint(52.0, 21.0, t = 0L),
            TrackPoint(52.1, 21.0, t = 5_000L),               // contiguous with point 0
            TrackPoint(53.0, 21.0, t = 5_000L + gapMs),       // gap → segment 2 starts
            TrackPoint(53.1, 21.0, t = 5_000L + gapMs + 5_000L), // contiguous with point 2
            TrackPoint(54.0, 21.0, t = 5_000L + gapMs * 2 + 5_000L), // gap → segment 3 starts
        )
        val result = TrackSegmenter.split(points)
        assertEquals("two gaps should produce three segments", 3, result.size)
        assertEquals(2, result[0].size)
        assertEquals(2, result[1].size)
        assertEquals(1, result[2].size)
    }

    // ── Legacy (null-t) compat ───────────────────────────────────────────────

    @Test
    fun `points with null t never split — single segment returned`() {
        val points = listOf(
            TrackPoint(52.0, 21.0, t = null),
            TrackPoint(53.0, 21.0, t = null),
            TrackPoint(54.0, 21.0, t = null),
        )
        val result = TrackSegmenter.split(points)
        assertEquals("null-t points must never split", 1, result.size)
        assertEquals(3, result[0].size)
    }

    @Test
    fun `mixed null-t and timestamped points do not split when either side is null`() {
        // Null-t followed by a timestamped point — gap cannot be computed → no split.
        val gapMs = ((RecordingEngine.GPS_GAP_SEC + 60.0) * 1000).toLong()
        val points = listOf(
            TrackPoint(52.0, 21.0, t = null),
            TrackPoint(53.0, 21.0, t = gapMs),      // cannot compute gap from null → no split
            TrackPoint(53.1, 21.0, t = gapMs + gapMs), // gap computable but both non-null → splits
        )
        // Point 0→1: null-t on left → no split.
        // Point 1→2: both non-null, gap > threshold → split.
        val result = TrackSegmenter.split(points)
        assertEquals(2, result.size)
        assertEquals(2, result[0].size) // points 0 and 1
        assertEquals(1, result[1].size) // point 2
    }

    @Test
    fun `custom gapSec parameter is respected`() {
        // With gapSec = 5.0, a 6-second gap should split.
        val points = listOf(
            TrackPoint(52.0, 21.0, t = 0L),
            TrackPoint(52.1, 21.0, t = 6_000L),
        )
        val resultDefault = TrackSegmenter.split(points)          // gapSec = 20 s → no split
        val resultCustom = TrackSegmenter.split(points, gapSec = 5.0) // gapSec = 5 s → split
        assertEquals("default gapSec — no split", 1, resultDefault.size)
        assertEquals("custom gapSec — split", 2, resultCustom.size)
    }

    @Test
    fun `segment point identity is preserved (same objects, not copies)`() {
        val pt1 = TrackPoint(52.0, 21.0, t = 0L)
        val pt2 = TrackPoint(52.1, 21.0, t = 5_000L)
        val result = TrackSegmenter.split(listOf(pt1, pt2))
        assertEquals(1, result.size)
        assertTrue("segment should contain same object instances", result[0][0] === pt1)
        assertTrue(result[0][1] === pt2)
    }

    // ── S2: dual-condition split (time gap AND distance jump) ────────────────

    @Test
    fun `stationary pause large time gap small distance does not split`() {
        // 0.00001° ≈ 1.1 m — far below REACQUIRE_DIST_M (60 m), but time gap > GPS_GAP_SEC.
        val gapMs = ((RecordingEngine.GPS_GAP_SEC + 5.0) * 1000).toLong()
        val points = listOf(
            TrackPoint(52.0, 21.0, t = 0L),
            TrackPoint(52.00001, 21.0, t = gapMs), // ~1.1 m — well below 60 m
        )
        val result = TrackSegmenter.split(points)
        assertEquals("stationary pause should remain one segment", 1, result.size)
        assertEquals(2, result[0].size)
    }

    @Test
    fun `true dropout large time gap and large distance splits into two segments`() {
        // 0.001° ≈ 111 m — above REACQUIRE_DIST_M (60 m), and time gap > GPS_GAP_SEC.
        val gapMs = ((RecordingEngine.GPS_GAP_SEC + 5.0) * 1000).toLong()
        val points = listOf(
            TrackPoint(52.0, 21.0, t = 0L),
            TrackPoint(52.001, 21.0, t = gapMs), // ~111 m — above 60 m
        )
        val result = TrackSegmenter.split(points)
        assertEquals("true dropout should produce two segments", 2, result.size)
    }

    @Test
    fun `walk-like track with many large time gaps but small deltas produces one segment`() {
        // Each step takes > GPS_GAP_SEC but covers only ~1.1 m — no step exceeds REACQUIRE_DIST_M.
        val gapMs = ((RecordingEngine.GPS_GAP_SEC + 5.0) * 1000).toLong()
        val walkPoints = (0 until 10).map { i ->
            TrackPoint(52.0 + i * 0.00001, 21.0, t = i * gapMs) // ~1.1 m per step
        }
        val result = TrackSegmenter.split(walkPoints)
        assertEquals("walk-like track should be one segment", 1, result.size)
        assertEquals(10, result[0].size)
    }

    @Test
    fun `boundary only time condition met does not split`() {
        // Time gap > GPS_GAP_SEC but distance < REACQUIRE_DIST_M (0.0004° ≈ 44 m < 60 m).
        val gapMs = ((RecordingEngine.GPS_GAP_SEC + 1.0) * 1000).toLong()
        val points = listOf(
            TrackPoint(52.0, 21.0, t = 0L),
            TrackPoint(52.0004, 21.0, t = gapMs), // ~44 m — below 60 m
        )
        val result = TrackSegmenter.split(points)
        assertEquals("time-only condition should not split", 1, result.size)
    }

    @Test
    fun `boundary only distance condition met does not split`() {
        // Distance > REACQUIRE_DIST_M (0.001° ≈ 111 m) but time gap < GPS_GAP_SEC (10 s < 20 s).
        val points = listOf(
            TrackPoint(52.0, 21.0, t = 0L),
            TrackPoint(52.001, 21.0, t = 10_000L), // 10 s < GPS_GAP_SEC
        )
        val result = TrackSegmenter.split(points)
        assertEquals("distance-only condition should not split", 1, result.size)
    }
}
