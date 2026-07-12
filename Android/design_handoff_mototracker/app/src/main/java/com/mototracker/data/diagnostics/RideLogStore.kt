package com.mototracker.data.diagnostics

import java.io.File

/**
 * Read-only access to the ride-log directory.
 *
 * All operations MUST NOT throw; they return null / 0 on any I/O error or when
 * external storage is unavailable.  Implementations are safe to call from any thread.
 */
interface RideLogStore {

    /**
     * Returns the newest ride-log file (by last-modified time), or null if the
     * directory is empty or unavailable.
     */
    fun latestLog(): File?

    /**
     * Returns the total size in bytes of all ride-log files, or 0 if the directory
     * is empty or unavailable.
     */
    fun totalBytes(): Long

    /**
     * Deletes every file in the ride-logs directory.
     *
     * @return the number of files deleted; 0 if the directory was already empty or
     *         unavailable.  Never throws.
     */
    fun clear(): Int
}
