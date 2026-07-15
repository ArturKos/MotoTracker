package com.mototracker.data.location

import app.cash.turbine.test
import com.mototracker.domain.recording.LocationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeGnssStatusClient(
    private val upstream: Flow<GnssSatelliteCount>,
) : GnssStatusClient {
    override fun satelliteCounts(): Flow<GnssSatelliteCount> = upstream
}

private val noOpLocationClient = object : LocationClient {
    override fun locationUpdates(intervalMs: Long): Flow<LocationSample> = emptyFlow()
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RideLocationCollectorGnssTest {

    /**
     * (a) start() re-emits upstream GNSS counts on [RideLocationCollector.satelliteCounts].
     */
    @Test
    fun `start re-emits GNSS counts on satelliteCounts`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val source = MutableSharedFlow<GnssSatelliteCount>(extraBufferCapacity = 8)
            val collector = RideLocationCollector(
                locationClient = noOpLocationClient,
                gnssStatusClient = FakeGnssStatusClient(source),
                scope = CoroutineScope(SupervisorJob() + testDispatcher),
            )

            collector.satelliteCounts.test {
                collector.start()
                advanceTimeBy(50L)
                source.tryEmit(GnssSatelliteCount(usedInFix = 7, total = 12))
                advanceTimeBy(50L)
                assertEquals(GnssSatelliteCount(usedInFix = 7, total = 12), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    /**
     * (b) stop() halts GNSS emissions; a subsequent start() resumes collection.
     */
    @Test
    fun `stop halts GNSS emissions and a subsequent start resumes collection`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val source = MutableSharedFlow<GnssSatelliteCount>(extraBufferCapacity = 8)
            val collector = RideLocationCollector(
                locationClient = noOpLocationClient,
                gnssStatusClient = FakeGnssStatusClient(source),
                scope = CoroutineScope(SupervisorJob() + testDispatcher),
            )

            collector.satelliteCounts.test {
                // Phase 1: start, emit, verify
                collector.start()
                advanceTimeBy(50L)
                source.tryEmit(GnssSatelliteCount(usedInFix = 5, total = 10))
                advanceTimeBy(50L)
                assertEquals(GnssSatelliteCount(usedInFix = 5, total = 10), awaitItem())

                // Phase 2: stop, emit, verify nothing arrives
                collector.stop()
                advanceTimeBy(50L)
                source.tryEmit(GnssSatelliteCount(usedInFix = 3, total = 8))
                advanceTimeBy(50L)
                // replay=1 holds the last value; after stop no new items should arrive
                expectNoEvents()

                // Phase 3: restart, emit, verify new item arrives
                collector.start()
                advanceTimeBy(50L)
                source.tryEmit(GnssSatelliteCount(usedInFix = 9, total = 14))
                advanceTimeBy(50L)
                assertEquals(GnssSatelliteCount(usedInFix = 9, total = 14), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    /**
     * (c) A [SecurityException] from the upstream GNSS flow does not crash the collector
     * and [RideLocationCollector.start] can be called again without error.
     */
    @Test
    fun `SecurityException from GNSS upstream does not crash the collector`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val goodSource = MutableSharedFlow<GnssSatelliteCount>(extraBufferCapacity = 8)
            var useGood = false
            val gnssClient = object : GnssStatusClient {
                override fun satelliteCounts(): Flow<GnssSatelliteCount> =
                    if (useGood) goodSource
                    else flow { throw SecurityException("location permission denied") }
            }
            val collector = RideLocationCollector(
                locationClient = noOpLocationClient,
                gnssStatusClient = gnssClient,
                scope = CoroutineScope(SupervisorJob() + testDispatcher),
            )

            collector.satelliteCounts.test {
                // First start — SecurityException caught; no crash, no items.
                collector.start()
                advanceTimeBy(50L)
                expectNoEvents()

                // Restart with good source.
                collector.stop()
                useGood = true
                collector.start()
                advanceTimeBy(50L)
                goodSource.tryEmit(GnssSatelliteCount(usedInFix = 4, total = 9))
                advanceTimeBy(50L)
                assertEquals(GnssSatelliteCount(usedInFix = 4, total = 9), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    /**
     * (d) start() is idempotent — a second call does not spawn a duplicate GNSS subscription.
     */
    @Test
    fun `start is idempotent for GNSS — second call does not duplicate emissions`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val source = MutableSharedFlow<GnssSatelliteCount>(extraBufferCapacity = 8)
            val collector = RideLocationCollector(
                locationClient = noOpLocationClient,
                gnssStatusClient = FakeGnssStatusClient(source),
                scope = CoroutineScope(SupervisorJob() + testDispatcher),
            )

            collector.satelliteCounts.test {
                collector.start()
                collector.start() // second call — must be a no-op
                advanceTimeBy(50L)
                source.tryEmit(GnssSatelliteCount(usedInFix = 6, total = 11))
                advanceTimeBy(50L)
                assertEquals(GnssSatelliteCount(usedInFix = 6, total = 11), awaitItem())
                // If a duplicate subscription existed, a second identical item would arrive.
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
