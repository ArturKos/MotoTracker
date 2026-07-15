package com.mototracker.ui.screens.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RecordLayoutSizing.forHeight].
 *
 * Verifies breakpoint correctness, boundary behaviour, monotonicity, extreme-value clamping, and
 * the contract that compact tokens are strictly smaller than comfortable tokens.
 */
class RecordLayoutSizingTest {

    // ── Breakpoint bands ──────────────────────────────────────────────────────

    @Test
    fun compactBandReturnsSmallTokens() {
        val s = RecordLayoutSizing.forHeight(400)
        assertEquals(28, s.speedFontSp)
        assertEquals(52, s.compassDiameterDp)
        assertEquals(22, s.bigNumberFontSp)
        assertEquals(3,  s.rowSpacingDp)
        assertEquals(44, s.controlButtonDp)
    }

    @Test
    fun regularBandReturnsMediumTokens() {
        val s = RecordLayoutSizing.forHeight(620)
        assertEquals(34, s.speedFontSp)
        assertEquals(62, s.compassDiameterDp)
        assertEquals(26, s.bigNumberFontSp)
        assertEquals(5,  s.rowSpacingDp)
        assertEquals(48, s.controlButtonDp)
    }

    @Test
    fun comfortableBandReturnsLargeTokens() {
        val s = RecordLayoutSizing.forHeight(800)
        assertEquals(40, s.speedFontSp)
        assertEquals(72, s.compassDiameterDp)
        assertEquals(30, s.bigNumberFontSp)
        assertEquals(6,  s.rowSpacingDp)
        assertEquals(52, s.controlButtonDp)
    }

    // ── Boundary values ───────────────────────────────────────────────────────

    @Test
    fun height559IsCompact() {
        assertEquals(RecordLayoutSizing.forHeight(400), RecordLayoutSizing.forHeight(559))
    }

    @Test
    fun height560IsRegular() {
        assertEquals(RecordLayoutSizing.forHeight(620), RecordLayoutSizing.forHeight(560))
    }

    @Test
    fun height719IsRegular() {
        assertEquals(RecordLayoutSizing.forHeight(620), RecordLayoutSizing.forHeight(719))
    }

    @Test
    fun height720IsComfortable() {
        assertEquals(RecordLayoutSizing.forHeight(800), RecordLayoutSizing.forHeight(720))
    }

    // ── Monotonic non-decreasing ──────────────────────────────────────────────

    @Test
    fun speedFontIsMonotonicNonDecreasing() {
        val heights = listOf(0, 200, 400, 559, 560, 600, 700, 719, 720, 800, 1200, Int.MAX_VALUE)
        val fonts = heights.map { RecordLayoutSizing.forHeight(it).speedFontSp }
        for (i in 0 until fonts.lastIndex) {
            assertTrue(
                "speedFontSp must be non-decreasing: index $i ${fonts[i]} > ${fonts[i + 1]}",
                fonts[i] <= fonts[i + 1],
            )
        }
    }

    @Test
    fun compassDiameterIsMonotonicNonDecreasing() {
        val heights = listOf(0, 300, 559, 560, 650, 719, 720, 1000)
        val diameters = heights.map { RecordLayoutSizing.forHeight(it).compassDiameterDp }
        for (i in 0 until diameters.lastIndex) {
            assertTrue(
                "compassDiameterDp must be non-decreasing: index $i ${diameters[i]} > ${diameters[i + 1]}",
                diameters[i] <= diameters[i + 1],
            )
        }
    }

    @Test
    fun bigNumberFontIsMonotonicNonDecreasing() {
        val heights = listOf(0, 400, 559, 560, 650, 719, 720, 900)
        val fonts = heights.map { RecordLayoutSizing.forHeight(it).bigNumberFontSp }
        for (i in 0 until fonts.lastIndex) {
            assertTrue(
                "bigNumberFontSp must be non-decreasing: index $i ${fonts[i]} > ${fonts[i + 1]}",
                fonts[i] <= fonts[i + 1],
            )
        }
    }

    // ── Clamping at extremes ──────────────────────────────────────────────────

    @Test
    fun zeroHeightClampsToCompact() {
        assertEquals(RecordLayoutSizing.forHeight(1), RecordLayoutSizing.forHeight(0))
    }

    @Test
    fun maxIntHeightClampsToComfortable() {
        assertEquals(RecordLayoutSizing.forHeight(720), RecordLayoutSizing.forHeight(Int.MAX_VALUE))
    }

    // ── Compact strictly smaller than comfortable ─────────────────────────────

    @Test
    fun compactSpeedFontStrictlySmallerThanComfortable() {
        val compact = RecordLayoutSizing.forHeight(300)
        val comfortable = RecordLayoutSizing.forHeight(800)
        assertTrue(
            "compact.speedFontSp (${compact.speedFontSp}) must be < comfortable.speedFontSp (${comfortable.speedFontSp})",
            compact.speedFontSp < comfortable.speedFontSp,
        )
    }

    @Test
    fun compactCompassDiameterStrictlySmallerThanComfortable() {
        val compact = RecordLayoutSizing.forHeight(300)
        val comfortable = RecordLayoutSizing.forHeight(800)
        assertTrue(
            "compact.compassDiameterDp (${compact.compassDiameterDp}) must be < comfortable.compassDiameterDp (${comfortable.compassDiameterDp})",
            compact.compassDiameterDp < comfortable.compassDiameterDp,
        )
    }

    @Test
    fun compactControlButtonStrictlySmallerThanComfortable() {
        val compact = RecordLayoutSizing.forHeight(300)
        val comfortable = RecordLayoutSizing.forHeight(800)
        assertTrue(
            "compact.controlButtonDp (${compact.controlButtonDp}) must be < comfortable.controlButtonDp (${comfortable.controlButtonDp})",
            compact.controlButtonDp < comfortable.controlButtonDp,
        )
    }

    @Test
    fun controlButtonIsMonotonicNonDecreasing() {
        val heights = listOf(0, 300, 559, 560, 650, 719, 720, 900)
        val buttons = heights.map { RecordLayoutSizing.forHeight(it).controlButtonDp }
        for (i in 0 until buttons.lastIndex) {
            assertTrue(
                "controlButtonDp must be non-decreasing: index $i ${buttons[i]} > ${buttons[i + 1]}",
                buttons[i] <= buttons[i + 1],
            )
        }
    }
}
