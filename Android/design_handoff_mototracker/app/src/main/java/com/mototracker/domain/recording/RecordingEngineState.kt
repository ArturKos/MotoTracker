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
 * @param prevTimeMs            Timestamp in ms of the last accepted moving fix, used by the
 *                              outlier gate; null before the first moving fix. Defaults to null
 *                              for backward-compatibility with pre-N2 snapshots.
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
 * @param pathPoints            Ordered [TrackPoint] values (lat, lng, ele, t) forming the GPS track.
 *                              Pre-N1 snapshots use ele=0.0 and t=null per backward-compat defaults.
 * @param speedOverTime         Pairs of (elapsedSec, speedKmh) for the speed-over-time chart.
 * @param elevOverDist          Pairs of (distanceKm, altitudeM) for the elevation-profile chart.
 * @param sessionFuelLper100km  Per-session fuel consumption rate in L/100km.
 * @param tankCapacityL         Configured bike tank capacity in litres; null if not set.
 * @param anchorKm              Odometer reading at the last fill/correction anchor; 0.0 at session start.
 * @param anchorLitres          Remaining-fuel level at the last fill/correction anchor (litres).
 *                              Defaults to 0.0; the engine treats null [tankCapacityL] as inert so the
 *                              default is only meaningful when [tankCapacityL] is non-null.
 * @param leanBucketCounts      Per-bucket time-in-seconds counts for the lean-angle histogram (Q1);
 *                              length 5, defaults to all-zeros for backward compatibility with pre-Q1 snapshots.
 */
data class RecordingEngineState(
    val prevLat: Double?,
    val prevLng: Double?,
    val prevAlt: Double?,
    val prevTimeMs: Long? = null,
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
    val pathPoints: List<TrackPoint>,
    val speedOverTime: List<Pair<Long, Double>>,
    val elevOverDist: List<Pair<Double, Double>>,
    val sessionFuelLper100km: Double = 5.0,
    val tankCapacityL: Double? = null,
    val anchorKm: Double = 0.0,
    val anchorLitres: Double = 0.0,
    val leanBucketCounts: List<Int> = List(5) { 0 },
)
