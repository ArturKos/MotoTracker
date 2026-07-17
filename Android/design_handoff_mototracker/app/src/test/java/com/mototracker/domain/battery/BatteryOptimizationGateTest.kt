package com.mototracker.domain.battery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryOptimizationGateTest {

    @Test
    fun `exempt app never prompts`() {
        assertFalse(BatteryOptimizationGate.shouldPrompt(isExempt = true, promptDismissed = false))
    }

    @Test
    fun `not exempt and not dismissed prompts`() {
        assertTrue(BatteryOptimizationGate.shouldPrompt(isExempt = false, promptDismissed = false))
    }

    @Test
    fun `dismissed flag suppresses prompt even when not exempt`() {
        assertFalse(BatteryOptimizationGate.shouldPrompt(isExempt = false, promptDismissed = true))
    }
}
