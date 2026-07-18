package com.mototracker.data.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveSignalDecisionTest {

    private val started = EncounterEvent.Started(shortId = "AA11", atMs = 1000L)
    private val extended = EncounterEvent.Extended(shortId = "AA11", atMs = 2000L)

    @Test
    fun `Started + signal enabled returns true`() {
        assertTrue(WaveSignalDecision.shouldSignal(started, signalEnabled = true))
    }

    @Test
    fun `Extended + signal enabled returns false`() {
        assertFalse(WaveSignalDecision.shouldSignal(extended, signalEnabled = true))
    }

    @Test
    fun `Started + signal disabled returns false`() {
        assertFalse(WaveSignalDecision.shouldSignal(started, signalEnabled = false))
    }

    @Test
    fun `Extended + signal disabled returns false`() {
        assertFalse(WaveSignalDecision.shouldSignal(extended, signalEnabled = false))
    }
}
