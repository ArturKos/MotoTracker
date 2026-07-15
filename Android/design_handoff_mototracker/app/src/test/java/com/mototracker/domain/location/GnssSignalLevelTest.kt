package com.mototracker.domain.location

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [GnssSignalLevel.fromSatelliteCount] threshold boundaries (K7).
 */
class GnssSignalLevelTest {

    // ── NONE band (≤ 0) ─────────────────────────────────────────────────────

    @Test
    fun `negative count returns NONE`() {
        assertEquals(GnssSignalLevel.NONE, GnssSignalLevel.fromSatelliteCount(-1))
    }

    @Test
    fun `zero count returns NONE`() {
        assertEquals(GnssSignalLevel.NONE, GnssSignalLevel.fromSatelliteCount(0))
    }

    // ── ACQUIRING band (1–3) ─────────────────────────────────────────────────

    @Test
    fun `one satellite returns ACQUIRING`() {
        assertEquals(GnssSignalLevel.ACQUIRING, GnssSignalLevel.fromSatelliteCount(1))
    }

    @Test
    fun `two satellites returns ACQUIRING`() {
        assertEquals(GnssSignalLevel.ACQUIRING, GnssSignalLevel.fromSatelliteCount(2))
    }

    @Test
    fun `three satellites returns ACQUIRING`() {
        assertEquals(GnssSignalLevel.ACQUIRING, GnssSignalLevel.fromSatelliteCount(3))
    }

    // ── FIXED band (≥ 4) ─────────────────────────────────────────────────────

    @Test
    fun `four satellites returns FIXED`() {
        assertEquals(GnssSignalLevel.FIXED, GnssSignalLevel.fromSatelliteCount(4))
    }

    @Test
    fun `five satellites returns FIXED`() {
        assertEquals(GnssSignalLevel.FIXED, GnssSignalLevel.fromSatelliteCount(5))
    }

    @Test
    fun `twelve satellites returns FIXED`() {
        assertEquals(GnssSignalLevel.FIXED, GnssSignalLevel.fromSatelliteCount(12))
    }
}
