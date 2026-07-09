package com.mototracker.core.time

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraction over wall-clock time so that callers can be tested with a fixed timestamp.
 *
 * Production code should always obtain the current time through this interface rather than
 * calling [System.currentTimeMillis] directly.
 */
interface TimeProvider {
    /** Returns the current wall-clock time in milliseconds since the Unix epoch. */
    fun nowEpochMs(): Long
}

/**
 * Production implementation of [TimeProvider] delegating to [System.currentTimeMillis].
 */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}
