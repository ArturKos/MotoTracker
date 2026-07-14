package com.mototracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceDownsamplerTest {

    // ── empty / trivial inputs ────────────────────────────────────────────────

    @Test
    fun `downsample empty list returns empty list`() {
        assertEquals(emptyList<Pair<Double, Double>>(), TraceDownsampler.downsample(emptyList(), 10))
    }

    @Test
    fun `downsample single point returns that point`() {
        val pts = listOf(1.0 to 2.0)
        assertEquals(pts, TraceDownsampler.downsample(pts, 10))
    }

    @Test
    fun `downsample two points returns both`() {
        val pts = listOf(0.0 to 0.0, 1.0 to 1.0)
        assertEquals(pts, TraceDownsampler.downsample(pts, 10))
    }

    // ── identity when n ≤ maxPoints ───────────────────────────────────────────

    @Test
    fun `downsample returns identity when list size equals maxPoints`() {
        val pts = (0 until 5).map { it.toDouble() to it.toDouble() }
        assertEquals(pts, TraceDownsampler.downsample(pts, 5))
    }

    @Test
    fun `downsample returns identity when list size is less than maxPoints`() {
        val pts = (0 until 3).map { it.toDouble() to it.toDouble() }
        assertEquals(pts, TraceDownsampler.downsample(pts, 10))
    }

    // ── first and last points preserved ──────────────────────────────────────

    @Test
    fun `first point is always present`() {
        val pts = (0 until 100).map { it.toDouble() to 0.0 }
        val result = TraceDownsampler.downsample(pts, 10)
        assertEquals(pts.first(), result.first())
    }

    @Test
    fun `last point is always present`() {
        val pts = (0 until 100).map { it.toDouble() to 0.0 }
        val result = TraceDownsampler.downsample(pts, 10)
        assertEquals(pts.last(), result.last())
    }

    // ── output size ───────────────────────────────────────────────────────────

    @Test
    fun `output size does not exceed maxPoints`() {
        val pts = (0 until 1000).map { it.toDouble() to 0.0 }
        val result = TraceDownsampler.downsample(pts, 120)
        assertTrue(result.size <= 120)
    }

    @Test
    fun `output size is maxPoints when input is much larger`() {
        val pts = (0 until 5000).map { it.toDouble() to 0.0 }
        val result = TraceDownsampler.downsample(pts, 120)
        assertEquals(120, result.size)
    }

    // ── uniform spacing ───────────────────────────────────────────────────────

    @Test
    fun `downsample 10 from 100 points returns every 10th point`() {
        val pts = (0 until 100).map { it.toDouble() to 0.0 }
        val result = TraceDownsampler.downsample(pts, 10)
        assertEquals(10, result.size)
        // The last point must be pts[99]
        assertEquals(99.0 to 0.0, result.last())
    }

    @Test
    fun `downsample preserves distinct coordinate values`() {
        val pts = listOf(0.0 to 0.0, 10.0 to 20.0, 30.0 to 40.0, 50.0 to 60.0, 70.0 to 80.0)
        val result = TraceDownsampler.downsample(pts, 3)
        assertEquals(3, result.size)
        assertEquals(pts.first(), result.first())
        assertEquals(pts.last(), result.last())
    }
}
