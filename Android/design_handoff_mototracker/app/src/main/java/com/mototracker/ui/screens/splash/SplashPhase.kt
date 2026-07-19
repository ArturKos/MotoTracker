package com.mototracker.ui.screens.splash

import androidx.annotation.StringRes
import com.mototracker.R

/** Discrete step the app is in while the startup flow is executing. */
enum class SplashPhase { INITIALIZING, MIGRATING_DB, LOADING_ROUTES, READY }

/**
 * Pure mapping from a [SplashPhase] to its string-resource label id.
 *
 * Stateless so the mapping can be exercised in unit tests without any Android
 * framework or Compose dependency.
 */
object SplashStatus {

    /**
     * Returns the [StringRes] id for the status label shown during [phase].
     *
     * [SplashPhase.READY] reuses [R.string.splash_status_loading_routes] because
     * the transition to the ready state is almost instant — a dedicated "ready"
     * label would be visible for only a few frames.
     */
    @StringRes
    fun labelFor(phase: SplashPhase): Int = when (phase) {
        SplashPhase.INITIALIZING   -> R.string.splash_status_initializing
        SplashPhase.MIGRATING_DB   -> R.string.splash_status_migrating_db
        SplashPhase.LOADING_ROUTES -> R.string.splash_status_loading_routes
        SplashPhase.READY          -> R.string.splash_status_loading_routes
    }
}

/**
 * Pure gate that decides when the splash screen should be dismissed.
 *
 * Two rules:
 * 1. **Minimum duration** — dismiss only when [ready] AND [elapsedMs] ≥ [minDurationMs].
 *    Guarantees the splash is visible long enough to be seen even if startup resolves
 *    instantly (e.g. warm-cache launch).
 * 2. **Hard timeout** — force-dismiss when [elapsedMs] ≥ [maxDurationMs] regardless of
 *    [ready]. Prevents an infinite hang if the startup flow stalls.
 *
 * All clock values are passed in so the gate is pure and trivially unit-testable without
 * a device or system-clock dependency.
 */
object SplashGate {

    /** Minimum time the splash stays visible regardless of startup speed. */
    const val DEFAULT_MIN_MS: Long = 700L

    /** Hard cap: the splash is force-dismissed after this many milliseconds. */
    const val DEFAULT_MAX_MS: Long = 4_000L

    /**
     * Total duration of the [splash_hero_avd] animation (≈ 2.1 s).
     *
     * The call site in [MainActivity] passes this as [minDurationMs] so the splash
     * is never dismissed before the AVD has finished playing — even when the app
     * finishes initialising faster than the animation completes.
     */
    const val AVD_DURATION_MS: Long = 2_000L

    /**
     * Returns `true` when the splash should be dismissed.
     *
     * @param ready          Whether the app's startup decision is resolved.
     * @param elapsedMs      Milliseconds elapsed since the splash was first shown.
     * @param minDurationMs  Minimum visibility window; defaults to [DEFAULT_MIN_MS].
     * @param maxDurationMs  Hard timeout; defaults to [DEFAULT_MAX_MS].
     */
    fun shouldDismiss(
        ready: Boolean,
        elapsedMs: Long,
        minDurationMs: Long = DEFAULT_MIN_MS,
        maxDurationMs: Long = DEFAULT_MAX_MS,
    ): Boolean = (ready && elapsedMs >= minDurationMs) || elapsedMs >= maxDurationMs
}
