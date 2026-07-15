package com.mototracker.data.location

import com.mototracker.domain.recording.LocationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Process-scoped owner of the GPS location stream and GNSS satellite status.
 *
 * Decoupled from any ViewModel or Activity lifecycle so that GPS acquisition
 * survives screen-off, Doze, and process-death-relaunch when hosted inside
 * [com.mototracker.service.RecordingService].
 *
 * The [samples] and [satelliteCounts] SharedFlows are permanently live for the process
 * lifetime; [start] and [stop] only control the upstream subscriptions, not the flows.
 *
 * @param locationClient   Upstream GPS source (real on-device or fake in tests).
 * @param scope            Long-lived coroutine scope; in production backed by [SupervisorJob] +
 *                         [kotlinx.coroutines.Dispatchers.Default].
 * @param gnssStatusClient Upstream GNSS satellite status source (real on-device or fake in tests).
 */
open class RideLocationCollector(
    private val locationClient: LocationClient,
    private val scope: CoroutineScope,
    private val gnssStatusClient: GnssStatusClient = object : GnssStatusClient {
        override fun satelliteCounts() = emptyFlow<GnssSatelliteCount>()
    },
) {

    private val _samples = MutableSharedFlow<LocationSample>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Hot stream of GPS location samples. Collectors receive only samples emitted after they subscribe. */
    open val samples: SharedFlow<LocationSample> = _samples.asSharedFlow()

    private val _satelliteCounts = MutableSharedFlow<GnssSatelliteCount>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Hot stream of GNSS satellite count snapshots.
     *
     * New subscribers immediately receive the most recently emitted value (replay=1).
     * On-device only (🔬) — satellite count requires a real GNSS fix.
     */
    open val satelliteCounts: SharedFlow<GnssSatelliteCount> = _satelliteCounts.asSharedFlow()

    private var collectionJob: Job? = null
    private var gnssCollectionJob: Job? = null

    /**
     * Begins collecting GPS updates from [locationClient] and GNSS status from
     * [gnssStatusClient], emitting them on [samples] and [satelliteCounts] respectively.
     *
     * Idempotent — a second call while a collection is already active is a no-op and does
     * not spawn duplicate upstream subscriptions.
     *
     * @param intervalMs Minimum time between GPS updates in milliseconds.
     */
    open fun start(intervalMs: Long = 1_000L) {
        if (collectionJob?.isActive == true) return
        collectionJob = scope.launch {
            try {
                locationClient.locationUpdates(intervalMs).collect { sample ->
                    _samples.emit(sample)
                }
            } catch (_: SecurityException) {
                // Location permission revoked mid-session; collector stays alive and restartable.
            }
        }
        startGnss()
    }

    /**
     * Begins collecting GNSS satellite status from [gnssStatusClient], emitting snapshots
     * on [satelliteCounts].
     *
     * Idempotent — a second call while GNSS collection is already active is a no-op.
     * Call this in the Recording screen's Idle phase so the satellite count is live before
     * the rider taps Start (K7). Degrades gracefully when location permission is denied —
     * [AndroidGnssStatusClient] catches [SecurityException] and closes the flow; the count
     * stays at its last emitted value (0 on a fresh start).
     */
    open fun startGnss() {
        if (gnssCollectionJob?.isActive == true) return
        gnssCollectionJob = scope.launch {
            try {
                gnssStatusClient.satelliteCounts().collect { count ->
                    _satelliteCounts.emit(count)
                }
            } catch (_: SecurityException) {
                // ACCESS_FINE_LOCATION revoked; GNSS stream closes cleanly.
            }
        }
    }

    /**
     * Cancels both the GPS and GNSS collection jobs.
     *
     * Does not cancel [scope] or close [samples] / [satelliteCounts]; [start] can resume
     * collection later without recreating the collector.
     */
    open fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        gnssCollectionJob?.cancel()
        gnssCollectionJob = null
    }

    /**
     * Cancels the GNSS collection job only, leaving GPS collection running.
     *
     * Used by [com.mototracker.ui.screens.record.RecordingViewModel] on screen exit when
     * a recording is not active — the service-owned GPS stream must not be interrupted, but
     * the Idle-phase GNSS listener is no longer needed.
     */
    open fun stopGnss() {
        gnssCollectionJob?.cancel()
        gnssCollectionJob = null
    }
}
