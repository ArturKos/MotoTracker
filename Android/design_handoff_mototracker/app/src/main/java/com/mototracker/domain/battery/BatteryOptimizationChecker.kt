package com.mototracker.domain.battery

/**
 * Seam for querying whether the app is currently exempt from battery optimization.
 *
 * Android-specific implementation lives in [com.mototracker.data.battery.AndroidBatteryOptimizationChecker].
 * Tests supply a fake without any Android runtime dependency.
 */
interface BatteryOptimizationChecker {

    /**
     * Returns `true` when the app is currently ignoring battery optimizations
     * (i.e. is exempt from Doze and OEM background-kill policies).
     *
     * Always returns `true` on API < 23 where the API is unavailable.
     */
    fun isIgnoringBatteryOptimizations(): Boolean
}
