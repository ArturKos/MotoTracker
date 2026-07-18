package com.mototracker.data.repository

import com.mototracker.core.format.TraceChunkCodec
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.local.dao.CorrectionQueueDao
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.dao.RouteTraceChunkDao
import com.mototracker.data.local.entity.CorrectionQueueEntity
import com.mototracker.data.local.entity.CorrectionQueueState
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.local.entity.RouteTraceChunkEntity
import com.mototracker.data.network.CorrectionOutcome
import com.mototracker.data.network.GpsCorrectionClient
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.network.TrackPoint
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.SyncRetryPolicy
import com.mototracker.domain.correction.CorrectionQualityGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [GpsCorrectionRepository].
 *
 * Drains the correction queue when online and `noInternet` is false, submitting
 * each route's raw GPS trace to the OSRM map-matching service and persisting the result
 * according to the [CorrectionQualityGate] verdict.
 *
 * Raw GPS chunks are read via [traceChunkDao] (`kind = "RAW"`). The raw trace is **never**
 * mutated. On ACCEPT, the corrected trace is written as `kind = "CORRECTED"` chunks.
 *
 * @param correctionQueueDao DAO for the correction queue.
 * @param routeDao           DAO for reading and updating route correction status columns.
 * @param traceChunkDao      DAO for reading RAW chunks and writing CORRECTED chunks.
 * @param settingsSource     Read-only stream of persisted app settings.
 * @param client             OSRM GPS-correction HTTP client.
 * @param networkMonitor     Observes device connectivity.
 * @param timeProvider       Wall-clock source for retry timestamps (injectable for tests).
 */
@Singleton
class GpsCorrectionRepositoryImpl @Inject constructor(
    private val correctionQueueDao: CorrectionQueueDao,
    private val routeDao: RouteDao,
    private val traceChunkDao: RouteTraceChunkDao,
    private val settingsSource: AppSettingsSource,
    private val client: GpsCorrectionClient,
    private val networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
) : GpsCorrectionRepository {

    private val qualityGate = CorrectionQualityGate()

    override val pendingCount: Flow<Int> = correctionQueueDao.getPendingCount()

    /** Inserts or resets the correction queue entry for [routeId] to [CorrectionQueueState.PENDING]. */
    override suspend fun enqueue(routeId: String) {
        val existing = correctionQueueDao.findByRouteId(routeId)
        correctionQueueDao.upsert(
            CorrectionQueueEntity(
                id = existing?.id ?: 0L,
                routeId = routeId,
                state = CorrectionQueueState.PENDING,
                attemptCount = 0,
                lastAttemptEpochMs = null,
                nextRetryEpochMs = null,
                lastError = null,
            ),
        )
    }

    /** Drains all due entries regardless of the auto-drain conditions. Returns 0 when [AppSettings.noInternet] is `true`. */
    override suspend fun correctNow(): Int {
        val settings = settingsSource.settings.first()
        if (settings.noInternet) return 0
        return drain(settings.osrmBaseUrl)
    }

    /**
     * Launches background coroutines in [scope] that re-drain when connectivity or settings change.
     */
    override fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.Unconfined) { tryDrain() }
        scope.launch(Dispatchers.Unconfined) { networkMonitor.isOnline.drop(1).collect { tryDrain() } }
        scope.launch(Dispatchers.Unconfined) { settingsSource.settings.drop(1).collect { tryDrain() } }
    }

    private suspend fun tryDrain() {
        val online = networkMonitor.isOnline.first()
        val settings = settingsSource.settings.first()
        if (online && !settings.noInternet) drain(settings.osrmBaseUrl)
    }

    private suspend fun drain(osrmBaseUrl: String): Int {
        val now = timeProvider.nowEpochMs()
        val pending = correctionQueueDao.getPendingSnapshot()
        val due = pending.filter { entry ->
            entry.state != CorrectionQueueState.IN_PROGRESS &&
                (entry.nextRetryEpochMs == null || entry.nextRetryEpochMs <= now)
        }
        var corrected = 0
        for (entry in due) {
            if (processEntry(entry, osrmBaseUrl)) corrected++
        }
        return corrected
    }

    /**
     * Processes a single correction queue entry.
     *
     * State transitions:
     * - Transport/HTTP error → FAILED + exponential back-off
     * - [CorrectionQualityGate.Verdict.ACCEPT] → write CORRECTED chunks + update status=DONE + confidence; queue DONE+prune
     * - [CorrectionQualityGate.Verdict.LOW_CONFIDENCE] → update confidence + status=LOW_CONFIDENCE; queue DONE+prune
     * - [CorrectionQualityGate.Verdict.REJECT] → update status=NONE; queue DONE+prune
     *
     * The RAW chunks are **never** modified.
     *
     * @return `true` only on a quality-gate ACCEPT.
     */
    private suspend fun processEntry(entry: CorrectionQueueEntity, osrmBaseUrl: String): Boolean {
        correctionQueueDao.upsert(entry.copy(state = CorrectionQueueState.IN_PROGRESS))

        val routeEntity = routeDao.getById(entry.routeId) ?: run {
            correctionQueueDao.delete(entry)
            return false
        }

        val rawChunks = traceChunkDao.getChunks(entry.routeId, KIND_RAW)
        val pathJson = TraceChunkCodec.join(rawChunks.map { it.chunkJson })
        val points = parsePathJson(pathJson)
        if (points.isEmpty()) {
            correctionQueueDao.upsert(entry.copy(state = CorrectionQueueState.DONE))
            correctionQueueDao.pruneDone()
            return false
        }

        val result = client.match(osrmBaseUrl, points)
        val now = timeProvider.nowEpochMs()

        return if (result.isSuccess) {
            val outcome = result.getOrThrow()
            when (qualityGate.evaluate(outcome)) {
                CorrectionQualityGate.Verdict.ACCEPT -> {
                    val matched = outcome as CorrectionOutcome.Matched
                    val correctedJson = buildPathJson(matched.points)
                    val corrChunks = TraceChunkCodec.split(correctedJson).mapIndexed { seq, chunkJson ->
                        RouteTraceChunkEntity(entry.routeId, KIND_CORRECTED, seq, chunkJson)
                    }
                    traceChunkDao.replace(entry.routeId, KIND_CORRECTED, corrChunks)
                    routeDao.upsert(
                        routeEntity.copy(
                            confidence = matched.confidence,
                            correctionStatus = CorrectionStatus.DONE,
                        ),
                    )
                    correctionQueueDao.upsert(entry.copy(state = CorrectionQueueState.DONE))
                    correctionQueueDao.pruneDone()
                    true
                }
                CorrectionQualityGate.Verdict.LOW_CONFIDENCE -> {
                    val matched = outcome as CorrectionOutcome.Matched
                    routeDao.upsert(
                        routeEntity.copy(
                            confidence = matched.confidence,
                            correctionStatus = CorrectionStatus.LOW_CONFIDENCE,
                        ),
                    )
                    correctionQueueDao.upsert(entry.copy(state = CorrectionQueueState.DONE))
                    correctionQueueDao.pruneDone()
                    false
                }
                CorrectionQualityGate.Verdict.REJECT -> {
                    routeDao.upsert(routeEntity.copy(correctionStatus = CorrectionStatus.NONE))
                    correctionQueueDao.upsert(entry.copy(state = CorrectionQueueState.DONE))
                    correctionQueueDao.pruneDone()
                    false
                }
            }
        } else {
            val newAttemptCount = entry.attemptCount + 1
            correctionQueueDao.upsert(
                entry.copy(
                    state = CorrectionQueueState.FAILED,
                    attemptCount = newAttemptCount,
                    lastAttemptEpochMs = now,
                    lastError = result.exceptionOrNull()?.message,
                    nextRetryEpochMs = now + SyncRetryPolicy.nextRetryDelayMs(newAttemptCount),
                ),
            )
            false
        }
    }

    /**
     * Parses a path JSON string (`[{"lat":…,"lng":…},…]`) into a list of [TrackPoint]s.
     *
     * Returns an empty list on null input or any parse error.
     */
    private fun parsePathJson(pathJson: String?): List<TrackPoint> {
        if (pathJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(pathJson)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                TrackPoint(lat = obj.getDouble("lat"), lon = obj.getDouble("lng"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Serialises [points] back to the `[{"lat":…,"lng":…},…]` JSON format. */
    private fun buildPathJson(points: List<TrackPoint>): String {
        val arr = JSONArray()
        points.forEach { pt ->
            arr.put(
                JSONObject().apply {
                    put("lat", pt.lat)
                    put("lng", pt.lon)
                },
            )
        }
        return arr.toString()
    }

    private companion object {
        private const val KIND_RAW = "RAW"
        private const val KIND_CORRECTED = "CORRECTED"
    }
}
