package com.mototracker.ui.screens.record

/**
 * Sizing tokens for one Recording-screen layout breakpoint.
 *
 * All fields are plain [Int] (dp or sp) so this class is testable on the JVM without any Android
 * or Compose runtime dependency.
 *
 * @param speedFontSp        Font size (sp) for the current-speed readout in the SpeedTile.
 * @param compassDiameterDp  Outer diameter (dp) of the CompassDial rose.
 * @param bigNumberFontSp    Font size (sp) for secondary numeric tiles (distance, altitude, timers).
 * @param rowSpacingDp       Vertical gap (dp) between major tile rows.
 * @param controlButtonDp    Side length (dp) of primary icon buttons in the control strip.
 */
data class RecordSizing(
    val speedFontSp: Int,
    val compassDiameterDp: Int,
    val bigNumberFontSp: Int,
    val rowSpacingDp: Int,
    val controlButtonDp: Int,
)

/**
 * Derives [RecordSizing] layout tokens from the available screen height.
 *
 * Three breakpoints keep the full control column — chip row → Speed + Compass →
 * distance/altitude/fuel → fuel-tank → lean bar → timers → controls → hint — on a single screen
 * without scrolling on phones as compact as ~411 dp × ~683 dp usable height.
 *
 * The [forHeight] function is deterministic, pure, and monotonic non-decreasing in height.
 * It has no side effects and no dependency on the Android or Compose runtime, making it fully
 * testable on the plain JVM.
 */
object RecordLayoutSizing {

    /** Usable height (exclusive) for the compact breakpoint; below this the smallest tokens apply. */
    private const val COMPACT_MAX_DP = 560

    /** Usable height (inclusive) at which comfortable (full-size) tokens apply. */
    private const val TALL_MIN_DP = 720

    private val COMPACT = RecordSizing(
        speedFontSp       = 28,
        compassDiameterDp = 52,
        bigNumberFontSp   = 22,
        rowSpacingDp      = 3,
        controlButtonDp   = 48,
    )

    private val REGULAR = RecordSizing(
        speedFontSp       = 34,
        compassDiameterDp = 62,
        bigNumberFontSp   = 26,
        rowSpacingDp      = 5,
        controlButtonDp   = 52,
    )

    private val COMFORTABLE = RecordSizing(
        speedFontSp       = 40,
        compassDiameterDp = 72,
        bigNumberFontSp   = 30,
        rowSpacingDp      = 6,
        controlButtonDp   = 56,
    )

    /**
     * Returns the [RecordSizing] token bundle for [availableHeightDp].
     *
     * Breakpoints:
     * - **compact**     `availableHeightDp < 560`  — smallest tokens for short/dense screens
     * - **regular**     `560 ≤ availableHeightDp < 720` — medium tokens for typical Android phones
     * - **comfortable** `availableHeightDp ≥ 720`  — full-size (original) tokens for tall screens
     *
     * The returned tokens are strictly non-decreasing as [availableHeightDp] increases.
     * Extreme values (0 and [Int.MAX_VALUE]) are clamped to COMPACT and COMFORTABLE respectively.
     *
     * @param availableHeightDp Usable screen height in density-independent pixels.
     */
    fun forHeight(availableHeightDp: Int): RecordSizing = when {
        availableHeightDp < COMPACT_MAX_DP -> COMPACT
        availableHeightDp < TALL_MIN_DP    -> REGULAR
        else                               -> COMFORTABLE
    }
}
