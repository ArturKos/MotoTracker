package com.mototracker.data.location

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Seam for reverse-geocoding a coordinate to a human-readable area name.
 *
 * Production code uses [AndroidReverseGeocoder]; tests inject a simple fake that
 * returns canned values without any network or device dependency.
 */
interface ReverseGeocoder {

    /**
     * Returns the best available area name for the given coordinate, or `null` when
     * the geocoder has no result or an error occurs.
     *
     * @param lat Latitude in decimal degrees.
     * @param lng Longitude in decimal degrees.
     */
    suspend fun areaName(lat: Double, lng: Double): String?
}

/**
 * Platform implementation of [ReverseGeocoder] backed by [android.location.Geocoder].
 *
 * Prefers [android.location.Address.locality], then falls back to
 * [android.location.Address.subAdminArea], then [android.location.Address.adminArea].
 * Returns `null` on any exception or empty result.
 *
 * The actual geocoding round-trip requires Google Play Services and is therefore 🔬
 * (on-device verification only).
 *
 * @param context Application context.
 */
class AndroidReverseGeocoder @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReverseGeocoder {

    @Suppress("DEPRECATION")
    override suspend fun areaName(lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context)
                val addresses = geocoder.getFromLocation(lat, lng, 1)
                val addr = addresses?.firstOrNull() ?: return@withContext null
                addr.locality ?: addr.subAdminArea ?: addr.adminArea
            } catch (_: Exception) {
                null
            }
        }
}
