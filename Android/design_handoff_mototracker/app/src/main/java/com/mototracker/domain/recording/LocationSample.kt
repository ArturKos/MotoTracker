package com.mototracker.domain.recording

/**
 * A single GPS fix delivered by the location provider.
 *
 * @param lat        Latitude in decimal degrees.
 * @param lng        Longitude in decimal degrees.
 * @param speedMps   Instantaneous speed in metres per second.
 * @param altitudeM  Altitude above sea level in metres.
 * @param bearingDeg True bearing in degrees (0–360).
 * @param timeMs     Fix timestamp in milliseconds since the Unix epoch.
 */
data class LocationSample(
    val lat: Double,
    val lng: Double,
    val speedMps: Double,
    val altitudeM: Double,
    val bearingDeg: Float,
    val timeMs: Long,
)
