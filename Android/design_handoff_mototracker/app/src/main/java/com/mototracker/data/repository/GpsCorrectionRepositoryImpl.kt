package com.mototracker.data.repository

import com.mototracker.core.time.TimeProvider
import com.mototracker.data.local.dao.CorrectionQueueDao
import com.mototracker.data.local.dao.RouteDao
import com.mototracker.data.local.entity.CorrectionQueueEntity
import com.mototracker.data.local.entity.CorrectionQueueState
import com.mototracker.data.local.entity.CorrectionStatus
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
 * Drains the correction queue when online and `offlineOnly` is false, submitting
 * each route's raw [pathJson][com.mototracker.data.local.entity.RouteEntity.pathJson]
 * to the OSRM map-matching service and persisting the result according to the
 * [CorrectionQualityGate] verdict. The raw [pathJson] is **never** mutated.
 *
 * @param correctionQueueDao DAO for the correction queue.
 * @param routeDao           DAO for reading and updating route correction fields.
 * @param settingsSource     Read-only stream of persisted app settings.
 * @param client             OSRM GPS-correction HTTP client.
 * @param networkMonitor     Observes device connectivity.
 * @param timeProvider       Wall-clock source for retry timestamps (injectable for tests).
 */
@Singleton
class GpsCorrectionRepositoryImpl @Inject constructor(
    private val correctionQueueDao: CorrectionQueueDao,
    private val routeDao: RouteDao,
    private val settingsSource: AppSettingsSource,
    private val client: GpsCorrectionClient,
    private val networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
) : GpsCorrectionRepository {

    private val qualityGate = CorrectionQualityGate()

    override val pendingCount: Flow<Int> = correctionQueueDao.getPendingCount()

    /**
     * Inserts or resets the correction queue entry for [routeId] to [CorrectionQueueState.PENDING].
     */
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

    /**
     * Drains all due entries regardless of the auto-drain conditions.
     *
     * Returns 0 immediately when `offlineOnly` is true.
     */
    override suspend fun correctNow(): Int {
        val settings = settingsSource.settings.first()
        if (settings.offlineOnly) return 0
        return drain(settings.osrmBaseUrl)
    }

    /**
     * Launches background coroutines in [scope] that re-drain when connectivity or
     * settings change. Uses [Dispatchers.Unconfined] for the same rationale as
     * [SyncRepositoryImpl.start]: lightweight check + immediate reaction to state changes.
     */
    override fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.Unconfined) { tryDrain() }
        scope.launch(Dispatchers.Unconfined) { networkMonitor.isOnline.drop(1).collect { tryDrain() } }
        scope.launch(Dispatchers.Unconfined) { settingsSource.settings.drop(1).collect { tryDrain() } }
    }

    private suspend fun tryDrain() {
        val online = networkMonitor.isOnline.first()
        val settings = settingsSource.settings.first()
        if (online && !settings.offlineOnly) drain(settings.osrmBaseUrl)
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
     * - [CorrectionQualityGate.Verdict.ACCEPT] → write [correctedPathJson] + confidence + correctionStatus=DONE; queue DONE+prune
     * - [CorrectionQualityGate.Verdict.LOW_CONFIDENCE] → write confidence + correctionStatus=LOW_CONFIDENCE; queue DONE+prune
     * - [CorrectionQualityGate.Verdict.REJECT] (NoMatch) → correctionStatus=NONE; queue DONE+prune
     *
     * The raw [pathJson][com.mototracker.data.local.entity.RouteEntity.pathJson] is never touched.
     *
     * @return `true` only on a quality-gate ACCEPT.
     */
    private suspend fun processEntry(entry: CorrectionQueueEntity, osrmBaseUrl: String): Boolean {
        correctionQueueDao.upsert(entry.copy(state = CorrectionQueueState.IN_PROGRESS))

        val routeEntity = routeDao.getById(entry.routeId) ?: run {
            correctionQueueDao.delete(entry)
            return false
        }

        val points = parsePathJson(routeEntity.pathJson)
        if (points.isEmpty()) {
            correctionQueueDao.upsert(entry.copy(state = CorrectionQueueState.DONE))
            correctionQueueDao.pruneDone()
            return false
        }

        val result = client.match(osrmBaseUrl, points)
        val now = timeProvider.nowEpochMs()

        return if (result.isSuccess) {
            val outcome = result.getOrThrow()
            when (val verdict = qualityGate.evaluate(outcome)) {
                CorrectionQualityGate.Verdict.ACCEPT -> {
                    val matched = outcome as CorrectionOutcome.Matched
                    routeDao.upsert(
                        routeEntity.copy(
                            correctedPathJson = buildPathJson(matched.points),
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
     * Parses `pathJson` (format `[{"lat":…,"lng":…},…]`) into a list of [TrackPoint]s.
     *
     * Returns an empty list on null input or any parse error so the caller can skip
     * correction rather than retrying an unparseable trace.
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

    /**
     * Serialises [points] back to the `[{"lat":…,"lng":…},…]` JSON format used by [pathJson].
     */
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
}
