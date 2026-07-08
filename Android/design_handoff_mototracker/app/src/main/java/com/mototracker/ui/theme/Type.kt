package com.mototracker.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mototracker.R

// ─────────────────────────────────────────────────────────────────────────────
// Font families (bundled as res/font/*.ttf — never fetched at runtime)
// ─────────────────────────────────────────────────────────────────────────────

/** Barlow — body copy and general UI text. */
val BarlowFamily: FontFamily = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

/**
 * Barlow Semi Condensed — screen titles and button labels.
 * Call [String.uppercase] at the call site for styles that require ALL CAPS.
 */
val BarlowSemiCondensedFamily: FontFamily = FontFamily(
    Font(R.font.barlow_semicondensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semicondensed_bold, FontWeight.Bold),
)

/** JetBrains Mono — all numeric / data readouts. */
val JetBrainsMonoFamily: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

// ─────────────────────────────────────────────────────────────────────────────
// Typography holder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Named text styles for the MotoTracker phone UI.
 *
 * Uppercase requirements for [screenTitle] and [label] must be applied at the
 * call site with [String.uppercase] — Compose TextStyle has no text-transform.
 *
 * @param timer          Large stopwatch / session timer. 52 sp, mono Bold.
 * @param bigCardNumber  Secondary numeric readout on a card. 30 sp, mono Medium.
 * @param recordSpeed    Current speed in the recording HUD. 40 sp, mono Bold.
 * @param screenTitle    Screen heading. 22 sp, semi-condensed Bold, ALL CAPS at call site.
 * @param routeTitle     Route / trip name in lists. 18 sp, semi-condensed SemiBold.
 * @param body           Standard body copy. 15 sp, Barlow Regular.
 * @param bodySmall      Captions and secondary body. 14 sp, Barlow Regular.
 * @param label          Small chip / tab label. 11 sp, semi-condensed SemiBold,
 *                       ALL CAPS + letterSpacing 0.4 sp at call site.
 */
data class MotoTypography(
    val timer: TextStyle,
    val bigCardNumber: TextStyle,
    val recordSpeed: TextStyle,
    val screenTitle: TextStyle,
    val routeTitle: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
) {
    companion object {
        /** Default instance using all README-specified sizes and the bundled font families. */
        val Default: MotoTypography = MotoTypography(
            timer = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 52.sp,
            ),
            bigCardNumber = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 30.sp,
            ),
            recordSpeed = TextStyle(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
            ),
            screenTitle = TextStyle(
                fontFamily = BarlowSemiCondensedFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            ),
            routeTitle = TextStyle(
                fontFamily = BarlowSemiCondensedFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            ),
            body = TextStyle(
                fontFamily = BarlowFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
            ),
            bodySmall = TextStyle(
                fontFamily = BarlowFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            ),
            label = TextStyle(
                fontFamily = BarlowSemiCondensedFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
            ),
        )
    }
}
