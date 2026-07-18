package com.mototracker.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.mototracker.domain.recording.LocationProvider
import com.mototracker.domain.recording.LocationSample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable seam over the platform location providers.
 *
 * The production implementation [FusedLocationClientImpl] wraps [LocationManager].
 * In unit tests, use a fake that emits pre-canned [LocationSample] values.
 */
interface LocationClient {
    /**
     * Hot [Flow] of fused location samples.
     *
     * Merges GPS and (when available) network positions via [LocationFusion], preferring GPS
     * when fresh. Begins requesting location updates on collection; stops when the collector
     * cancels. On-device only (🔬) — use a fake in unit tests.
     *
     * @param intervalMs Minimum time between updates in milliseconds.
     */
    fun locationUpdates(intervalMs: Long = 1_000L): Flow<LocationSample>
}

/**
 * [LocationClient] implementation backed by [LocationManager].
 *
 * Registers both [LocationManager.GPS_PROVIDER] and (when present and enabled)
 * [LocationManager.NETWORK_PROVIDER]. Both listeners feed the same [callbackFlow] channel;
 * [LocationFusion] merges them so downstream always receives the best available position.
 *
 * Location permission (ACCESS_FINE_LOCATION) must be granted before collecting the flow.
 * The foreground [com.mototracker.service.RecordingService] keeps the notification alive so
 * the system does not kill location updates while the app is in the background.
 *
 * @param context Application context supplied by Hilt.
 */
@Singleton
class FusedLocationClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationClient {

    @SuppressLint("MissingPermission")
    override fun locationUpdates(intervalMs: Long): Flow<LocationSample> = callbackFlow {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        var lastGps: LocationSample? = null
        var lastNetwork: LocationSample? = null

        fun emitFused() {
            LocationFusion.prefer(lastGps, lastNetwork, System.currentTimeMillis())
                ?.let { trySend(it) }
        }

        val gpsListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastGps = location.toSample(LocationProvider.GPS)
                emitFused()
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val networkListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastNetwork = location.toSample(LocationProvider.NETWORK)
                emitFused()
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        try {
            // Use the Looper-taking overload so callbacks are delivered on the main Looper
            // regardless of which thread collects this flow. Without an explicit Looper,
            // Android binds delivery to the calling thread's Looper; pool threads on
            // Dispatchers.Default have none, causing silent loss of all updates (BACKLOG M1).
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                intervalMs,
                0f,
                gpsListener,
                Looper.getMainLooper(),
            )

            // Register NETWORK_PROVIDER as a supplementary coarse source (S4).
            // Guarded so a missing or disabled provider degrades gracefully without a crash,
            // allowing GPS to continue working alone. ACCESS_COARSE_LOCATION is already
            // declared in the manifest alongside ACCESS_FINE_LOCATION.
            val allProviders = locationManager.allProviders
            if (allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                try {
                    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            intervalMs,
                            0f,
                            networkListener,
                            Looper.getMainLooper(),
                        )
                    }
                } catch (_: SecurityException) {
                    // ACCESS_COARSE_LOCATION not granted; GPS continues alone.
                } catch (_: IllegalArgumentException) {
                    // Provider rejected by the system; GPS continues alone.
                }
            }

            awaitClose {
                locationManager.removeUpdates(gpsListener)
                locationManager.removeUpdates(networkListener)
            }
        } catch (e: SecurityException) {
            // ACCESS_FINE_LOCATION not granted — terminate the flow cleanly.
            close(e)
        }
    }

    private fun Location.toSample(provider: LocationProvider) = LocationSample(
        lat = latitude,
        lng = longitude,
        speedMps = speed.toDouble(),
        altitudeM = altitude,
        bearingDeg = bearing,
        timeMs = time,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else 0.0,
        provider = provider,
    )
}
