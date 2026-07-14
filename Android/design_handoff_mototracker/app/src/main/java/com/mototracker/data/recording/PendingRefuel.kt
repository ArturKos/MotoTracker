package com.mototracker.data.recording

/**
 * An in-memory refuel event recorded during an active session.
 *
 * Buffered in [com.mototracker.ui.screens.record.RecordingViewModel] while the
 * route row does not yet exist (it is only written at session end). Persisted in
 * [ActiveSessionSnapshot] so that a process-death resume restores the buffer.
 *
 * @param epochMs   Wall-clock time of the refuel in milliseconds since epoch.
 * @param litres    Volume of fuel added in litres.
 * @param pricePerL Price per litre in the user's currency at the time of the event.
 */
data class PendingRefuel(
    val epochMs: Long,
    val litres: Double,
    val pricePerL: Double,
)
