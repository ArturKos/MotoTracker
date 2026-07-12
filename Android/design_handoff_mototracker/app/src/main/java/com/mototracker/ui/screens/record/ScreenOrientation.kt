package com.mototracker.ui.screens.record

/**
 * Orientation lock policy for the Recording screen.
 *
 * Kept framework-free so it can be tested in a plain JVM context without Android dependencies.
 */
enum class RecordOrientation {
    /** Lock the display to portrait to keep accelerometer axes stable for lean-angle math. */
    LockedPortrait,

    /** Allow the system to rotate freely (default app behaviour outside of active recording). */
    Unspecified,
}

/**
 * Maps a [RecordingPhase] to the orientation policy required by that phase.
 *
 * Portrait is required whenever the device is actively capturing sensor data
 * ([RecordingPhase.Recording] and [RecordingPhase.Paused]) so that the accelerometer axes
 * remain consistent with the lean-angle calibration baseline.  The lock is released when
 * the phase returns to [RecordingPhase.Idle].
 *
 * @param phase The current recording lifecycle phase.
 * @return [RecordOrientation.LockedPortrait] while recording or paused;
 *         [RecordOrientation.Unspecified] when idle.
 */
fun requestedOrientationFor(phase: RecordingPhase): RecordOrientation = when (phase) {
    RecordingPhase.Recording, RecordingPhase.Paused -> RecordOrientation.LockedPortrait
    RecordingPhase.Idle -> RecordOrientation.Unspecified
}
