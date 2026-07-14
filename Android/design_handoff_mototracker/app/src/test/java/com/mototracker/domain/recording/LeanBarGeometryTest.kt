package com.mototracker.domain.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LeanBarGeometry] fill-fraction and marker-position math (E7).
 *
 * All tests run on the JVM with no Android dependencies.
 */
class LeanBarGeometryTest {

    // ── fillFraction ──────────────────────────────────────────────────────────

    @Test
    fun `fillFraction returns 0 for zero lean`() {
        assertEquals(0.0, LeanBarGeometry.fillFraction(0.0, 45.0), 0.0001)
    }

    @Test
    fun `fillFraction returns positive fraction for right lean`() {
        assertEquals(0.5, LeanBarGeometry.fillFraction(22.5, 45.0), 0.0001)
    }

    @Test
    fun `fillFraction returns negative fraction for left lean`() {
        assertEquals(-0.5, LeanBarGeometry.fillFraction(-22.5, 45.0), 0.0001)
    }

    @Test
    fun `fillFraction clamps to 1 when deg exceeds maxScaleDeg`() {
        assertEquals(1.0, LeanBarGeometry.fillFraction(90.0, 45.0), 0.0001)
    }

    @Test
    fun `fillFraction clamps to -1 when deg is less than -maxScaleDeg`() {
        assertEquals(-1.0, LeanBarGeometry.fillFraction(-90.0, 45.0), 0.0001)
    }

    @Test
    fun `fillFraction at full scale right returns 1`() {
        assertEquals(1.0, LeanBarGeometry.fillFraction(45.0, 45.0), 0.0001)
    }

    @Test
    fun `fillFraction at full scale left returns -1`() {
        assertEquals(-1.0, LeanBarGeometry.fillFraction(-45.0, 45.0), 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fillFraction throws when maxScaleDeg is zero`() {
        LeanBarGeometry.fillFraction(10.0, 0.0)
    }

    // ── markerFractions ───────────────────────────────────────────────────────

    @Test
    fun `markerFractions returns center for zero maxima`() {
        val (left, right) = LeanBarGeometry.markerFractions(0.0, 0.0, 45.0)
        assertEquals(0.5, left, 0.0001)
        assertEquals(0.5, right, 0.0001)
    }

    @Test
    fun `markerFractions left marker is less than 0_5 for non-zero left`() {
        val (left, _) = LeanBarGeometry.markerFractions(22.5, 0.0, 45.0)
        assertTrue("left marker should be below center", left < 0.5)
        assertEquals(0.25, left, 0.0001)
    }

    @Test
    fun `markerFractions right marker is greater than 0_5 for non-zero right`() {
        val (_, right) = LeanBarGeometry.markerFractions(0.0, 22.5, 45.0)
        assertTrue("right marker should be above center", right > 0.5)
        assertEquals(0.75, right, 0.0001)
    }

    @Test
    fun `markerFractions full scale left clamps to 0`() {
        val (left, _) = LeanBarGeometry.markerFractions(45.0, 0.0, 45.0)
        assertEquals(0.0, left, 0.0001)
    }

    @Test
    fun `markerFractions full scale right clamps to 1`() {
        val (_, right) = LeanBarGeometry.markerFractions(0.0, 45.0, 45.0)
        assertEquals(1.0, right, 0.0001)
    }

    @Test
    fun `markerFractions symmetric maxima produce symmetric markers`() {
        val (left, right) = LeanBarGeometry.markerFractions(30.0, 30.0, 45.0)
        // Both should be equidistant from center
        assertEquals(1.0 - right, left, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `markerFractions throws when maxScaleDeg is zero`() {
        LeanBarGeometry.markerFractions(10.0, 10.0, 0.0)
    }
}
