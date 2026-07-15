package com.mototracker.data.location

import com.mototracker.domain.recording.LocationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Process-scoped owner of the GPS location stream.
 *
 * Decoupled from any ViewModel or Activity lifecycle so that GPS acquisition
 * survives screen-off, Doze, and process-death-relaunch when hosted inside
 * [com.mototracker.service.RecordingService].
 *
 * The [samples] SharedFlow is permanently live for the process lifetime; [start] and [stop]
 * only control the upstream [LocationClient] subscription, not the flow itself.
 *
 * @param locationClient Upstream GPS source (real on-device or fake in tests).
 * @param scope          Long-lived coroutine scope; in production backed by [SupervisorJob] +
 *                       [kotlinx.coroutines.Dispatchers.Default].
 */
open class RideLocationCollector(
    private val locationClient: LocationClient,
    private val scope: CoroutineScope,
) {

    private val _samples = MutableSharedFlow<LocationSample>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Hot stream of GPS location samples. Collectors receive only samples emitted after they subscribe. */
    open val samples: SharedFlow<LocationSample> = _samples.asSharedFlow()

    private var collectionJob: Job? = null

    /**
     * Begins collecting GPS updates from [locationClient] and emitting them on [samples].
     *
     * Idempotent — a second call while a collection is already active is a no-op and does
     * not spawn a duplicate upstream subscription.
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
    }

    /**
     * Cancels the GPS collection job.
     *
     * Does not cancel [scope] or close [samples]; [start] can resume collection later without
     * recreating the collector.
     */
    open fun stop() {
        collectionJob?.cancel()
        collectionJob = null
    }
}
