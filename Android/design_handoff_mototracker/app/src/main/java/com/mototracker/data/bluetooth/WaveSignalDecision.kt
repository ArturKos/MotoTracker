package com.mototracker.data.bluetooth

/**
 * Pure, Android-free decision helper that determines whether a haptic signal
 * should fire for a BLE encounter event (X3).
 *
 * Rules:
 * - Fire **only** on [EncounterEvent.Started] (once per new encounter).
 * - Never fire on [EncounterEvent.Extended] (encounter continuation).
 * - Honour the [signalEnabled] gate: when `false`, always return `false`.
 *
 * Extracted as an `object` so the logic is unit-testable without instantiating
 * [com.mototracker.service.RecordingService], mirroring [EncounterGap].
 */
object WaveSignalDecision {

    /**
     * Returns `true` when a haptic signal should be emitted for [event].
     *
     * @param event         The encounter event produced by [EncounterTracker.onSighting].
     * @param signalEnabled Whether the "signal waves" setting is enabled
     *                      ([com.mototracker.data.settings.AppSettings.signalWavesEnabled]).
     * @return `true` iff [event] is [EncounterEvent.Started] **and** [signalEnabled] is `true`.
     */
    fun shouldSignal(event: EncounterEvent, signalEnabled: Boolean): Boolean =
        event is EncounterEvent.Started && signalEnabled
}
