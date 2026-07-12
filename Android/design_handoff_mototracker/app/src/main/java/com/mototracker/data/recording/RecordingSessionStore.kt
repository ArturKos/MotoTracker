package com.mototracker.data.recording

import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for the active recording session snapshot (B20).
 *
 * Implementations must be durable (DataStore / Room), not in-memory, so the
 * snapshot survives a process death and is available when the app relaunches.
 *
 * The snapshot is written on every GPS fix while recording and cleared when
 * the session is finished or discarded.
 */
interface RecordingSessionStore {

    /** Live stream of the most-recently saved snapshot, or null when none exists. */
    val snapshot: Flow<ActiveSessionSnapshot?>

    /**
     * Durably saves [s] as the current active-session snapshot.
     *
     * Overwrites any previously stored snapshot atomically.
     *
     * @param s Snapshot to persist.
     */
    suspend fun save(s: ActiveSessionSnapshot)

    /** Removes the stored snapshot. Call after the session is finished or discarded. */
    suspend fun clear()
}
