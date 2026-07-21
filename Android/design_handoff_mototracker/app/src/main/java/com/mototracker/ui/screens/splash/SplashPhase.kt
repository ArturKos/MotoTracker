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
 * 1. **Animation-driven gate** — dismiss when [ready] AND [animationComplete] are both true.
 *    The animation-complete signal replaces the old wall-clock minimum: the splash stays visible
 *    until the frame-delta-clamped entrance animation genuinely reaches
 *    [SplashChoreography.TOTAL_MS], regardless of wall-clock elapsed time.
 * 2. **Hard timeout** — force-dismiss when [elapsedMs] ≥ [maxDurationMs] regardless of [ready]
 *    or [animationComplete]. Prevents an infinite hang if the startup flow stalls.
 *
 * All inputs are passed in so the gate is pure and trivially unit-testable without a device or
 * system-clock dependency.
 */
object SplashGate {

    /** Hard cap: the splash is force-dismissed after this many milliseconds. */
    const val DEFAULT_MAX_MS: Long = 4_000L

    /**
     * Returns `true` when the splash should be dismissed.
     *
     * @param ready             Whether the app's startup decision is resolved.
     * @param animationComplete Whether the entrance animation has genuinely reached completion.
     * @param elapsedMs         Milliseconds elapsed since the splash was first shown.
     * @param maxDurationMs     Hard timeout; defaults to [DEFAULT_MAX_MS].
     */
    fun shouldDismiss(
        ready: Boolean,
        animationComplete: Boolean,
        elapsedMs: Long,
        maxDurationMs: Long = DEFAULT_MAX_MS,
    ): Boolean = (ready && animationComplete) || elapsedMs >= maxDurationMs
}
