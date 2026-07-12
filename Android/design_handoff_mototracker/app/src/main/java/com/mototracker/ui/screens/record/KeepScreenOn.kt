package com.mototracker.ui.screens.record

/**
 * Decides whether the FLAG_KEEP_SCREEN_ON window flag should be active.
 *
 * The screen is kept awake only while a ride is actively in progress — i.e. the phase is
 * [RecordingPhase.Recording] or [RecordingPhase.Paused] — to conserve battery when the
 * recording screen is open but no ride has started ([RecordingPhase.Idle]).
 *
 * Kept framework-free so it can be tested in a plain JVM context without Android dependencies.
 *
 * @param keepScreenOn User preference: whether the display should stay on during a ride.
 * @param phase        Current recording lifecycle phase.
 * @return `true` when the window flag should be applied; `false` otherwise.
 */
fun shouldKeepScreenOn(keepScreenOn: Boolean, phase: RecordingPhase): Boolean =
    keepScreenOn && (phase == RecordingPhase.Recording || phase == RecordingPhase.Paused)
