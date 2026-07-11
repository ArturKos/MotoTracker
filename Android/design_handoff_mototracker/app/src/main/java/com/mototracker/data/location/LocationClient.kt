package com.mototracker.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import com.mototracker.domain.recording.LocationSample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable seam over the platform GPS provider.
 *
 * The production implementation [FusedLocationClientImpl] wraps [LocationManager].
 * In unit tests, use a fake that emits pre-canned [LocationSample] values.
 */
interface LocationClient {
    /**
     * Hot [Flow] of GPS location samples.
     *
     * Begins requesting location updates on collection; stops when the collector cancels.
     * On-device only (🔬) — use a fake in unit tests.
     *
     * @param intervalMs Minimum time between updates in milliseconds.
     */
    fun locationUpdates(intervalMs: Long = 1_000L): Flow<LocationSample>
}

/**
 * [LocationClient] implementation backed by [LocationManager].
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

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toSample())
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                intervalMs,
                0f,
                listener,
            )
            awaitClose { locationManager.removeUpdates(listener) }
        } catch (e: SecurityException) {
            // ACCESS_FINE_LOCATION not granted — terminate the flow cleanly.
            close(e)
        }
    }

    private fun Location.toSample() = LocationSample(
        lat = latitude,
        lng = longitude,
        speedMps = speed.toDouble(),
        altitudeM = altitude,
        bearingDeg = bearing,
        timeMs = time,
    )
}
