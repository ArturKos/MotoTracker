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
     * Shown in [RecordingPhase.Recording] and [RecordingPhase.Paused] when the current bike has a
     * tank capacity configured ([RecordingControls.forPhase] hasFuelTank = true).
     * Never shown in [RecordingPhase.Idle].
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
     * Returns the ordered list of controls to display for a given [phase] and fuel-tank presence.
     *
     * - [RecordingPhase.Idle]      → [[RecordingControl.START]] (never includes FILL_TO_FULL)
     * - [RecordingPhase.Recording] → [PAUSE, STOP] + [FILL_TO_FULL] when [hasFuelTank] is true
     * - [RecordingPhase.Paused]    → [RESUME, STOP] + [FILL_TO_FULL] when [hasFuelTank] is true
     *
     * @param phase       Current recording lifecycle phase.
     * @param hasFuelTank True when the current bike has a tank capacity configured.
     * @return Ordered control list; rendered left-to-right in the control strip.
     */
    fun forPhase(phase: RecordingPhase, hasFuelTank: Boolean): List<RecordingControl> = when (phase) {
        RecordingPhase.Idle -> listOf(RecordingControl.START)
        RecordingPhase.Recording -> buildList {
            add(RecordingControl.PAUSE)
            add(RecordingControl.STOP)
            if (hasFuelTank) add(RecordingControl.FILL_TO_FULL)
        }
        RecordingPhase.Paused -> buildList {
            add(RecordingControl.RESUME)
            add(RecordingControl.STOP)
            if (hasFuelTank) add(RecordingControl.FILL_TO_FULL)
        }
    }
}
