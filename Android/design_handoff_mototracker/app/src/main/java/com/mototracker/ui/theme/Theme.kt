package com.mototracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

// ─────────────────────────────────────────────────────────────────────────────
// CompositionLocals
// ─────────────────────────────────────────────────────────────────────────────

/** Provides [MotoColors] for the active theme + accent combination. */
val LocalMotoColors = staticCompositionLocalOf<MotoColors> {
    resolveMotoColors(MotoTheme.COCKPIT, AccentColor.TEAL)
}

/** Provides [MotoTypography] (same font scale across all themes). */
val LocalMotoTypography = staticCompositionLocalOf<MotoTypography> {
    MotoTypography.Default
}

/** Provides [MotoShapes] for the active theme's corner radii. */
val LocalMotoShapes = staticCompositionLocalOf<MotoShapes> {
    resolveMotoShapes(MotoTheme.COCKPIT)
}

// ─────────────────────────────────────────────────────────────────────────────
// Theme accessor (mirrors MaterialTheme access pattern)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Entry point for reading active theme values inside a Composable, analogous
 * to [MaterialTheme].
 *
 * Usage:
 * ```kotlin
 * Box(Modifier.background(MotoTracker.colors.bg)) {
 *     Text(style = MotoTracker.typography.body)
 * }
 * ```
 */
object MotoTracker {

    /** The resolved colour palette for the current theme + accent. */
    val colors: MotoColors
        @Composable @ReadOnlyComposable
        get() = LocalMotoColors.current

    /** The typography scale (font families + text sizes). */
    val typography: MotoTypography
        @Composable @ReadOnlyComposable
        get() = LocalMotoTypography.current

    /** Corner-radius tokens for the current theme. */
    val shapes: MotoShapes
        @Composable @ReadOnlyComposable
        get() = LocalMotoShapes.current
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal M3 colour-scheme builder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps MotoTracker design tokens onto an M3 [ColorScheme] so that stock
 * Material 3 components (Buttons, TopAppBar, etc.) inherit the correct
 * colours without manual overrides.
 *
 * Mapping per README / task spec:
 * - bg      → background / onBackground
 * - panel   → surface    / onSurface
 * - accent  → primary    / onPrimary
 * - accent2 → error      / onError
 * - text    → onSurface
 * - dim     → onSurfaceVariant
 */
private fun buildColorScheme(colors: MotoColors, isLight: Boolean): ColorScheme =
    if (isLight) lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = colors.bg,
        onBackground = colors.text,
        surface = colors.panel,
        onSurface = colors.text,
        onSurfaceVariant = colors.dim,
        error = colors.accent2,
        onError = colors.onAccent2,
    ) else darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        background = colors.bg,
        onBackground = colors.text,
        surface = colors.panel,
        onSurface = colors.text,
        onSurfaceVariant = colors.dim,
        error = colors.accent2,
        onError = colors.onAccent2,
    )

// ─────────────────────────────────────────────────────────────────────────────
// Theme entry composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root theme wrapper for the MotoTracker app.
 *
 * Resolves [MotoColors] and [MotoShapes] for the chosen [theme] + [accent]
 * combination, provides the three [CompositionLocalProvider] values
 * ([LocalMotoColors], [LocalMotoTypography], [LocalMotoShapes]), and wraps a
 * [MaterialTheme] whose colour scheme is mapped from the tokens so that stock
 * M3 components inherit correct colours.
 *
 * @param theme  Visual mode (cockpit / grid / light). Defaults to [MotoTheme.COCKPIT].
 * @param accent Accent colour override. Defaults to [AccentColor.TEAL].
 * @param content Composable content rendered inside the theme.
 */
@Composable
fun MotoTrackerTheme(
    theme: MotoTheme = MotoTheme.COCKPIT,
    accent: AccentColor = AccentColor.TEAL,
    content: @Composable () -> Unit,
) {
    val motoColors = resolveMotoColors(theme, accent)
    val motoShapes = resolveMotoShapes(theme)
    val motoTypography = MotoTypography.Default

    val colorScheme = buildColorScheme(
        colors = motoColors,
        isLight = theme == MotoTheme.LIGHT,
    )

    CompositionLocalProvider(
        LocalMotoColors provides motoColors,
        LocalMotoTypography provides motoTypography,
        LocalMotoShapes provides motoShapes,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
