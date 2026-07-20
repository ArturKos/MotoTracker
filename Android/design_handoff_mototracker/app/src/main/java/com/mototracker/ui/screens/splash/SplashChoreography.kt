package com.mototracker.ui.screens.splash

/**
 * Immutable per-layer transform snapshot for the splash entrance animation.
 *
 * [translateXFrac] is a **fraction of the composable's width** — AE2 multiplies it
 * by the measured pixel width to obtain the actual horizontal offset.  All other
 * fields are self-contained values ready for use in a Compose `graphicsLayer`.
 *
 * Default values represent the fully-visible, at-rest state so that layers that
 * do not animate a particular property keep their natural appearance.
 */
data class LayerState(
    val alpha: Float = 1f,
    val translateXFrac: Float = 0f,
    val translateYPx: Float = 0f,
    val rotationDeg: Float = 0f,
    val scale: Float = 1f,
)

/**
 * Snapshot of all animated splash layer states at a single point in time.
 *
 * Obtain an instance via [SplashChoreography.stateAt] and apply each field to
 * the corresponding `graphicsLayer` modifier inside SplashScreen (AE2).
 *
 * Note: `beam` rides with [bike] and `tagline` rides with [wordmark] — AE2 can
 * expose them as separate composable parameters but should drive their transforms
 * directly from these two fields.
 */
data class SplashLayers(
    val bike: LayerState,
    val wheelRear: LayerState,
    val wheelFront: LayerState,
    val trail: LayerState,
    val pin: LayerState,
    val wordmark: LayerState,
    val mountains: LayerState,
)

/**
 * Pure timing brain for the MotoTracker splash entrance animation.
 *
 * Contains **no Android imports** — all computation is plain Kotlin/JVM so the
 * class runs under `testDebugUnitTest` without Robolectric.
 *
 * Full timeline ([TOTAL_MS] = 2200 ms):
 * - **0–800 ms** — Bike + wheels slide in from the left.  [bike].translateXFrac
 *   travels −0.52 → 0 (eased).  Both wheels spin 0 → 720° (linear) and track
 *   the bike's horizontal position.
 * - **600–1300 ms** — Trail fades and scales in (eased).
 * - **1200–1600 ms** — Pin drops in from above, fades and scales in (eased).
 * - **1500–2200 ms** — Wordmark fades and scales up (eased).
 * - **always** — Mountains remain fully opaque at all times.
 *
 * Before a layer's window it holds its start state; after its window it is
 * locked at the end state.  Call [stateAt] once per animation frame (driven by
 * a single `Animatable<Long>` in AE2) to obtain the [SplashLayers] snapshot.
 */
object SplashChoreography {

    /** Total animation duration in milliseconds. */
    const val TOTAL_MS = 2200L

    // ── Phase window bounds (ms) — adjust here to re-tune timing. ──────────
    private const val BIKE_START_MS = 0L
    private const val BIKE_END_MS = 800L
    private const val TRAIL_START_MS = 600L
    private const val TRAIL_END_MS = 1300L
    private const val PIN_START_MS = 1200L
    private const val PIN_END_MS = 1600L
    private const val WORDMARK_START_MS = 1500L
    private const val WORDMARK_END_MS = 2200L

    // ── Layer start values ──────────────────────────────────────────────────
    private const val BIKE_START_X_FRAC = -0.52f
    private const val WHEEL_MAX_ROTATION_DEG = 720f
    private const val TRAIL_START_SCALE = 0.96f
    private const val PIN_START_Y_PX = -24f
    private const val WORDMARK_START_SCALE = 0.9f

    /**
     * Returns the [SplashLayers] for [elapsedMs], clamped to [0, TOTAL_MS].
     *
     * Before a layer's animation window the layer holds its start state;
     * inside the window values interpolate via [easeFastOutSlowIn] (except wheel
     * rotation which uses linear progress); after the window the layer is locked
     * at its end state.
     */
    fun stateAt(elapsedMs: Long): SplashLayers {
        val t = elapsedMs.coerceIn(0L, TOTAL_MS)

        // Bike + wheels (0–800 ms)
        val bikeLinear = windowProgress(t, BIKE_START_MS, BIKE_END_MS)
        val bikeEased = easeFastOutSlowIn(bikeLinear)
        val bikeXFrac = lerp(BIKE_START_X_FRAC, 0f, bikeEased)
        val wheelRotation = lerp(0f, WHEEL_MAX_ROTATION_DEG, bikeLinear) // linear per spec

        val bike = LayerState(translateXFrac = bikeXFrac)
        val wheelRear = LayerState(translateXFrac = bikeXFrac, rotationDeg = wheelRotation)
        val wheelFront = LayerState(translateXFrac = bikeXFrac, rotationDeg = wheelRotation)

        // Trail (600–1300 ms)
        val trailP = easeFastOutSlowIn(windowProgress(t, TRAIL_START_MS, TRAIL_END_MS))
        val trail = LayerState(
            alpha = lerp(0f, 1f, trailP),
            scale = lerp(TRAIL_START_SCALE, 1f, trailP),
        )

        // Pin (1200–1600 ms)
        val pinP = easeFastOutSlowIn(windowProgress(t, PIN_START_MS, PIN_END_MS))
        val pin = LayerState(
            alpha = lerp(0f, 1f, pinP),
            translateYPx = lerp(PIN_START_Y_PX, 0f, pinP),
            scale = lerp(0f, 1f, pinP),
        )

        // Wordmark (1500–2200 ms)
        val wordmarkP = easeFastOutSlowIn(windowProgress(t, WORDMARK_START_MS, WORDMARK_END_MS))
        val wordmark = LayerState(
            alpha = lerp(0f, 1f, wordmarkP),
            scale = lerp(WORDMARK_START_SCALE, 1f, wordmarkP),
        )

        val mountains = LayerState(alpha = 1f)

        return SplashLayers(
            bike = bike,
            wheelRear = wheelRear,
            wheelFront = wheelFront,
            trail = trail,
            pin = pin,
            wordmark = wordmark,
            mountains = mountains,
        )
    }

    /**
     * Returns linear progress in [0, 1] for [t] within [[startMs], [endMs]].
     *
     * Returns 0 when [t] ≤ [startMs] (before the window) and 1 when [t] ≥ [endMs]
     * (window complete), so callers lock naturally at start/end states.
     */
    private fun windowProgress(t: Long, startMs: Long, endMs: Long): Float {
        if (t <= startMs) return 0f
        if (t >= endMs) return 1f
        return (t - startMs).toFloat() / (endMs - startMs).toFloat()
    }

    /**
     * Approximate Material FastOutSlowIn easing (cubic ease-out).
     *
     * Starts with high velocity and decelerates toward [p] = 1, approximating
     * `cubic-bezier(0.4, 0, 0.2, 1)`.  Input is implicitly bounded by [0, 1]
     * via [windowProgress]; the `coerceIn` guard is a defensive belt-and-braces.
     */
    private fun easeFastOutSlowIn(p: Float): Float {
        val c = p.coerceIn(0f, 1f)
        val q = 1f - c
        return 1f - q * q * q
    }

    /** Linear interpolation from [a] to [b] by factor [t] in [0, 1]. */
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
