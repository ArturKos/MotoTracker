package com.mototracker.domain.recording

/**
 * A single point in a recorded GPS track, enriched with elevation and a fix timestamp.
 *
 * This replaces raw `Pair<Double, Double>` for track geometry and is the canonical
 * in-memory and serialised representation of a path point from format version N1 onward.
 * Older points recorded without elevation or timestamp data are represented with
 * default values ([ele] = 0.0, [t] = null) so that deserialization never fails.
 *
 * @param lat Latitude in decimal degrees (WGS-84).
 * @param lng Longitude in decimal degrees (WGS-84).
 * @param ele Altitude above sea level in metres; 0.0 when the GPS fix had no altitude data.
 * @param t   Epoch-millisecond timestamp of the GPS fix, or null for legacy points recorded
 *            before per-point timestamps were introduced (N1).
 */
data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val ele: Double = 0.0,
    val t: Long? = null,
)
