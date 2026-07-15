package com.mototracker.domain.recording

import com.mototracker.ui.screens.record.RecordingPhase

/**
 * A discrete user-facing action in the recording control strip.
 *
 * Instances are pure values — no Android or Compose dependencies — so the
 * phase → control-set logic can be unit-tested without a device.
 */
enum class RecordingControl {
    /** Starts a new recording session. Shown only in [RecordingPhase.Idle]. */
    START,

    /** Pauses the active recording session. Shown only in [RecordingPhase.Recording]. */
    PAUSE,

    /** Resumes a paused session. Shown only in [RecordingPhase.Paused]. */
    RESUME,

    /** Finishes and saves the current session. Shown in [RecordingPhase.Recording] and [RecordingPhase.Paused]. */
    STOP,

    /**
     * Logs a fill-to-full refuel event.
     *
     * Shown in [RecordingPhase.Recording] and [RecordingPhase.Paused] unconditionally — no longer
     * dependent on whether the current bike has a tank capacity configured. Never shown in
     * [RecordingPhase.Idle].
     */
    FILL_TO_FULL,
}

/**
 * Pure, Android-free model for deriving the control-strip contents from a recording phase.
 *
 * All logic is in [forPhase]; no state is held. Unit-testable with plain JUnit.
 */
object RecordingControls {

    /**
     * Returns the ordered list of controls to display for a given [phase].
     *
     * - [RecordingPhase.Idle]      → [[RecordingControl.START]] (never includes FILL_TO_FULL)
     * - [RecordingPhase.Recording] → [PAUSE, STOP, FILL_TO_FULL] (unconditional)
     * - [RecordingPhase.Paused]    → [RESUME, STOP, FILL_TO_FULL] (unconditional)
     *
     * FILL_TO_FULL is always shown during Recording and Paused regardless of tank configuration.
     * The refuel dialog handles the no-tank case with an empty, editable litres field.
     *
     * @param phase Current recording lifecycle phase.
     * @return Ordered control list; rendered left-to-right in the control strip.
     */
    fun forPhase(phase: RecordingPhase): List<RecordingControl> = when (phase) {
        RecordingPhase.Idle -> listOf(RecordingControl.START)
        RecordingPhase.Recording -> listOf(RecordingControl.PAUSE, RecordingControl.STOP, RecordingControl.FILL_TO_FULL)
        RecordingPhase.Paused -> listOf(RecordingControl.RESUME, RecordingControl.STOP, RecordingControl.FILL_TO_FULL)
    }
}
