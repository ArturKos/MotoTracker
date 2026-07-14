package com.mototracker.domain.recording

import com.mototracker.ui.screens.record.RecordingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RecordingControls.forPhase].
 *
 * Verifies the exact control list for each [RecordingPhase], with and without a fuel tank.
 * All tests are pure JUnit — no Android runtime, no Compose.
 */
class RecordingControlsTest {

    // ── Idle ─────────────────────────────────────────────────────────────────

    @Test
    fun `Idle without tank returns only START`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Idle, hasFuelTank = false)
        assertEquals(listOf(RecordingControl.START), controls)
    }

    @Test
    fun `Idle with tank returns only START — no FILL_TO_FULL in Idle`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Idle, hasFuelTank = true)
        assertEquals(listOf(RecordingControl.START), controls)
        assertFalse("FILL_TO_FULL must never appear in Idle", controls.contains(RecordingControl.FILL_TO_FULL))
    }

    // ── Recording ────────────────────────────────────────────────────────────

    @Test
    fun `Recording without tank returns PAUSE and STOP`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Recording, hasFuelTank = false)
        assertEquals(listOf(RecordingControl.PAUSE, RecordingControl.STOP), controls)
    }

    @Test
    fun `Recording with tank returns PAUSE, STOP, FILL_TO_FULL`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Recording, hasFuelTank = true)
        assertEquals(listOf(RecordingControl.PAUSE, RecordingControl.STOP, RecordingControl.FILL_TO_FULL), controls)
    }

    @Test
    fun `Recording with tank includes FILL_TO_FULL`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Recording, hasFuelTank = true)
        assertTrue("FILL_TO_FULL must appear during Recording when hasFuelTank", controls.contains(RecordingControl.FILL_TO_FULL))
    }

    @Test
    fun `Recording without tank excludes FILL_TO_FULL`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Recording, hasFuelTank = false)
        assertFalse("FILL_TO_FULL must not appear during Recording without tank", controls.contains(RecordingControl.FILL_TO_FULL))
    }

    // ── Paused ───────────────────────────────────────────────────────────────

    @Test
    fun `Paused without tank returns RESUME and STOP`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Paused, hasFuelTank = false)
        assertEquals(listOf(RecordingControl.RESUME, RecordingControl.STOP), controls)
    }

    @Test
    fun `Paused with tank returns RESUME, STOP, FILL_TO_FULL`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Paused, hasFuelTank = true)
        assertEquals(listOf(RecordingControl.RESUME, RecordingControl.STOP, RecordingControl.FILL_TO_FULL), controls)
    }

    @Test
    fun `Paused with tank includes FILL_TO_FULL`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Paused, hasFuelTank = true)
        assertTrue("FILL_TO_FULL must appear during Paused when hasFuelTank", controls.contains(RecordingControl.FILL_TO_FULL))
    }

    @Test
    fun `Paused without tank excludes FILL_TO_FULL`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Paused, hasFuelTank = false)
        assertFalse("FILL_TO_FULL must not appear during Paused without tank", controls.contains(RecordingControl.FILL_TO_FULL))
    }

    // ── No START outside Idle ─────────────────────────────────────────────────

    @Test
    fun `START never appears in Recording`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Recording, hasFuelTank = true)
        assertFalse("START must not appear during Recording", controls.contains(RecordingControl.START))
    }

    @Test
    fun `START never appears in Paused`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Paused, hasFuelTank = true)
        assertFalse("START must not appear during Paused", controls.contains(RecordingControl.START))
    }
}
