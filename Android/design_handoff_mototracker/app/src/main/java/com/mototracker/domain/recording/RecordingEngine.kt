package com.mototracker.domain.recording

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure accumulator for a recording session — no Android dependencies.
 *
 * Call [onLocation] for each GPS fix, [onLean] for each gravity-sensor reading,
 * and [tick] once per elapsed second. Call [reset] before starting a new session,
 * optionally supplying the bike's per-session consumption and tank capacity. Retrieve
 * live metrics via [snapshot] and the final persisted payload via [buildRoutePayload].
 *
 * The "fill to full" feature anchors the tank to full at a specific odometer reading;
 * call [fillToFull] when the rider refuels. Remaining fuel and range are computed
 * relative to the most-recent fill event (or session start when no fill has occurred).
 *
 * @param fuelLper100km Initial consumption constant (default 5.0 L/100km). Used before the
 *                      first [reset] call and for engines that are created with a pre-known
 *                      rate (e.g. in tests).
 */
class RecordingEngine(fuelLper100km: Double = 5.0) {

    /** Consumption constant for the current or most-recently-started session. */
    private var sessionFuelLper100km: Double = fuelLper100km

    /** Configured tank capacity in litres; null when the current bike has no capacity set. */
    private var tankCapacityL: Double? = null

    /** Accumulated distance in km at the last 'fill to full' event; 0.0 at session start. */
    private var fillAnchorKm: Double = 0.0

    companion object {
        /** Remaining-fuel fraction below which the low-fuel warning is raised (15 %). */
        const val LOW_FUEL_FRACTION = 0.15

        /**
         * Minimum speed in km/h above which a GPS fix counts as motion.
         *
         * Fixes below this threshold are ignored for distance/track accumulation to
         * prevent parked-bike GPS jitter from polluting the recorded path and odometer.
         */
        const val MOVING_THRESHOLD_KMH = 2.0
    }

    private var prevLat: Double? = null
    private var prevLng: Double? = null
    private var prevAlt: Double? = null

    private var distanceKm: Double = 0.0
    private var durationSec: Long = 0L
    private var movingSec: Long = 0L
    private var currentSpeedKmh: Double = 0.0
    private var maxSpeedKmh: Double = 0.0
    private var currentLeanDeg: Double = 0.0
    private var maxLeanDeg: Double = 0.0
    private var altitudeM: Double = 0.0
    private var elevGainM: Double = 0.0
    private var headingDeg: Float = 0f

    private val pathPoints = mutableListOf<Pair<Double, Double>>()
    private val speedOverTime = mutableListOf<Pair<Long, Double>>()
    private val elevOverDist = mutableListOf<Pair<Double, Double>>()

    /**
     * Integrates a new GPS [sample] into the session state and returns the updated [RecordingMetrics].
     *
     * Live display values (speed, max speed, altitude, heading) are updated on every fix.
     * Track accumulation (distance, elevation gain, path points, chart series, and prev* anchors)
     * is gated on [MOVING_THRESHOLD_KMH]: fixes below the threshold are silently dropped from the
     * persisted track so that a parked bike does not accumulate jitter distance or ghost points.
     */
    fun onLocation(sample: LocationSample): RecordingMetrics {
        val speedKmh = sample.speedMps * 3.6
        currentSpeedKmh = speedKmh
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh

        altitudeM = sample.altitudeM
        headingDeg = sample.bearingDeg

        if (speedKmh >= MOVING_THRESHOLD_KMH) {
            val pLat = prevLat
            val pLng = prevLng
            if (pLat != null && pLng != null) {
                distanceKm += haversine(pLat, pLng, sample.lat, sample.lng)
            }

            val pAlt = prevAlt
            if (pAlt != null && sample.altitudeM > pAlt) {
                elevGainM += sample.altitudeM - pAlt
            }

            prevLat = sample.lat
            prevLng = sample.lng
            prevAlt = sample.altitudeM

            pathPoints += sample.lat to sample.lng
            speedOverTime += durationSec to speedKmh
            elevOverDist += distanceKm to sample.altitudeM
        }

        return snapshot()
    }

    /**
     * Updates the lean-angle state with [deg] degrees from [LeanAngleCalculator].
     *
     * Tracks the session maximum absolute lean.
     */
    fun onLean(deg: Double) {
        currentLeanDeg = deg
        val abs = kotlin.math.abs(deg)
        if (abs > maxLeanDeg) maxLeanDeg = abs
    }

    /**
     * Advances elapsed recording time by [elapsedSec] seconds.
     *
     * Also advances [movingSec] by the same amount when the current speed is at or above
     * [MOVING_THRESHOLD_KMH], so the moving-time counter only ticks while the bike is in motion.
     * Should be called once per second while recording (not while paused).
     */
    fun tick(elapsedSec: Long) {
        durationSec += elapsedSec
        if (currentSpeedKmh >= MOVING_THRESHOLD_KMH) movingSec += elapsedSec
    }

    /** Returns an immutable snapshot of the current [RecordingMetrics]. */
    fun snapshot(): RecordingMetrics {
        val avgSpeed = if (durationSec > 0) distanceKm / (durationSec / 3600.0) else 0.0
        val fuel = distanceKm * sessionFuelLper100km / 100.0
        val distanceSinceFullKm = distanceKm - fillAnchorKm
        val fuelSinceFullL = distanceSinceFullKm * sessionFuelLper100km / 100.0
        val cap = tankCapacityL
        val remainingFuelL: Double?
        val remainingRangeKm: Double?
        val lowFuel: Boolean
        if (cap != null) {
            val remaining = (cap - fuelSinceFullL).coerceAtLeast(0.0)
            remainingFuelL = remaining
            remainingRangeKm = if (sessionFuelLper100km > 0.0) remaining / sessionFuelLper100km * 100.0 else null
            lowFuel = remaining <= LOW_FUEL_FRACTION * cap
        } else {
            remainingFuelL = null
            remainingRangeKm = null
            lowFuel = false
        }
        return RecordingMetrics(
            distanceKm = distanceKm,
            durationSec = durationSec,
            movingSec = movingSec,
            currentSpeedKmh = currentSpeedKmh,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeedKmh,
            currentLeanDeg = currentLeanDeg,
            maxLeanDeg = maxLeanDeg,
            altitudeM = altitudeM,
            elevGainM = elevGainM,
            fuelL = fuel,
            headingDeg = headingDeg,
            distanceSinceFullKm = distanceSinceFullKm,
            tankCapacityL = cap,
            remainingFuelL = remainingFuelL,
            remainingRangeKm = remainingRangeKm,
            lowFuel = lowFuel,
        )
    }

    /**
     * Builds the final [RecordingResult] with metrics and serialised JSON profile arrays.
     *
     * Call once at session end, after all [onLocation] / [tick] calls have been made.
     */
    fun buildRoutePayload(): RecordingResult = RecordingResult(
        metrics = snapshot(),
        pathJson = buildPathJson(),
        speedJson = buildSpeedJson(),
        elevProfileJson = buildElevJson(),
    )

    /**
     * Resets all accumulated state and sets the per-session consumption rate and tank capacity.
     *
     * Always call this before starting a new recording session. Session start implicitly
     * anchors the tank to full (i.e. [fillAnchorKm] is set to 0.0).
     *
     * @param fuelLper100km Fuel consumption constant for the new session in L/100km.
     *                      Defaults to 5.0 when no bike consumption is configured.
     * @param tankCapacityL Configured tank capacity of the current bike in litres; null when
     *                      the bike has no capacity set (fill-to-full feature inert).
     */
    fun reset(fuelLper100km: Double = 5.0, tankCapacityL: Double? = null) {
        sessionFuelLper100km = fuelLper100km
        this.tankCapacityL = tankCapacityL
        fillAnchorKm = 0.0
        prevLat = null; prevLng = null; prevAlt = null
        distanceKm = 0.0; durationSec = 0L; movingSec = 0L
        currentSpeedKmh = 0.0; maxSpeedKmh = 0.0
        currentLeanDeg = 0.0; maxLeanDeg = 0.0
        altitudeM = 0.0; elevGainM = 0.0; headingDeg = 0f
        pathPoints.clear(); speedOverTime.clear(); elevOverDist.clear()
    }

    /**
     * Anchors the tank to full at the current odometer reading.
     *
     * After this call [snapshot] will report [RecordingMetrics.distanceSinceFullKm] as 0.0
     * and [RecordingMetrics.remainingFuelL] as the full tank capacity (when configured).
     * No-op when the engine is in an idle / never-started state.
     */
    fun fillToFull() {
        fillAnchorKm = distanceKm
    }

    /**
     * Exports a full snapshot of all accumulator state as an immutable [RecordingEngineState].
     *
     * Safe to call at any point during a session; the returned object is independent of
     * the engine's mutable lists (copies are taken). The fuel-model fields
     * ([RecordingEngineState.sessionFuelLper100km], [RecordingEngineState.tankCapacityL],
     * [RecordingEngineState.fillAnchorKm]) are included so that a B20-resumed ride keeps
     * its fill-to-full anchor.
     */
    fun exportState(): RecordingEngineState = RecordingEngineState(
        prevLat = prevLat,
        prevLng = prevLng,
        prevAlt = prevAlt,
        distanceKm = distanceKm,
        durationSec = durationSec,
        movingSec = movingSec,
        currentSpeedKmh = currentSpeedKmh,
        maxSpeedKmh = maxSpeedKmh,
        currentLeanDeg = currentLeanDeg,
        maxLeanDeg = maxLeanDeg,
        altitudeM = altitudeM,
        elevGainM = elevGainM,
        headingDeg = headingDeg,
        pathPoints = pathPoints.toList(),
        speedOverTime = speedOverTime.toList(),
        elevOverDist = elevOverDist.toList(),
        sessionFuelLper100km = sessionFuelLper100km,
        tankCapacityL = tankCapacityL,
        fillAnchorKm = fillAnchorKm,
    )

    /**
     * Overwrites all accumulator state from [state], resuming a previously exported session.
     *
     * Replaces the mutable lists in-place and restores all scalar fields exactly, including
     * the fuel-model fields ([sessionFuelLper100km], [tankCapacityL], [fillAnchorKm]) so
     * that a B20-resumed ride continues with the correct fill-to-full anchor.
     * After calling this, [snapshot] and [buildRoutePayload] reflect the restored values.
     *
     * @param state Previously exported [RecordingEngineState].
     */
    fun restore(state: RecordingEngineState) {
        prevLat = state.prevLat
        prevLng = state.prevLng
        prevAlt = state.prevAlt
        distanceKm = state.distanceKm
        durationSec = state.durationSec
        movingSec = state.movingSec
        currentSpeedKmh = state.currentSpeedKmh
        maxSpeedKmh = state.maxSpeedKmh
        currentLeanDeg = state.currentLeanDeg
        maxLeanDeg = state.maxLeanDeg
        altitudeM = state.altitudeM
        elevGainM = state.elevGainM
        headingDeg = state.headingDeg
        sessionFuelLper100km = state.sessionFuelLper100km
        tankCapacityL = state.tankCapacityL
        fillAnchorKm = state.fillAnchorKm
        pathPoints.clear(); pathPoints.addAll(state.pathPoints)
        speedOverTime.clear(); speedOverTime.addAll(state.speedOverTime)
        elevOverDist.clear(); elevOverDist.addAll(state.elevOverDist)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Haversine great-circle distance between two WGS-84 coordinates, in kilometres. */
    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun buildPathJson(): String {
        if (pathPoints.isEmpty()) return "[]"
        return pathPoints.joinToString(separator = ",", prefix = "[", postfix = "]") {
            """{"lat":${it.first},"lng":${it.second}}"""
        }
    }

    private fun buildSpeedJson(): String {
        if (speedOverTime.isEmpty()) return "[]"
        return speedOverTime.joinToString(separator = ",", prefix = "[", postfix = "]") {
            """{"t":${it.first},"v":${it.second}}"""
        }
    }

    private fun buildElevJson(): String {
        if (elevOverDist.isEmpty()) return "[]"
        return elevOverDist.joinToString(separator = ",", prefix = "[", postfix = "]") {
            """{"d":${it.first},"a":${it.second}}"""
        }
    }
}
