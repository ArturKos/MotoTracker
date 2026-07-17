package com.mototracker.domain.battery

/**
 * Pure gate deciding whether to prompt the user for battery-optimization exemption.
 *
 * No Android types; fully unit-testable in isolation.
 */
object BatteryOptimizationGate {

    /**
     * Returns `true` when the battery-optimization prompt should be shown.
     *
     * @param isExempt        Whether the app is already exempt from battery optimization.
     * @param promptDismissed Whether the user has previously dismissed the prompt.
     */
    fun shouldPrompt(isExempt: Boolean, promptDismissed: Boolean): Boolean =
        !isExempt && !promptDismissed
}
