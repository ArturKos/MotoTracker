package com.mototracker.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the [SyncRepository] auto-drain exactly once per process, in a process-lived scope.
 *
 * Called from every process entry point that can produce or need synced routes — the phone UI
 * ([com.mototracker.MainActivity]) and Android Auto
 * ([com.mototracker.car.MotoTrackerCarAppService]) — so queued routes upload automatically
 * without the rider having to tap "Sync now".
 *
 * @param syncRepository Repository whose [SyncRepository.start] loop is launched.
 * @param scope          Process-lived scope owning the loop; a fresh supervisor scope in production.
 */
@Singleton
class SyncAutoStarter internal constructor(
    private val syncRepository: SyncRepository,
    private val scope: CoroutineScope,
) {

    /** Production constructor: the loop runs on [Dispatchers.Default] for the process lifetime. */
    @Inject
    constructor(syncRepository: SyncRepository) :
        this(syncRepository, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val started = AtomicBoolean(false)

    /** Launches the auto-drain on the first call; later calls are no-ops. */
    fun ensureStarted() {
        if (started.compareAndSet(false, true)) syncRepository.start(scope)
    }
}
