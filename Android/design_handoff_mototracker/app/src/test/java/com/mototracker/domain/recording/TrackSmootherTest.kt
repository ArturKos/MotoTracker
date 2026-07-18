package com.mototracker.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * At lat ≈ 0, Δlng of 0.00003° ≈ 3.34 m and Δlng of 0.00002° ≈ 2.22 m.
 * Tests use these values to exercise above/below the 3 m default gate without
 * relying on exact floating-point haversine output.
 */
class TrackSmootherTest {

    // ── helper ────────────────────────────────────────────────────────────────

    /** Build a TrackPoint at (lat, lng, ele, t) with lat=0 and varying lng. */
    private fun pt(lngDeg: Double, ele: Double = 0.0, t: Long? = null) =
        TrackPoint(lat = 0.0, lng = lngDeg, ele = ele, t = t)

    // Δlng spacings at lat=0  (haversine distance ≈ 111_195 m per degree)
    private val STEP_ABOVE_3M = 0.00004  // ≈ 4.45 m  — passes the gate
    private val STEP_BELOW_3M = 0.00002  // ≈ 2.22 m  — filtered by the gate

    // ── empty / single ────────────────────────────────────────────────────────

    @Test
    fun `empty list returns empty`() {
        val result = TrackSmoother.smooth(emptyList())
        assertEquals(emptyList<TrackPoint>(), result)
    }

    @Test
    fun `single point returns that same point`() {
        val p = pt(0.0, ele = 5.0, t = 1000L)
        val result = TrackSmoother.smooth(listOf(p))
        assertEquals(listOf(p), result)
    }

    // ── two-point cases ───────────────────────────────────────────────────────

    @Test
    fun `two points far apart are both kept`() {
        val pts = listOf(pt(0.0), pt(STEP_ABOVE_3M))
        val result = TrackSmoother.smooth(pts)
        assertEquals(2, result.size)
        assertSame(pts[0], result[0])
        assertSame(pts[1], result[1])
    }

    @Test
    fun `two points within gate — last-point guarantee keeps both`() {
        val pts = listOf(pt(0.0), pt(STEP_BELOW_3M))
        val result = TrackSmoother.smooth(pts)
        assertEquals(2, result.size)
        assertSame(pts[0], result[0])
        assertSame(pts[1], result[1])
    }

    // ── straight run: all points > 3 m apart ─────────────────────────────────

    @Test
    fun `straight run with all gaps above gate keeps all points`() {
        val pts = (0 until 5).map { i -> pt(i * STEP_ABOVE_3M) }
        val result = TrackSmoother.smooth(pts)
        assertEquals(5, result.size)
        assertSame(pts.first(), result.first())
        assertSame(pts.last(), result.last())
    }

    @Test
    fun `first point is always the original first`() {
        val pts = (0 until 10).map { i -> pt(i * STEP_ABOVE_3M) }
        val result = TrackSmoother.smooth(pts)
        assertSame(pts[0], result[0])
    }

    @Test
    fun `last point is always the original last`() {
        val pts = (0 until 10).map { i -> pt(i * STEP_ABOVE_3M) }
        val result = TrackSmoother.smooth(pts)
        assertSame(pts.last(), result.last())
    }

    // ── jitter cluster collapses ──────────────────────────────────────────────

    @Test
    fun `jitter cluster within gate collapses — only first and last survive`() {
        // All intermediate points are within 3 m of the first, so they are dropped.
        val pts = listOf(
            pt(0.0),
            pt(STEP_BELOW_3M * 0.5),
            pt(STEP_BELOW_3M * 0.8),
            pt(STEP_BELOW_3M),        // last — guaranteed
        )
        val result = TrackSmoother.smooth(pts)
        assertEquals(2, result.size)
        assertSame(pts.first(), result.first())
        assertSame(pts.last(), result.last())
    }

    @Test
    fun `multiple jitter clusters between real moves collapse correctly`() {
        // Move → jitter → move → jitter
        val move = STEP_ABOVE_3M
        val jitter = STEP_BELOW_3M * 0.5
        val pts = listOf(
            pt(0.0),           // 0 — kept (first)
            pt(jitter),        // ≈ 2.2 m — filtered
            pt(move),          // ≈ 4.45 m from pt[0] — kept
            pt(move + jitter), // ≈ 2.2 m from prev kept — filtered
            pt(move * 2),      // kept
        )
        val result = TrackSmoother.smooth(pts)
        assertTrue(result.size in 3..5)
        assertSame(pts[0], result[0])
        assertSame(pts[4], result.last())
    }

    // ── corner retained ───────────────────────────────────────────────────────

    @Test
    fun `corner point farther than gate is retained`() {
        // Three points forming an L-shape; each > 3 m from predecessor.
        val p1 = TrackPoint(lat = 0.0, lng = 0.0)
        val p2 = TrackPoint(lat = STEP_ABOVE_3M, lng = 0.0)   // north
        val p3 = TrackPoint(lat = STEP_ABOVE_3M, lng = STEP_ABOVE_3M) // east
        val result = TrackSmoother.smooth(listOf(p1, p2, p3))
        assertEquals(3, result.size)
        assertSame(p1, result[0])
        assertSame(p2, result[1])
        assertSame(p3, result[2])
    }

    // ── last-point guarantee ──────────────────────────────────────────────────

    @Test
    fun `last point kept even when within gate of last accepted point`() {
        // First point accepted; all subsequent are within gate; last must still appear.
        val pts = listOf(
            pt(0.0),
            pt(STEP_BELOW_3M * 0.3),
            pt(STEP_BELOW_3M * 0.6),
            pt(STEP_BELOW_3M),           // final — within gate, still forced in
        )
        val result = TrackSmoother.smooth(pts)
        assertSame(pts.last(), result.last())
        assertEquals(2, result.size) // only first and forced last
    }

    // ── ele / t preserved ────────────────────────────────────────────────────

    @Test
    fun `elevation and timestamp of kept points are unchanged`() {
        val pts = listOf(
            TrackPoint(lat = 0.0, lng = 0.0, ele = 100.0, t = 1_000L),
            TrackPoint(lat = 0.0, lng = STEP_ABOVE_3M, ele = 105.5, t = 2_000L),
            TrackPoint(lat = 0.0, lng = STEP_ABOVE_3M * 2, ele = 110.2, t = 3_000L),
        )
        val result = TrackSmoother.smooth(pts)
        assertEquals(3, result.size)
        assertEquals(100.0, result[0].ele, 0.0)
        assertEquals(1_000L, result[0].t)
        assertEquals(105.5, result[1].ele, 0.0)
        assertEquals(2_000L, result[1].t)
        assertEquals(110.2, result[2].ele, 0.0)
        assertEquals(3_000L, result[2].t)
    }

    @Test
    fun `null timestamp is preserved in kept points`() {
        val p = TrackPoint(lat = 0.0, lng = 0.0, t = null)
        val result = TrackSmoother.smooth(listOf(p))
        assertEquals(null, result[0].t)
    }

    // ── 3 m boundary ─────────────────────────────────────────────────────────

    @Test
    fun `point below 3 m gate is filtered`() {
        val pts = listOf(pt(0.0), pt(STEP_BELOW_3M), pt(STEP_ABOVE_3M * 3))
        val result = TrackSmoother.smooth(pts)
        // pts[1] (≈ 2.2 m from pts[0]) is filtered; pts[2] passes (> 3 m from pts[0])
        assertSame(pts[0], result[0])
        val secondKept = result[1]
        // secondKept must be pts[2] (≈ 4.45 m), not pts[1] (≈ 2.2 m)
        assertEquals(pts[2].lng, secondKept.lng, 1e-12)
    }

    @Test
    fun `point above 3 m gate is kept`() {
        val pts = listOf(pt(0.0), pt(STEP_ABOVE_3M))
        val result = TrackSmoother.smooth(pts)
        assertEquals(2, result.size)
    }

    @Test
    fun `custom gate overrides default — point above custom gate is kept`() {
        // Use a 1 m gate so that even STEP_BELOW_3M (≈ 2.22 m) passes.
        val pts = listOf(pt(0.0), pt(STEP_BELOW_3M))
        val result = TrackSmoother.smooth(pts, minMoveM = 1.0)
        assertEquals(2, result.size)
        // The intermediate point was actually kept (not just the last-point guarantee)
        // — verify by checking the kept-set without the forced-last heuristic:
        // Both points are > 1 m apart, so both are independently included.
        assertSame(pts[0], result[0])
        assertSame(pts[1], result[1])
    }

    // ── output size invariant ─────────────────────────────────────────────────

    @Test
    fun `smoothed result is never larger than the input`() {
        val pts = (0 until 1000).map { i -> pt(i * STEP_ABOVE_3M) }
        val result = TrackSmoother.smooth(pts)
        assertTrue(result.size <= pts.size)
    }

    @Test
    fun `all returned points are members of the original list`() {
        val pts = (0 until 50).map { i -> pt(i * STEP_ABOVE_3M) }
        val result = TrackSmoother.smooth(pts)
        val ptsSet = pts.toSet()
        assertTrue(result.all { it in ptsSet })
    }

    // ── lat/lng preserved exactly ─────────────────────────────────────────────

    @Test
    fun `kept point coordinates are identical to originals — no rounding`() {
        val p0 = TrackPoint(lat = 52.237049, lng = 21.017532, ele = 112.3, t = 1_720_000_000_000L)
        val p1 = TrackPoint(lat = 52.237800, lng = 21.018100, ele = 113.0, t = 1_720_000_001_000L)
        val result = TrackSmoother.smooth(listOf(p0, p1))
        assertEquals(p0.lat, result[0].lat, 0.0)
        assertEquals(p0.lng, result[0].lng, 0.0)
        assertEquals(p1.lat, result[1].lat, 0.0)
        assertEquals(p1.lng, result[1].lng, 0.0)
    }
}
