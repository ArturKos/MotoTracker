package com.mototracker.data.location

import com.mototracker.domain.recording.LocationProvider
import com.mototracker.domain.recording.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [LocationFusion.prefer].
 *
 * All tests use a fixed [NOW] timestamp and create samples with explicit [LocationSample.timeMs]
 * values relative to it so freshness windows are deterministic.
 */
class LocationFusionTest {

    private val NOW = 100_000L
    private val FRESH_MS = 10_000L   // gpsFreshMs default

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun gpsSample(
        timeMs: Long = NOW,
        accuracyM: Double = 5.0,
    ) = LocationSample(
        lat = 52.0, lng = 21.0,
        speedMps = 10.0, altitudeM = 100.0, bearingDeg = 0f,
        timeMs = timeMs,
        accuracyM = accuracyM,
        provider = LocationProvider.GPS,
    )

    private fun networkSample(
        timeMs: Long = NOW,
        accuracyM: Double = 50.0,
    ) = LocationSample(
        lat = 52.1, lng = 21.1,
        speedMps = 0.0, altitudeM = 0.0, bearingDeg = 0f,
        timeMs = timeMs,
        accuracyM = accuracyM,
        provider = LocationProvider.NETWORK,
    )

    // ── cases ─────────────────────────────────────────────────────────────────

    /** Both null → null. */
    @Test
    fun `prefer returns null when both inputs are null`() {
        assertNull(LocationFusion.prefer(null, null, NOW))
    }

    /** Only GPS present → GPS returned regardless of freshness. */
    @Test
    fun `prefer returns GPS when only GPS is present`() {
        val gps = gpsSample()
        assertEquals(gps, LocationFusion.prefer(gps, null, NOW))
    }

    /** Only network present → network returned regardless of freshness. */
    @Test
    fun `prefer returns network when only network is present`() {
        val net = networkSample()
        assertEquals(net, LocationFusion.prefer(null, net, NOW))
    }

    /** Fresh GPS wins over a fresh network sample with worse accuracy. */
    @Test
    fun `prefer returns GPS when GPS is fresh and network is also fresh but GPS accuracy is better`() {
        val gps = gpsSample(timeMs = NOW, accuracyM = 5.0)
        val net = networkSample(timeMs = NOW, accuracyM = 50.0)
        val result = LocationFusion.prefer(gps, net, NOW, FRESH_MS)
        assertEquals(LocationProvider.GPS, result?.provider)
    }

    /** Stale GPS falls back to fresh network. */
    @Test
    fun `prefer falls back to network when GPS is stale`() {
        val staleGps = gpsSample(timeMs = NOW - FRESH_MS - 1, accuracyM = 5.0)
        val net = networkSample(timeMs = NOW, accuracyM = 50.0)
        val result = LocationFusion.prefer(staleGps, net, NOW, FRESH_MS)
        assertEquals(LocationProvider.NETWORK, result?.provider)
    }

    /** When both are fresh, prefer the sample with the smaller accuracyM; GPS wins ties. */
    @Test
    fun `prefer GPS when both fresh and GPS has better accuracy`() {
        val gps = gpsSample(timeMs = NOW, accuracyM = 4.0)
        val net = networkSample(timeMs = NOW, accuracyM = 30.0)
        val result = LocationFusion.prefer(gps, net, NOW, FRESH_MS)
        assertEquals(LocationProvider.GPS, result?.provider)
    }

    /** When both are fresh, network wins if its accuracyM is strictly smaller. */
    @Test
    fun `prefer network when both fresh and network has strictly better accuracy`() {
        val gps = gpsSample(timeMs = NOW, accuracyM = 60.0)
        val net = networkSample(timeMs = NOW, accuracyM = 20.0)
        val result = LocationFusion.prefer(gps, net, NOW, FRESH_MS)
        assertEquals(LocationProvider.NETWORK, result?.provider)
    }

    /** GPS wins a tie on accuracyM when both are fresh. */
    @Test
    fun `prefer GPS when both fresh and accuracyM is equal (GPS tie-break)`() {
        val gps = gpsSample(timeMs = NOW, accuracyM = 10.0)
        val net = networkSample(timeMs = NOW, accuracyM = 10.0)
        val result = LocationFusion.prefer(gps, net, NOW, FRESH_MS)
        assertEquals(LocationProvider.GPS, result?.provider)
    }

    /** GPS exactly at the freshness boundary is still considered fresh. */
    @Test
    fun `prefer GPS at exactly the freshness boundary`() {
        val gps = gpsSample(timeMs = NOW - FRESH_MS, accuracyM = 5.0)
        val net = networkSample(timeMs = NOW, accuracyM = 50.0)
        val result = LocationFusion.prefer(gps, net, NOW, FRESH_MS)
        assertEquals(LocationProvider.GPS, result?.provider)
    }

    /** GPS one ms past the boundary is stale; network is returned. */
    @Test
    fun `prefer network when GPS is one ms past freshness boundary`() {
        val staleGps = gpsSample(timeMs = NOW - FRESH_MS - 1, accuracyM = 5.0)
        val net = networkSample(timeMs = NOW, accuracyM = 50.0)
        val result = LocationFusion.prefer(staleGps, net, NOW, FRESH_MS)
        assertEquals(LocationProvider.NETWORK, result?.provider)
    }
}
