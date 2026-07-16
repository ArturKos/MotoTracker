package com.mototracker.data.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * End-to-end delivery tests for [FusedLocationClientImpl] using Robolectric's
 * [org.robolectric.shadows.ShadowLocationManager].
 *
 * These tests complement the MockK-based [LocationClientTest] by exercising the full
 * registration → delivery → emission path through a real (shadow) [LocationManager].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FusedLocationClientImplTest {

    private lateinit var context: Context
    private lateinit var locationManager: LocationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    /**
     * Regression test for BACKLOG M1 (L3 GPS recording bug).
     *
     * [FusedLocationClientImpl.locationUpdates] collected on [Dispatchers.Default] (a pool
     * thread with no Looper) must still deliver location samples. The fix registers the listener
     * with [Looper.getMainLooper] so callback delivery is independent of the collecting thread.
     *
     * ShadowLocationManager's [org.robolectric.shadows.ShadowLocationManager.simulateLocation]
     * dispatches via the Looper/Handler that was supplied at registration time. After the fix the
     * callback is posted to the main Looper; [shadowOf(Looper.getMainLooper()).idle()] processes it
     * synchronously so the test is deterministic without a polling loop.
     */
    @Test
    fun `locationUpdates delivers sample when collected from Dispatchers Default`() {
        val received = CompletableDeferred<com.mototracker.domain.recording.LocationSample>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scope.launch {
            FusedLocationClientImpl(context)
                .locationUpdates(intervalMs = 100L)
                .take(1)
                .collect { received.complete(it) }
        }

        // Give the background coroutine time to start and register the GPS listener.
        // Dispatchers.Default uses real JVM threads; 300 ms is a generous upper bound.
        Thread.sleep(300L)

        val fakeLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 52.237049
            longitude = 21.017532
            speed = 13.89f    // ~50 km/h
            altitude = 120.0
            bearing = 45.0f
            time = 1_000L
        }

        // Deliver the fake fix. ShadowLocationManager posts to the registered Looper.
        shadowOf(locationManager).simulateLocation(fakeLocation)
        // Flush all pending tasks on the main Looper so onLocationChanged fires now.
        shadowOf(Looper.getMainLooper()).idle()

        val sample = runBlocking { withTimeout(5_000L) { received.await() } }

        assertEquals("lat mismatch", 52.237049, sample.lat, 1e-6)
        assertEquals("lng mismatch", 21.017532, sample.lng, 1e-6)
        assertEquals("speed mismatch", 13.89, sample.speedMps, 0.01)
        assertEquals("altitude mismatch", 120.0, sample.altitudeM, 0.01)
        assertEquals("bearing mismatch", 45.0f, sample.bearingDeg)

        scope.cancel()
    }
}
