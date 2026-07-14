package com.mototracker.domain.recording

/**
 * Immutable snapshot of all cumulative recording metrics at a point in time.
 *
 * Computed entirely from [RecordingEngine] state — no Android dependencies.
 *
 * @param distanceKm          Total distance travelled in kilometres.
 * @param durationSec         Elapsed recording time in seconds (excludes paused intervals).
 * @param movingSec           Elapsed time in motion, in seconds — total time above the moving threshold.
 * @param currentSpeedKmh     Speed at the most recent GPS fix, in km/h.
 * @param avgSpeedKmh         Average speed over the entire recording (distance / time).
 * @param maxSpeedKmh         Peak speed recorded during the session.
 * @param currentLeanDeg      Most recent lean angle from the gravity sensor, in degrees.
 * @param maxLeanDeg          Maximum lean angle recorded during the session.
 * @param maxLeanLeftDeg      Non-negative magnitude of the peak leftward lean this session (positive = right convention).
 * @param maxLeanRightDeg     Non-negative magnitude of the peak rightward lean this session.
 * @param altitudeM           Altitude at the most recent GPS fix, metres above sea level.
 * @param elevGainM           Cumulative positive elevation gain in metres.
 * @param fuelL               Estimated fuel consumed based on distance and configurable L/100km.
 * @param headingDeg          True bearing at the most recent GPS fix (0–360°).
 * @param distanceSinceFullKm Distance accumulated since the last 'fill to full' event, in km.
 * @param tankCapacityL       Configured tank capacity for the current bike in litres; null when not configured.
 * @param remainingFuelL      Estimated fuel remaining in the tank in litres; null when [tankCapacityL] is null.
 * @param remainingRangeKm    Estimated remaining range in kilometres; null when capacity or consumption is unknown.
 * @param lowFuel             True when [remainingFuelL] is at or below 15 % of [tankCapacityL].
 */
data class RecordingMetrics(
    val distanceKm: Double = 0.0,
    val durationSec: Long = 0L,
    val movingSec: Long = 0L,
    val currentSpeedKmh: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val currentLeanDeg: Double = 0.0,
    val maxLeanDeg: Double = 0.0,
    val maxLeanLeftDeg: Double = 0.0,
    val maxLeanRightDeg: Double = 0.0,
    val altitudeM: Double = 0.0,
    val elevGainM: Double = 0.0,
    val fuelL: Double = 0.0,
    val headingDeg: Float = 0f,
    val distanceSinceFullKm: Double = 0.0,
    val tankCapacityL: Double? = null,
    val remainingFuelL: Double? = null,
    val remainingRangeKm: Double? = null,
    val lowFuel: Boolean = false,
)
