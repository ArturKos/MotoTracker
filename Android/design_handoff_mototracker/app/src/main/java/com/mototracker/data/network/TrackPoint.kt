package com.mototracker.data.network

/**
 * A single GPS sample used as input to (and output from) the OSRM map-matching API.
 *
 * @param lat          WGS-84 latitude in decimal degrees.
 * @param lon          WGS-84 longitude in decimal degrees.
 * @param timestampSec Unix timestamp in seconds; null if not available.
 */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val timestampSec: Long? = null,
)
