package com.mototracker.domain.recording

/**
 * Immutable snapshot of all accumulator state inside [RecordingEngine].
 *
 * Used by B20 to persist an in-progress recording session durably so it can be
 * restored after a process death (START_STICKY service kill).
 *
 * @param prevLat         Latitude of the previous GPS fix, or null before the first fix.
 * @param prevLng         Longitude of the previous GPS fix, or null before the first fix.
 * @param prevAlt         Altitude of the previous GPS fix, or null before the first fix.
 * @param distanceKm      Accumulated distance in kilometres.
 * @param durationSec     Elapsed recording time in seconds.
 * @param currentSpeedKmh Current speed in km/h at the last GPS fix.
 * @param maxSpeedKmh     Maximum speed in km/h reached during the session.
 * @param currentLeanDeg  Most-recent lean angle in degrees from the gravity sensor.
 * @param maxLeanDeg      Maximum absolute lean angle in degrees during the session.
 * @param altitudeM       Current altitude in metres from the last GPS fix.
 * @param elevGainM       Accumulated elevation gain in metres.
 * @param headingDeg      Current GPS bearing in degrees (0–360, 0 = North).
 * @param pathPoints      Ordered (lat, lng) pairs forming the GPS track.
 * @param speedOverTime   Pairs of (elapsedSec, speedKmh) for the speed-over-time chart.
 * @param elevOverDist    Pairs of (distanceKm, altitudeM) for the elevation-profile chart.
 */
data class RecordingEngineState(
    val prevLat: Double?,
    val prevLng: Double?,
    val prevAlt: Double?,
    val distanceKm: Double,
    val durationSec: Long,
    val currentSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val currentLeanDeg: Double,
    val maxLeanDeg: Double,
    val altitudeM: Double,
    val elevGainM: Double,
    val headingDeg: Float,
    val pathPoints: List<Pair<Double, Double>>,
    val speedOverTime: List<Pair<Long, Double>>,
    val elevOverDist: List<Pair<Double, Double>>,
)
