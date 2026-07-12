package com.mototracker.ui.screens.record

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure [shouldKeepScreenOn] decision function. */
class KeepScreenOnTest {

    // ── Setting OFF — should never return true regardless of phase ─────────────

    @Test
    fun `setting off + Idle returns false`() {
        assertFalse(shouldKeepScreenOn(keepScreenOn = false, phase = RecordingPhase.Idle))
    }

    @Test
    fun `setting off + Recording returns false`() {
        assertFalse(shouldKeepScreenOn(keepScreenOn = false, phase = RecordingPhase.Recording))
    }

    @Test
    fun `setting off + Paused returns false`() {
        assertFalse(shouldKeepScreenOn(keepScreenOn = false, phase = RecordingPhase.Paused))
    }

    // ── Setting ON — true only while a ride is in progress ────────────────────

    @Test
    fun `setting on + Idle returns false`() {
        assertFalse(shouldKeepScreenOn(keepScreenOn = true, phase = RecordingPhase.Idle))
    }

    @Test
    fun `setting on + Recording returns true`() {
        assertTrue(shouldKeepScreenOn(keepScreenOn = true, phase = RecordingPhase.Recording))
    }

    @Test
    fun `setting on + Paused returns true`() {
        assertTrue(shouldKeepScreenOn(keepScreenOn = true, phase = RecordingPhase.Paused))
    }
}
