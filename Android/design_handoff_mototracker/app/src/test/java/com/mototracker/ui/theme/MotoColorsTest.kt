package com.mototracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for the MotoTracker design-token resolver.
 *
 * Covers:
 * - Exact colour values for all three base themes
 * - Accent-override: replaces [MotoColors.accent] but never [MotoColors.accent2]
 * - [OnAccentFixed] / [OnAccent2Fixed] are constant across all themes
 * - Per-theme card corner radius from [resolveMotoShapes]
 */
class MotoColorsTest {

    // ─────────────────────────────────────────────────────────────────────────
    // COCKPIT theme token values
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun cockpit_bg_isCorrect() {
        val c = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.TEAL)
        assertEquals(Color(0xFF0A0B0D), c.bg)
    }

    @Test
    fun cockpit_panel_isCorrect() {
        val c = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.TEAL)
        assertEquals(Color(0xFF14171B), c.panel)
    }

    @Test
    fun cockpit_panel2_isCorrect() {
        val c = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.TEAL)
        assertEquals(Color(0xFF1D2127), c.panel2)
    }

    @Test
    fun cockpit_line_isWhiteAt8PercentAlpha() {
        val c = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.TEAL)
        // rgba(255,255,255,0.08) → alpha = round(255×0.08) = 20 = 0x14
        assertEquals(Color(0x14FFFFFF), c.line)
    }

    @Test
    fun cockpit_text_isCorrect() {
        val c = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.TEAL)
        assertEquals(Color(0xFFF1F4F3), c.text)
    }

    @Test
    fun cockpit_dim_isCorrect() {
        val c = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.TEAL)
        assertEquals(Color(0xFF899089), c.dim)
    }

    @Test
    fun cockpit_defaultAccent_isTeal() {
        val c = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.TEAL)
        assertEquals(Color(0xFF00D1B2), c.accent)
    }

    @Test
    fun cockpit_accent2_isOrange() {
        val c = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.TEAL)
        assertEquals(Color(0xFFFF5C38), c.accent2)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GRID theme token values
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun grid_bg_isCorrect() {
        val c = resolveMotoColors(MotoTheme.GRID, AccentColor.TEAL)
        assertEquals(Color(0xFF0B0F15), c.bg)
    }

    @Test
    fun grid_panel_isCorrect() {
        val c = resolveMotoColors(MotoTheme.GRID, AccentColor.TEAL)
        assertEquals(Color(0xFF121924), c.panel)
    }

    @Test
    fun grid_accent2_isAmber() {
        val c = resolveMotoColors(MotoTheme.GRID, AccentColor.TEAL)
        assertEquals(Color(0xFFFFB020), c.accent2)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIGHT theme token values
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun light_bg_isCorrect() {
        val c = resolveMotoColors(MotoTheme.LIGHT, AccentColor.TEAL)
        assertEquals(Color(0xFFE9EEEC), c.bg)
    }

    @Test
    fun light_panel_isWhite() {
        val c = resolveMotoColors(MotoTheme.LIGHT, AccentColor.TEAL)
        assertEquals(Color(0xFFFFFFFF), c.panel)
    }

    @Test
    fun light_text_isCorrect() {
        val c = resolveMotoColors(MotoTheme.LIGHT, AccentColor.TEAL)
        assertEquals(Color(0xFF111819), c.text)
    }

    @Test
    fun light_dim_isCorrect() {
        val c = resolveMotoColors(MotoTheme.LIGHT, AccentColor.TEAL)
        assertEquals(Color(0xFF5C6660), c.dim)
    }

    @Test
    fun light_nativeAccentToken_isCorrect() {
        // Test the raw LIGHT token constant directly; resolveMotoColors always
        // overrides accent, so the base value must be verified via the internal constant.
        assertEquals(Color(0xFF009A89), LightAccent)
    }

    @Test
    fun light_accent_overriddenByAccentColor() {
        // When TEAL accent is chosen, it replaces the LIGHT theme's native #009a89.
        val c = resolveMotoColors(MotoTheme.LIGHT, AccentColor.TEAL)
        assertEquals(AccentColor.TEAL.value, c.accent)
    }

    @Test
    fun light_accent2_isCorrect() {
        val c = resolveMotoColors(MotoTheme.LIGHT, AccentColor.TEAL)
        assertEquals(Color(0xFFE2542E), c.accent2)
    }

    @Test
    fun light_line_isBlackAt8PercentAlpha() {
        val c = resolveMotoColors(MotoTheme.LIGHT, AccentColor.TEAL)
        assertEquals(Color(0x14000000), c.line)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accent override behaviour
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun accentOverride_replacesAccent_notAccent2() {
        val c = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.ORANGE)
        assertEquals(Color(0xFFFF5C38), c.accent)     // AccentColor.ORANGE.value
        assertEquals(Color(0xFFFF5C38), c.accent2)    // cockpit accent2 unchanged
    }

    @Test
    fun accentOverride_lime_setsCorrectColor() {
        val c = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.LIME)
        assertEquals(Color(0xFFC6FF00), c.accent)
    }

    @Test
    fun accentOverride_blue_setsCorrectColor() {
        val c = resolveMotoColors(MotoTheme.GRID, AccentColor.BLUE)
        assertEquals(Color(0xFF3B82F6), c.accent)
        // accent2 stays at GRID theme value
        assertEquals(Color(0xFFFFB020), c.accent2)
    }

    @Test
    fun accentOverride_doesNotChangeOtherFields() {
        val base = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.TEAL)
        val overridden = resolveMotoColors(MotoTheme.COCKPIT, AccentColor.BLUE)
        assertEquals(base.bg, overridden.bg)
        assertEquals(base.panel, overridden.panel)
        assertEquals(base.panel2, overridden.panel2)
        assertEquals(base.line, overridden.line)
        assertEquals(base.text, overridden.text)
        assertEquals(base.dim, overridden.dim)
        assertEquals(base.accent2, overridden.accent2)
        assertNotEquals(base.accent, overridden.accent)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // onAccent / onAccent2 are constant across all themes
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun onAccent_isConstantAcrossAllThemes() {
        val expected = Color(0xFF05100E)
        MotoTheme.entries.forEach { theme ->
            val c = resolveMotoColors(theme, AccentColor.TEAL)
            assertEquals("onAccent mismatch for $theme", expected, c.onAccent)
        }
    }

    @Test
    fun onAccent2_isConstantAcrossAllThemes() {
        val expected = Color(0xFF1A0A05)
        MotoTheme.entries.forEach { theme ->
            val c = resolveMotoColors(theme, AccentColor.TEAL)
            assertEquals("onAccent2 mismatch for $theme", expected, c.onAccent2)
        }
    }

    @Test
    fun onAccent_isConstantRegardlessOfAccentChoice() {
        val expected = Color(0xFF05100E)
        AccentColor.entries.forEach { accent ->
            val c = resolveMotoColors(MotoTheme.COCKPIT, accent)
            assertEquals("onAccent mismatch for accent $accent", expected, c.onAccent)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-theme card corner radius
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun cockpit_cardCornerRadius_is16dp() {
        assertEquals(16.dp, resolveMotoShapes(MotoTheme.COCKPIT).cardCornerRadius)
    }

    @Test
    fun grid_cardCornerRadius_is5dp() {
        assertEquals(5.dp, resolveMotoShapes(MotoTheme.GRID).cardCornerRadius)
    }

    @Test
    fun light_cardCornerRadius_is14dp() {
        assertEquals(14.dp, resolveMotoShapes(MotoTheme.LIGHT).cardCornerRadius)
    }
}
