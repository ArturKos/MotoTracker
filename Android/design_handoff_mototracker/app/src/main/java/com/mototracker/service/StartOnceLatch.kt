package com.mototracker.service

import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-shot latch: [tryAcquire] returns `true` exactly once per instance, `false` afterwards.
 *
 * Used by [RecordingService] so that repeated `onStartCommand` calls within one service
 * lifetime do not launch duplicate background workers.
 */
class StartOnceLatch {
    private val acquired = AtomicBoolean(false)

    /** Returns `true` on the first call only; thread-safe. */
    fun tryAcquire(): Boolean = acquired.compareAndSet(false, true)
}
