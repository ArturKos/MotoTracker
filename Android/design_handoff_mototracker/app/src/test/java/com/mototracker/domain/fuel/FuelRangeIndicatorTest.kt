package com.mototracker.domain.fuel

import org.junit.Assert.assertEquals
import org.junit.Test

class FuelRangeIndicatorTest {

    // ── GREEN — fraction > 0.50, range ≥ 100 km ──────────────────────────────

    @Test
    fun `GREEN when fraction is high and range is ample`() {
        assertEquals(FuelRangeColor.GREEN, FuelRangeIndicator.colorFor(0.8, 400.0))
    }

    @Test
    fun `GREEN when fraction is just above 0_50 and range at exactly 100`() {
        assertEquals(FuelRangeColor.GREEN, FuelRangeIndicator.colorFor(0.51, 100.0))
    }

    // ── YELLOW — fraction 0.20..0.50 inclusive, range ≥ 100 km ──────────────

    @Test
    fun `YELLOW when fraction is in the mid range`() {
        assertEquals(FuelRangeColor.YELLOW, FuelRangeIndicator.colorFor(0.35, 150.0))
    }

    @Test
    fun `YELLOW at fraction boundary exactly 0_50`() {
        assertEquals(FuelRangeColor.YELLOW, FuelRangeIndicator.colorFor(0.50, 200.0))
    }

    @Test
    fun `YELLOW at fraction boundary exactly 0_20 with range over 100`() {
        assertEquals(FuelRangeColor.YELLOW, FuelRangeIndicator.colorFor(0.20, 120.0))
    }

    // ── RED via fraction < 0.20 ───────────────────────────────────────────────

    @Test
    fun `RED when fraction is below 0_20`() {
        assertEquals(FuelRangeColor.RED, FuelRangeIndicator.colorFor(0.15, 200.0))
    }

    @Test
    fun `RED when fraction is just below 0_20`() {
        assertEquals(FuelRangeColor.RED, FuelRangeIndicator.colorFor(0.199, 200.0))
    }

    @Test
    fun `RED when fraction is zero`() {
        assertEquals(FuelRangeColor.RED, FuelRangeIndicator.colorFor(0.0, 300.0))
    }

    // ── RED via absolute range < 100 km (small-tank override) ────────────────

    @Test
    fun `RED via range override when fraction is high but range below 100`() {
        assertEquals(FuelRangeColor.RED, FuelRangeIndicator.colorFor(0.6, 80.0))
    }

    @Test
    fun `RED via range override when range is just below 100`() {
        assertEquals(FuelRangeColor.RED, FuelRangeIndicator.colorFor(0.9, 99.9))
    }

    // ── Range boundary exactly 100 km ────────────────────────────────────────

    @Test
    fun `range exactly 100_0 is not RED via the reserve rule`() {
        // fraction 0.6 → GREEN (range exactly 100.0 does not trigger RED)
        assertEquals(FuelRangeColor.GREEN, FuelRangeIndicator.colorFor(0.6, 100.0))
    }

    @Test
    fun `range exactly 100_0 with fraction 0_30 yields YELLOW`() {
        assertEquals(FuelRangeColor.YELLOW, FuelRangeIndicator.colorFor(0.30, 100.0))
    }

    // ── UNKNOWN — null / NaN / Infinite ──────────────────────────────────────

    @Test
    fun `UNKNOWN when fraction is null`() {
        assertEquals(FuelRangeColor.UNKNOWN, FuelRangeIndicator.colorFor(null, 200.0))
    }

    @Test
    fun `UNKNOWN when range is null`() {
        assertEquals(FuelRangeColor.UNKNOWN, FuelRangeIndicator.colorFor(0.5, null))
    }

    @Test
    fun `UNKNOWN when both are null`() {
        assertEquals(FuelRangeColor.UNKNOWN, FuelRangeIndicator.colorFor(null, null))
    }

    @Test
    fun `UNKNOWN when fraction is NaN`() {
        assertEquals(FuelRangeColor.UNKNOWN, FuelRangeIndicator.colorFor(Double.NaN, 200.0))
    }

    @Test
    fun `UNKNOWN when range is NaN`() {
        assertEquals(FuelRangeColor.UNKNOWN, FuelRangeIndicator.colorFor(0.5, Double.NaN))
    }

    @Test
    fun `UNKNOWN when fraction is positive infinity`() {
        assertEquals(FuelRangeColor.UNKNOWN, FuelRangeIndicator.colorFor(Double.POSITIVE_INFINITY, 200.0))
    }

    @Test
    fun `UNKNOWN when range is negative infinity`() {
        assertEquals(FuelRangeColor.UNKNOWN, FuelRangeIndicator.colorFor(0.5, Double.NEGATIVE_INFINITY))
    }
}
