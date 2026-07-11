package com.mototracker.data.location

import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [FusedLocationClientImpl].
 *
 * Verifies the Layer-1 seam: when [LocationManager.requestLocationUpdates] throws
 * [SecurityException] (ACCESS_FINE_LOCATION not granted), the Flow returned by
 * [FusedLocationClientImpl.locationUpdates] terminates with that exception rather than
 * propagating an uncaught crash out of the coroutine.
 */
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

    @Test
    fun `locationUpdates flow closes with SecurityException when permission denied`() =
        runTest(testDispatcher) {
            val locationManager = mockk<LocationManager>()
            every {
                locationManager.requestLocationUpdates(
                    any<String>(),
                    any<Long>(),
                    any<Float>(),
                    any<LocationListener>(),
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
}
