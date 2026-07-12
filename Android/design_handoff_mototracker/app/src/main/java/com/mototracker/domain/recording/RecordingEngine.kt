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
 * and [tick] once per elapsed second. Call [reset] before starting a new session.
 * Retrieve live metrics via [snapshot] and the final persisted payload via [buildRoutePayload].
 *
 * @param fuelLper100km Consumption constant used to estimate fuel (default 5.0 L/100km).
 */
class RecordingEngine(private val fuelLper100km: Double = 5.0) {

    private var prevLat: Double? = null
    private var prevLng: Double? = null
    private var prevAlt: Double? = null

    private var distanceKm: Double = 0.0
    private var durationSec: Long = 0L
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
     */
    fun onLocation(sample: LocationSample): RecordingMetrics {
        val speedKmh = sample.speedMps * 3.6
        currentSpeedKmh = speedKmh
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh

        altitudeM = sample.altitudeM
        headingDeg = sample.bearingDeg

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
     * Should be called once per second while recording (not while paused).
     */
    fun tick(elapsedSec: Long) {
        durationSec += elapsedSec
    }

    /** Returns an immutable snapshot of the current [RecordingMetrics]. */
    fun snapshot(): RecordingMetrics {
        val avgSpeed = if (durationSec > 0) distanceKm / (durationSec / 3600.0) else 0.0
        val fuel = distanceKm * fuelLper100km / 100.0
        return RecordingMetrics(
            distanceKm = distanceKm,
            durationSec = durationSec,
            currentSpeedKmh = currentSpeedKmh,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeedKmh,
            currentLeanDeg = currentLeanDeg,
            maxLeanDeg = maxLeanDeg,
            altitudeM = altitudeM,
            elevGainM = elevGainM,
            fuelL = fuel,
            headingDeg = headingDeg,
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
     * Resets all accumulated state. Call before starting a new recording session.
     */
    fun reset() {
        prevLat = null; prevLng = null; prevAlt = null
        distanceKm = 0.0; durationSec = 0L
        currentSpeedKmh = 0.0; maxSpeedKmh = 0.0
        currentLeanDeg = 0.0; maxLeanDeg = 0.0
        altitudeM = 0.0; elevGainM = 0.0; headingDeg = 0f
        pathPoints.clear(); speedOverTime.clear(); elevOverDist.clear()
    }

    /**
     * Exports a full snapshot of all accumulator state as an immutable [RecordingEngineState].
     *
     * Safe to call at any point during a session; the returned object is independent of
     * the engine's mutable lists (copies are taken).
     */
    fun exportState(): RecordingEngineState = RecordingEngineState(
        prevLat = prevLat,
        prevLng = prevLng,
        prevAlt = prevAlt,
        distanceKm = distanceKm,
        durationSec = durationSec,
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
    )

    /**
     * Overwrites all accumulator state from [state], resuming a previously exported session.
     *
     * Replaces the mutable lists in-place and restores all scalar fields exactly.
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
        currentSpeedKmh = state.currentSpeedKmh
        maxSpeedKmh = state.maxSpeedKmh
        currentLeanDeg = state.currentLeanDeg
        maxLeanDeg = state.maxLeanDeg
        altitudeM = state.altitudeM
        elevGainM = state.elevGainM
        headingDeg = state.headingDeg
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
