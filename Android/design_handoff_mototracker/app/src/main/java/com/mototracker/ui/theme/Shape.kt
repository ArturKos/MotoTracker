package com.mototracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radii used across the MotoTracker UI, resolved per [MotoTheme].
 *
 * @param cardCornerRadius Corner radius applied to card and panel surfaces.
 */
data class MotoShapes(val cardCornerRadius: Dp) {

    /** Convenience [RoundedCornerShape] built from [cardCornerRadius]. */
    val card: RoundedCornerShape get() = RoundedCornerShape(cardCornerRadius)
}

/**
 * Returns the [MotoShapes] specification for [theme].
 *
 * Per README design tokens:
 * - COCKPIT → 16 dp (rounded feel)
 * - GRID    →  5 dp (almost sharp, industrial grid look)
 * - LIGHT   → 14 dp (slightly softer than cockpit)
 */
fun resolveMotoShapes(theme: MotoTheme): MotoShapes = when (theme) {
    MotoTheme.COCKPIT -> MotoShapes(cardCornerRadius = 16.dp)
    MotoTheme.GRID    -> MotoShapes(cardCornerRadius = 5.dp)
    MotoTheme.LIGHT   -> MotoShapes(cardCornerRadius = 14.dp)
}
