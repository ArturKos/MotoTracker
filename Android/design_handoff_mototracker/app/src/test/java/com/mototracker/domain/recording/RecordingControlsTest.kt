package com.mototracker.domain.recording

import com.mototracker.ui.screens.record.RecordingPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RecordingControls.forPhase] under the H2 contract.
 *
 * Contract:
 * - [RecordingPhase.Idle]      → [START] only; FILL_TO_FULL never appears.
 * - [RecordingPhase.Recording] → [PAUSE, STOP, FILL_TO_FULL] unconditionally.
 * - [RecordingPhase.Paused]    → [RESUME, STOP, FILL_TO_FULL] unconditionally.
 *
 * All tests are pure JUnit — no Android runtime, no Compose.
 */
class RecordingControlsTest {

    // ── Idle ─────────────────────────────────────────────────────────────────

    @Test
    fun `Idle returns only START`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Idle)
        assertEquals(listOf(RecordingControl.START), controls)
    }

    @Test
    fun `Idle never shows FILL_TO_FULL`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Idle)
        assertFalse("FILL_TO_FULL must never appear in Idle", controls.contains(RecordingControl.FILL_TO_FULL))
    }

    // ── Recording ────────────────────────────────────────────────────────────

    @Test
    fun `Recording returns PAUSE, STOP, FILL_TO_FULL`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Recording)
        assertEquals(listOf(RecordingControl.PAUSE, RecordingControl.STOP, RecordingControl.FILL_TO_FULL), controls)
    }

    @Test
    fun `Recording always includes FILL_TO_FULL`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Recording)
        assertTrue("FILL_TO_FULL must always appear during Recording (H2)", controls.contains(RecordingControl.FILL_TO_FULL))
    }

    @Test
    fun `START never appears in Recording`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Recording)
        assertFalse("START must not appear during Recording", controls.contains(RecordingControl.START))
    }

    // ── Paused ───────────────────────────────────────────────────────────────

    @Test
    fun `Paused returns RESUME, STOP, FILL_TO_FULL`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Paused)
        assertEquals(listOf(RecordingControl.RESUME, RecordingControl.STOP, RecordingControl.FILL_TO_FULL), controls)
    }

    @Test
    fun `Paused always includes FILL_TO_FULL`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Paused)
        assertTrue("FILL_TO_FULL must always appear during Paused (H2)", controls.contains(RecordingControl.FILL_TO_FULL))
    }

    @Test
    fun `Paused includes STOP`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Paused)
        assertTrue("STOP must appear during Paused", controls.contains(RecordingControl.STOP))
    }

    @Test
    fun `START never appears in Paused`() {
        val controls = RecordingControls.forPhase(RecordingPhase.Paused)
        assertFalse("START must not appear during Paused", controls.contains(RecordingControl.START))
    }
}
