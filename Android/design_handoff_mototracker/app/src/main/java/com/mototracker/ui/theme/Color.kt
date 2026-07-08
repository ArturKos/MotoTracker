package com.mototracker.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Theme / accent enumerations
// ─────────────────────────────────────────────────────────────────────────────

/** Three visual modes the user can choose in Settings. */
enum class MotoTheme { COCKPIT, GRID, LIGHT }

/**
 * User-selectable accent colour; overrides the theme default accent while
 * leaving accent2 (the secondary / warning hue) as the theme defines it.
 *
 * @property value The resolved [Color] for this accent choice.
 */
enum class AccentColor(val value: Color) {
    TEAL(Color(0xFF00D1B2)),
    ORANGE(Color(0xFFFF5C38)),
    LIME(Color(0xFFC6FF00)),
    BLUE(Color(0xFF3B82F6)),
}

// ─────────────────────────────────────────────────────────────────────────────
// Colour palette data class
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Immutable colour palette for one theme + accent combination.
 *
 * @param bg Background colour (behind all panels).
 * @param panel Primary surface (cards, bottom sheets).
 * @param panel2 Elevated surface (nested cards, overlays).
 * @param line Divider / stroke colour.
 * @param text Primary text colour.
 * @param dim Secondary / caption text colour.
 * @param accent Primary brand accent — overridable via [AccentColor].
 * @param accent2 Secondary accent / warning hue — always the theme's own value.
 * @param onAccent Content colour drawn on [accent]. Fixed = #05100E across all themes.
 * @param onAccent2 Content colour drawn on [accent2]. Fixed = #1A0A05 across all themes.
 */
data class MotoColors(
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val line: Color,
    val text: Color,
    val dim: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val onAccent2: Color,
)

// ─────────────────────────────────────────────────────────────────────────────
// Fixed on-accent values (README: constant regardless of theme or accent pick)
// ─────────────────────────────────────────────────────────────────────────────

internal val OnAccentFixed = Color(0xFF05100E)
internal val OnAccent2Fixed = Color(0xFF1A0A05)

// ─────────────────────────────────────────────────────────────────────────────
// COCKPIT raw tokens
// ─────────────────────────────────────────────────────────────────────────────

internal val CockpitBg = Color(0xFF0A0B0D)
internal val CockpitPanel = Color(0xFF14171B)
internal val CockpitPanel2 = Color(0xFF1D2127)

/** White at 8 % alpha — rgba(255,255,255,0.08) per README. 0x14 = round(255 × 0.08) = 20. */
internal val CockpitLine = Color(0x14FFFFFF)
internal val CockpitText = Color(0xFFF1F4F3)
internal val CockpitDim = Color(0xFF899089)
internal val CockpitAccent = Color(0xFF00D1B2)
internal val CockpitAccent2 = Color(0xFFFF5C38)

// ─────────────────────────────────────────────────────────────────────────────
// GRID raw tokens
// (panel2 / line / text / dim not restated in README → shared with COCKPIT dark family)
// ─────────────────────────────────────────────────────────────────────────────

internal val GridBg = Color(0xFF0B0F15)
internal val GridPanel = Color(0xFF121924)
internal val GridPanel2 = CockpitPanel2
internal val GridLine = CockpitLine
internal val GridText = CockpitText
internal val GridDim = CockpitDim
internal val GridAccent = Color(0xFF12D6C0)
internal val GridAccent2 = Color(0xFFFFB020)

// ─────────────────────────────────────────────────────────────────────────────
// LIGHT raw tokens
// (panel2 = off-white surface variant; line = black @ 8 % alpha, derived)
// ─────────────────────────────────────────────────────────────────────────────

internal val LightBg = Color(0xFFE9EEEC)
internal val LightPanel = Color(0xFFFFFFFF)

/** Slightly tinted white surface variant — not stated in README, derived sensibly. */
internal val LightPanel2 = Color(0xFFF0F4F2)

/** Black at 8 % alpha — dark-on-light analogue of [CockpitLine]. */
internal val LightLine = Color(0x14000000)
internal val LightText = Color(0xFF111819)
internal val LightDim = Color(0xFF5C6660)
internal val LightAccent = Color(0xFF009A89)
internal val LightAccent2 = Color(0xFFE2542E)

// ─────────────────────────────────────────────────────────────────────────────
// Base palettes (before accent override)
// ─────────────────────────────────────────────────────────────────────────────

private val cockpitBase = MotoColors(
    bg = CockpitBg, panel = CockpitPanel, panel2 = CockpitPanel2,
    line = CockpitLine, text = CockpitText, dim = CockpitDim,
    accent = CockpitAccent, accent2 = CockpitAccent2,
    onAccent = OnAccentFixed, onAccent2 = OnAccent2Fixed,
)

private val gridBase = MotoColors(
    bg = GridBg, panel = GridPanel, panel2 = GridPanel2,
    line = GridLine, text = GridText, dim = GridDim,
    accent = GridAccent, accent2 = GridAccent2,
    onAccent = OnAccentFixed, onAccent2 = OnAccent2Fixed,
)

private val lightBase = MotoColors(
    bg = LightBg, panel = LightPanel, panel2 = LightPanel2,
    line = LightLine, text = LightText, dim = LightDim,
    accent = LightAccent, accent2 = LightAccent2,
    onAccent = OnAccentFixed, onAccent2 = OnAccent2Fixed,
)

// ─────────────────────────────────────────────────────────────────────────────
// Resolver — the primary unit-tested seam
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns the full [MotoColors] palette for [theme], replacing the theme's
 * default accent hue with [accent].value. accent2, onAccent, and onAccent2
 * are always the theme / README values and are never overridden here.
 */
fun resolveMotoColors(theme: MotoTheme, accent: AccentColor): MotoColors {
    val base = when (theme) {
        MotoTheme.COCKPIT -> cockpitBase
        MotoTheme.GRID    -> gridBase
        MotoTheme.LIGHT   -> lightBase
    }
    return base.copy(accent = accent.value)
}
