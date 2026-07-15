package com.mototracker.data.location

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssSatelliteCounterTest {

    @Test
    fun `total zero returns (0, 0)`() {
        val result = GnssSatelliteCounter.count(0) { true }
        assertEquals(GnssSatelliteCount(usedInFix = 0, total = 0), result)
    }

    @Test
    fun `negative total treated as zero — returns (0, 0)`() {
        val result = GnssSatelliteCounter.count(-5) { true }
        assertEquals(GnssSatelliteCount(usedInFix = 0, total = 0), result)
    }

    @Test
    fun `all satellites used in fix`() {
        val result = GnssSatelliteCounter.count(8) { true }
        assertEquals(GnssSatelliteCount(usedInFix = 8, total = 8), result)
    }

    @Test
    fun `no satellites used in fix`() {
        val result = GnssSatelliteCounter.count(6) { false }
        assertEquals(GnssSatelliteCount(usedInFix = 0, total = 6), result)
    }

    @Test
    fun `mixed — only even indices used in fix`() {
        // indices 0, 2, 4 → 3 used out of 6
        val result = GnssSatelliteCounter.count(6) { index -> index % 2 == 0 }
        assertEquals(GnssSatelliteCount(usedInFix = 3, total = 6), result)
    }

    @Test
    fun `single satellite used in fix`() {
        val result = GnssSatelliteCounter.count(1) { true }
        assertEquals(GnssSatelliteCount(usedInFix = 1, total = 1), result)
    }

    @Test
    fun `single satellite not used in fix`() {
        val result = GnssSatelliteCounter.count(1) { false }
        assertEquals(GnssSatelliteCount(usedInFix = 0, total = 1), result)
    }

    @Test
    fun `large constellation — partial fix`() {
        // 12 satellites visible, indices 0..4 used (5 used)
        val result = GnssSatelliteCounter.count(12) { index -> index < 5 }
        assertEquals(GnssSatelliteCount(usedInFix = 5, total = 12), result)
    }
}
