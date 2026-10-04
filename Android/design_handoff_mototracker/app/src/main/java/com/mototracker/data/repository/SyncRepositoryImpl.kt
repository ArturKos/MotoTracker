package com.mototracker.data.repository

import com.mototracker.core.time.TimeProvider
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.dao.SyncQueueDao
import com.mototracker.data.local.entity.SyncQueueEntity
import com.mototracker.data.local.entity.SyncQueueState
import com.mototracker.data.model.mapper.toDomain
import com.mototracker.data.network.GpStrackClient
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.SyncRetryPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [SyncRepository].
 *
 * The auto-drain loop (started via [start]) combines [NetworkMonitor.isOnline] with
 * the current [AppSettingsSource.settings] and drains due queue entries whenever
 * `isOnline && !noInternet && syncEnabled`.
 *
 * @param syncQueueDao   DAO for the outbound sync queue.
 * @param routeDao       DAO for reading and updating route sync status.
 * @param settingsSource Read-only stream of persisted app settings.
 * @param client         HTTP client that uploads a route to the GPStrack server.
 * @param networkMonitor Observes device connectivity.
 * @param timeProvider   Wall-clock source for retry timestamps (injectable for tests).
 */
@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val syncQueueDao: SyncQueueDao,
    private val routeDao: RouteDao,
    private val settingsSource: AppSettingsSource,
    private val client: GpStrackClient,
    private val networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
) : SyncRepository {

    /**
     * Serialises [drain] passes. Connectivity changes, settings changes and manual
     * "sync now" can all trigger a drain at the same moment; without this, two passes could
     * read the same pending snapshot and upload the same entry twice.
     */
    private val drainMutex = Mutex()

    /** Fired by [enqueue] so a newly queued route is uploaded without waiting for a connectivity change. */
    private val enqueued = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Wakes the retry timer after every drain pass so it re-reads the earliest back-off deadline. */
    private val rearmRetry = Channel<Unit>(Channel.CONFLATED)

    override val pendingCount: Flow<Int> = syncQueueDao.getPendingCount()

    /**
     * Inserts or resets the sync queue entry for [routeId] to [SyncQueueState.PENDING].
     *
     * If an entry already exists for this route (e.g. a previous FAILED attempt), it is
     * updated in place so the queue never holds duplicate entries for the same route.
     */
    override suspend fun enqueue(routeId: String) {
        val existing = syncQueueDao.findByRouteId(routeId)
        syncQueueDao.upsert(
            SyncQueueEntity(
                id = existing?.id ?: 0L,
                routeId = routeId,
                state = SyncQueueState.PENDING,
                attemptCount = 0,
                lastAttemptEpochMs = null,
                nextRetryEpochMs = null,
                lastError = null,
            )
        )
        enqueued.tryEmit(Unit)
    }

    /**
     * Drains all due entries regardless of the [AppSettings.syncEnabled] setting.
     *
     * Returns 0 immediately if [AppSettings.noInternet] is `true` (no network calls permitted).
     */
    override suspend fun syncNow(): Int {
        val settings = settingsSource.settings.first()
        if (settings.noInternet) return 0
        return drain(settings.serverAddress)
    }

    /**
     * Launches the background auto-drain coroutines in [scope].
     *
     * Three tasks are started using [Dispatchers.Unconfined]:
     * 1. A one-shot initial [tryDrain] that runs synchronously on the calling thread, so
     *    it fires immediately even when [scope] is backed by a paused test dispatcher.
     * 2. A continuous watcher on [NetworkMonitor.isOnline] that re-evaluates on every
     *    connectivity change after the first emission (drop(1) avoids double-drain with #1).
     * 3. A continuous watcher on [AppSettingsSource.settings] that re-evaluates on every
     *    settings change after the first emission.
     *
     * Two more run on [scope]'s own dispatcher:
     * 4. A watcher on [enqueue] calls, so a route saved while online is uploaded right away.
     * 5. A retry timer that sleeps until the earliest FAILED entry's back-off deadline and then
     *    re-evaluates; without it a failed upload waited for the next connectivity change.
     *
     * [Dispatchers.Unconfined] is intentional here: the lightweight [tryDrain] check reads
     * two StateFlows (instant) then delegates all IO work to [Dispatchers.IO] inside
     * [drain] and [HttpGpStrackClient.uploadRoute]. Using Unconfined means changes in
     * network/settings are acted on immediately without an extra dispatcher hop, and tests
     * using `backgroundScope` observe the effect synchronously.
     */
    override fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.Unconfined) {
            // Recover entries orphaned in IN_PROGRESS by a process death mid-upload.
            syncQueueDao.resetInProgress()
            tryDrain()
        }
        scope.launch(Dispatchers.Unconfined) { networkMonitor.isOnline.drop(1).collect { tryDrain() } }
        scope.launch(Dispatchers.Unconfined) { settingsSource.settings.drop(1).collect { tryDrain() } }
        scope.launch { enqueued.collect { tryDrain() } }
        scope.launch { runRetryTimer() }
    }

    /**
     * Sleeps until the earliest FAILED entry becomes due, then runs [tryDrain]. Every drain pass
     * re-arms the timer ([rearmRetry]) so a fresh failure shortens the wait; with no failed
     * entries the timer only waits for the next re-arm. When a due retry cannot run (offline,
     * sync disabled) the timer also waits for a re-arm instead of polling — the connectivity
     * and settings watchers drain, and so re-arm it, once conditions allow.
     */
    private suspend fun runRetryTimer() {
        var blocked = false
        while (true) {
            val waitMs = if (blocked) null else millisUntilNextRetry()
            val rearmed = if (waitMs == null) {
                rearmRetry.receive()
                true
            } else {
                withTimeoutOrNull(waitMs) { rearmRetry.receive() } != null
            }
            blocked = if (rearmed) false else !tryDrain()
        }
    }

    /** Milliseconds until the earliest FAILED entry is due (at least [MIN_RETRY_WAIT_MS]); null when none. */
    private suspend fun millisUntilNextRetry(): Long? {
        val next = syncQueueDao.getPendingSnapshot()
            .filter { it.state == SyncQueueState.FAILED }
            .mapNotNull { it.nextRetryEpochMs }
            .minOrNull()
            ?: return null
        return (next - timeProvider.nowEpochMs()).coerceAtLeast(MIN_RETRY_WAIT_MS)
    }

    /**
     * Evaluates the current network + settings state and drains the queue if conditions are met.
     *
     * Conditions: online AND NOT [AppSettings.noInternet] AND [AppSettings.syncEnabled].
     *
     * @return `true` when a drain pass ran, `false` when the conditions blocked it.
     */
    private suspend fun tryDrain(): Boolean {
        val online = networkMonitor.isOnline.first()
        val settings = settingsSource.settings.first()
        val shouldDrain = online && !settings.noInternet && settings.syncEnabled
        if (shouldDrain) drain(settings.serverAddress)
        return shouldDrain
    }

    /**
     * Processes all due (non-IN_PROGRESS) pending entries in a single pass.
     *
     * "Due" means [SyncQueueEntity.nextRetryEpochMs] is null or has passed.
     *
     * @return Number of successfully uploaded entries.
     */
    private suspend fun drain(serverAddress: String): Int {
        return drainMutex.withLock {
            val now = timeProvider.nowEpochMs()
            val pending = syncQueueDao.getPendingSnapshot()
            val due = pending.filter { entry ->
                entry.state != SyncQueueState.IN_PROGRESS &&
                    (entry.nextRetryEpochMs == null || entry.nextRetryEpochMs <= now)
            }
            var uploaded = 0
            for (entry in due) {
                if (processEntry(entry, serverAddress)) uploaded++
            }
            rearmRetry.trySend(Unit)
            uploaded
        }
    }

    /**
     * Attempts to upload a single queue entry to the server.
     *
     * Sets state to [SyncQueueState.IN_PROGRESS] before sending, then transitions to
     * [SyncQueueState.DONE] on success or [SyncQueueState.FAILED] with back-off on failure.
     * If the backing [routeDao] cannot find the route (deleted by user), the entry is removed.
     *
     * @return `true` if the upload succeeded.
     */
    private suspend fun processEntry(entry: SyncQueueEntity, serverAddress: String): Boolean {
        syncQueueDao.upsert(entry.copy(state = SyncQueueState.IN_PROGRESS))

        val routeEntity = routeDao.getById(entry.routeId)
        if (routeEntity == null) {
            syncQueueDao.delete(entry)
            return false
        }

        val result = client.uploadRoute(serverAddress, routeEntity.toDomain())
        val now = timeProvider.nowEpochMs()

        return if (result.isSuccess) {
            routeDao.setSynced(entry.routeId, true)
            syncQueueDao.upsert(entry.copy(state = SyncQueueState.DONE))
            syncQueueDao.pruneDone()
            true
        } else {
            val newAttemptCount = entry.attemptCount + 1
            syncQueueDao.upsert(
                entry.copy(
                    state = SyncQueueState.FAILED,
                    attemptCount = newAttemptCount,
                    lastAttemptEpochMs = now,
                    lastError = result.exceptionOrNull()?.message,
                    nextRetryEpochMs = now + SyncRetryPolicy.nextRetryDelayMs(newAttemptCount),
                )
            )
            false
        }
    }

    private companion object {
        /** Floor for the retry timer so an already-due entry is not re-checked in a busy loop. */
        const val MIN_RETRY_WAIT_MS = 1_000L
    }
}
