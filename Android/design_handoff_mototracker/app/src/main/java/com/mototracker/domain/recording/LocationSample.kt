package com.mototracker.domain.recording

/**
 * A single location fix delivered by a location provider.
 *
 * @param lat        Latitude in decimal degrees.
 * @param lng        Longitude in decimal degrees.
 * @param speedMps   Instantaneous speed in metres per second.
 * @param altitudeM  Altitude above sea level in metres.
 * @param bearingDeg True bearing in degrees (0–360).
 * @param timeMs     Fix timestamp in milliseconds since the Unix epoch.
 * @param accuracyM  Horizontal accuracy radius in metres reported by the location provider.
 *                   A value of 0.0 means the provider did not report accuracy (unknown);
 *                   treat it as acceptable and do not reject on accuracy grounds.
 * @param provider   Which location technology produced this fix. Defaults to [LocationProvider.GPS]
 *                   so all existing call sites keep compiling unchanged.
 */
data class LocationSample(
    val lat: Double,
    val lng: Double,
    val speedMps: Double,
    val altitudeM: Double,
    val bearingDeg: Float,
    val timeMs: Long,
    val accuracyM: Double = 0.0,
    val provider: LocationProvider = LocationProvider.GPS,
)
