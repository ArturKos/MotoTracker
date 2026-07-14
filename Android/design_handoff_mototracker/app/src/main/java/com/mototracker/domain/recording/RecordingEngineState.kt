package com.mototracker.domain.recording

/**
 * Immutable snapshot of all accumulator state inside [RecordingEngine].
 *
 * Used by B20 to persist an in-progress recording session durably so it can be
 * restored after a process death (START_STICKY service kill).
 *
 * @param prevLat               Latitude of the previous GPS fix, or null before the first fix.
 * @param prevLng               Longitude of the previous GPS fix, or null before the first fix.
 * @param prevAlt               Altitude of the previous GPS fix, or null before the first fix.
 * @param distanceKm            Accumulated distance in kilometres.
 * @param durationSec           Elapsed recording time in seconds.
 * @param movingSec             Elapsed time in motion in seconds; defaults to 0 for backward-compat with older snapshots.
 * @param currentSpeedKmh       Current speed in km/h at the last GPS fix.
 * @param maxSpeedKmh           Maximum speed in km/h reached during the session.
 * @param currentLeanDeg        Most-recent lean angle in degrees from the gravity sensor.
 * @param maxLeanDeg            Maximum absolute lean angle in degrees during the session.
 * @param maxLeanLeftDeg        Non-negative magnitude of the peak leftward lean; defaults to 0 for backward-compat with older snapshots.
 * @param maxLeanRightDeg       Non-negative magnitude of the peak rightward lean; defaults to 0 for backward-compat with older snapshots.
 * @param altitudeM             Current altitude in metres from the last GPS fix.
 * @param elevGainM             Accumulated elevation gain in metres.
 * @param headingDeg            Current GPS bearing in degrees (0–360, 0 = North).
 * @param pathPoints            Ordered (lat, lng) pairs forming the GPS track.
 * @param speedOverTime         Pairs of (elapsedSec, speedKmh) for the speed-over-time chart.
 * @param elevOverDist          Pairs of (distanceKm, altitudeM) for the elevation-profile chart.
 * @param sessionFuelLper100km  Per-session fuel consumption rate in L/100km.
 * @param tankCapacityL         Configured bike tank capacity in litres; null if not set.
 * @param fillAnchorKm          Accumulated distance at the last 'fill to full' event; 0.0 at session start.
 */
data class RecordingEngineState(
    val prevLat: Double?,
    val prevLng: Double?,
    val prevAlt: Double?,
    val distanceKm: Double,
    val durationSec: Long,
    val movingSec: Long = 0L,
    val currentSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val currentLeanDeg: Double,
    val maxLeanDeg: Double,
    val maxLeanLeftDeg: Double = 0.0,
    val maxLeanRightDeg: Double = 0.0,
    val altitudeM: Double,
    val elevGainM: Double,
    val headingDeg: Float,
    val pathPoints: List<Pair<Double, Double>>,
    val speedOverTime: List<Pair<Long, Double>>,
    val elevOverDist: List<Pair<Double, Double>>,
    val sessionFuelLper100km: Double = 5.0,
    val tankCapacityL: Double? = null,
    val fillAnchorKm: Double = 0.0,
)
