package com.mototracker.data.location

import app.cash.turbine.test
import com.mototracker.domain.recording.LocationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeLocationClient(private val upstream: Flow<LocationSample>) : LocationClient {
    override fun locationUpdates(intervalMs: Long): Flow<LocationSample> = upstream
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun makeSample(lat: Double = 0.0, lng: Double = 0.0) = LocationSample(
    lat = lat, lng = lng, speedMps = 0.0, altitudeM = 0.0, bearingDeg = 0f, timeMs = 0L,
)

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RideLocationCollectorTest {

    /**
     * (a) start() re-emits upstream samples on [RideLocationCollector.samples].
     */
    @Test
    fun `start re-emits upstream samples on samples`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val source = MutableSharedFlow<LocationSample>(extraBufferCapacity = 8)
            val collector = RideLocationCollector(
                locationClient = FakeLocationClient(source),
                scope = CoroutineScope(SupervisorJob() + testDispatcher),
            )

            collector.samples.test {
                collector.start()
                advanceTimeBy(50L)
                source.tryEmit(makeSample(lat = 1.0))
                advanceTimeBy(50L)
                assertEquals(makeSample(lat = 1.0), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    /**
     * (b) A second [RideLocationCollector.start] call while already collecting is a no-op
     * and does NOT spawn a duplicate upstream subscription (no doubled emissions).
     */
    @Test
    fun `start is idempotent — second call does not duplicate emissions`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val source = MutableSharedFlow<LocationSample>(extraBufferCapacity = 8)
            val collector = RideLocationCollector(
                locationClient = FakeLocationClient(source),
                scope = CoroutineScope(SupervisorJob() + testDispatcher),
            )

            collector.samples.test {
                collector.start()
                collector.start() // second call — must be a no-op
                advanceTimeBy(50L)
                source.tryEmit(makeSample(lat = 2.0))
                advanceTimeBy(50L)
                assertEquals(makeSample(lat = 2.0), awaitItem())
                // If a duplicate upstream subscription existed, a second identical item would arrive.
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    /**
     * (c) [RideLocationCollector.stop] halts emissions; a subsequent [RideLocationCollector.start]
     * resumes collection so new samples arrive again.
     */
    @Test
    fun `stop halts emissions and a subsequent start resumes collection`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val source = MutableSharedFlow<LocationSample>(extraBufferCapacity = 8)
            val collector = RideLocationCollector(
                locationClient = FakeLocationClient(source),
                scope = CoroutineScope(SupervisorJob() + testDispatcher),
            )

            collector.samples.test {
                // Phase 1: start, emit, verify
                collector.start()
                advanceTimeBy(50L)
                source.tryEmit(makeSample(lat = 10.0))
                advanceTimeBy(50L)
                assertEquals(makeSample(lat = 10.0), awaitItem())

                // Phase 2: stop, emit, verify nothing arrives
                collector.stop()
                advanceTimeBy(50L) // let cancellation process
                source.tryEmit(makeSample(lat = 20.0))
                advanceTimeBy(50L)
                expectNoEvents() // subscription cancelled; item dropped

                // Phase 3: restart, emit, verify
                collector.start()
                advanceTimeBy(50L)
                source.tryEmit(makeSample(lat = 30.0))
                advanceTimeBy(50L)
                assertEquals(makeSample(lat = 30.0), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    /**
     * (d) A [SecurityException] from the upstream flow does not crash the collector and
     * [RideLocationCollector.start] can be called again to resume collection.
     */
    @Test
    fun `SecurityException from upstream does not crash the collector and it can be restarted`() {
        val testDispatcher = StandardTestDispatcher()
        runTest(testDispatcher) {
            val goodSource = MutableSharedFlow<LocationSample>(extraBufferCapacity = 8)
            var useGood = false
            val client = object : LocationClient {
                override fun locationUpdates(intervalMs: Long): Flow<LocationSample> =
                    if (useGood) goodSource
                    else flow { throw SecurityException("location permission denied") }
            }
            val collector = RideLocationCollector(
                locationClient = client,
                scope = CoroutineScope(SupervisorJob() + testDispatcher),
            )

            collector.samples.test {
                // First start — SecurityException thrown and caught internally; no crash, no items.
                collector.start()
                advanceTimeBy(50L)
                expectNoEvents()

                // Collector can be restarted after the failed job completes.
                useGood = true
                collector.start()
                advanceTimeBy(50L)
                goodSource.tryEmit(makeSample(lat = 99.0))
                advanceTimeBy(50L)
                assertEquals(makeSample(lat = 99.0), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
