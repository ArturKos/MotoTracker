package com.mototracker.ui.screens.record

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the pure [requestedOrientationFor] mapping function. */
class ScreenOrientationTest {

    @Test
    fun `Recording phase maps to LockedPortrait`() {
        assertEquals(
            RecordOrientation.LockedPortrait,
            requestedOrientationFor(RecordingPhase.Recording),
        )
    }

    @Test
    fun `Paused phase maps to LockedPortrait`() {
        assertEquals(
            RecordOrientation.LockedPortrait,
            requestedOrientationFor(RecordingPhase.Paused),
        )
    }

    @Test
    fun `Idle phase maps to Unspecified`() {
        assertEquals(
            RecordOrientation.Unspecified,
            requestedOrientationFor(RecordingPhase.Idle),
        )
    }
}
