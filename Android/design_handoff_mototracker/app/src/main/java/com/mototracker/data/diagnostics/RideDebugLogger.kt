package com.mototracker.data.diagnostics

/**
 * Seam for writing diagnostic ride logs.
 *
 * Every method MUST be non-blocking (use a channel internally) and MUST NOT throw.
 * When diagnostics are disabled the implementation MUST be a true no-op: no file is
 * created, no channel work is done, and no CPU cycles are wasted beyond the guard check.
 *
 * [RecordingViewModel] (B10) depends on this interface, not on [FileRideDebugLogger],
 * so tests can supply a fake without touching the filesystem.
 */
interface RideDebugLogger {

    /**
     * Signals that a ride has begun.
     *
     * If diagnostics are enabled, opens a new log file for this ride session.
     * If disabled, this is a no-op.
     */
    fun beginRide()

    /**
     * Appends a diagnostic line to the current ride log.
     *
     * The line is enqueued on an internal channel and written asynchronously on
     * [kotlinx.coroutines.Dispatchers.IO] — this call never blocks the caller.
     *
     * @param tag     Short identifier of the subsystem emitting the message (e.g. "GPS", "LEAN").
     * @param message Human-readable diagnostic content.
     */
    fun log(tag: String, message: String)

    /**
     * Signals that the current ride has ended.
     *
     * Enqueues a close event on the internal channel; the writer flushes and closes
     * the log file asynchronously. If no ride is active or diagnostics are disabled,
     * this is a no-op.
     */
    fun endRide()
}
