package com.mototracker.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable seam for receiving GNSS satellite status updates.
 *
 * The production implementation [AndroidGnssStatusClient] wraps [LocationManager].
 * In unit tests, use a fake that emits pre-canned [GnssSatelliteCount] values.
 */
interface GnssStatusClient {

    /**
     * Hot [Flow] of satellite count snapshots.
     *
     * Begins registering for GNSS callbacks on collection; unregisters when the collector cancels.
     * On-device only (🔬) — use a fake in unit tests.
     */
    fun satelliteCounts(): Flow<GnssSatelliteCount>
}

/**
 * [GnssStatusClient] implementation backed by [LocationManager.registerGnssStatusCallback].
 *
 * ACCESS_FINE_LOCATION must be granted before collecting the flow. Uses the Handler/Looper
 * overload to avoid the deprecated executor-free variant lint warning.
 *
 * @param context Application context supplied by Hilt.
 */
@Singleton
class AndroidGnssStatusClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : GnssStatusClient {

    @SuppressLint("MissingPermission")
    override fun satelliteCounts(): Flow<GnssSatelliteCount> = callbackFlow {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                trySend(
                    GnssSatelliteCounter.count(status.satelliteCount) { status.usedInFix(it) },
                )
            }
        }

        try {
            locationManager.registerGnssStatusCallback(
                callback,
                Handler(Looper.getMainLooper()),
            )
            awaitClose { locationManager.unregisterGnssStatusCallback(callback) }
        } catch (e: SecurityException) {
            // ACCESS_FINE_LOCATION not granted — terminate the flow cleanly.
            close(e)
        }
    }
}
