package com.mototracker.data.location

import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import app.cash.turbine.test
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [FusedLocationClientImpl].
 *
 * Robolectric is required so that [Looper.getMainLooper] — called inside
 * [FusedLocationClientImpl.locationUpdates] — resolves to a real Android Looper
 * rather than the stub that throws [RuntimeException] in plain JVM tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class LocationClientTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * When ACCESS_FINE_LOCATION is not granted, [FusedLocationClientImpl.locationUpdates]
     * must close the flow with [SecurityException] instead of propagating an uncaught crash.
     */
    @Test
    fun `locationUpdates flow closes with SecurityException when permission denied`() =
        runTest(testDispatcher) {
            val locationManager = mockk<LocationManager>()
            every { locationManager.allProviders } returns emptyList()
            // Match the 5-arg overload used after the M1 Looper fix.
            every {
                locationManager.requestLocationUpdates(
                    any<String>(),
                    any<Long>(),
                    any<Float>(),
                    any<LocationListener>(),
                    any<Looper>(),
                )
            } throws SecurityException("ACCESS_FINE_LOCATION not granted")

            val context = mockk<Context>()
            every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager

            val client = FusedLocationClientImpl(context)

            client.locationUpdates().test {
                val error = awaitError()
                assertTrue(
                    "error should be SecurityException, was ${error::class.simpleName}",
                    error is SecurityException,
                )
            }
        }

    /**
     * Regression guard for BACKLOG M1: [FusedLocationClientImpl.locationUpdates] must
     * register the [LocationListener] with an explicit [Looper.getMainLooper] so that
     * GPS callbacks are delivered regardless of the collecting thread's Looper state.
     *
     * Pool threads on [kotlinx.coroutines.Dispatchers.Default] have no Looper. The old
     * 4-arg [LocationManager.requestLocationUpdates] overload bound delivery to the calling
     * thread's Looper; on a pool thread that caused silent loss of all GPS updates.
     */
    @Test
    fun `locationUpdates registers GPS listener with Looper getMainLooper for thread-independent delivery`() =
        runTest(testDispatcher) {
            val locationManager = mockk<LocationManager>()
            var capturedLooper: Looper? = null

            every { locationManager.allProviders } returns emptyList()
            every {
                locationManager.requestLocationUpdates(
                    any<String>(),
                    any<Long>(),
                    any<Float>(),
                    any<LocationListener>(),
                    any<Looper>(),
                )
            } answers {
                // lastArg() is the Looper (5th arg); store it for assertion below.
                capturedLooper = lastArg()
            }
            every { locationManager.removeUpdates(any<LocationListener>()) } just runs

            val context = mockk<Context>()
            every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager

            val client = FusedLocationClientImpl(context)

            val job = launch { client.locationUpdates().collect {} }
            // Advance the scheduler so the callbackFlow producer block runs and
            // requestLocationUpdates is called before we inspect the captured value.
            advanceUntilIdle()

            assertNotNull(
                "GPS listener must be registered with a non-null Looper",
                capturedLooper,
            )
            assertEquals(
                "GPS listener must be registered with Looper.getMainLooper() " +
                    "so callbacks arrive on any collecting thread",
                Looper.getMainLooper(),
                capturedLooper,
            )

            job.cancel()
        }
}
