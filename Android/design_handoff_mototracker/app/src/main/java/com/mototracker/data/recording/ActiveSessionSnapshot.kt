package com.mototracker.data.recording

import com.mototracker.domain.recording.RecordingEngineState

/**
 * Durable snapshot of an in-progress recording session.
 *
 * Written to DataStore on every GPS fix and read back on app startup to detect
 * an unfinished session (B20 — process-death resume).
 *
 * @param engineState       Full accumulator state from [com.mototracker.domain.recording.RecordingEngine].
 * @param recordingStartMs  Epoch-millisecond timestamp captured when recording began.
 * @param bikeId            ID of the bike selected at recording start, or null if none was chosen.
 * @param paused            Whether the session was in the Paused phase when the snapshot was written.
 */
data class ActiveSessionSnapshot(
    val engineState: RecordingEngineState,
    val recordingStartMs: Long,
    val bikeId: String?,
    val paused: Boolean,
)
