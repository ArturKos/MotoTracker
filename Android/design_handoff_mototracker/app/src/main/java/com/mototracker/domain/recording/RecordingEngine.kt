package com.mototracker.domain.recording

import com.mototracker.domain.fuel.FuelAdjustmentCalculator
import com.mototracker.domain.fuel.FuelAdjustmentMode
import com.mototracker.domain.stats.LeanHistogram
import kotlin.math.abs
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

    /** Odometer reading at the last fill/correction anchor; 0.0 at session start. */
    private var anchorKm: Double = 0.0

    /** Remaining-fuel level at the last fill/correction anchor in litres. */
    private var anchorLitres: Double = 0.0

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

        /**
         * Maximum acceptable horizontal accuracy in metres.
         *
         * A fix whose [LocationSample.accuracyM] exceeds this value is fully dropped:
         * no distance, path point, max-speed update, altitude, or heading is applied.
         * A value of 0.0 in [LocationSample.accuracyM] means the provider did not report
         * accuracy (unknown) and is always accepted.
         */
        const val MAX_ACCURACY_M = 35.0

        /**
         * Implied-speed ceiling in km/h for the teleport-outlier gate.
         *
         * When two consecutive accepted moving fixes imply a ground speed above this
         * threshold (computed via haversine distance divided by elapsed time), the newer
         * fix is treated as a GPS teleport and fully dropped without updating any
         * accumulator or the prev* anchors.
         */
        const val MAX_PLAUSIBLE_SPEED_KMH = 300.0

        /**
         * Elapsed-seconds threshold above which a gap between two consecutive accepted moving
         * fixes is treated as a GPS dropout (tunnel, urban canyon, phone in a bag) rather than
         * real travel.
         *
         * When the gap exceeds this value the bridging haversine distance and elevation gain are
         * NOT added to the odometer/elevation accumulators, preventing a false straight-line
         * segment from inflating the recorded distance. The reacquired fix is still recorded as
         * a path point and becomes the new prev* anchor so that subsequent movement accumulates
         * correctly from the correct position.
         *
         * Typical GPS re-acquisition after a short tunnel takes 2–10 s; 20 s gives comfortable
         * headroom for slower re-acquisition while still catching brief signal losses.
         */
        const val GPS_GAP_SEC = 20.0
    }

    private var prevLat: Double? = null
    private var prevLng: Double? = null
    private var prevAlt: Double? = null
    /** Timestamp of the last accepted moving fix; null until the first moving fix. */
    private var prevTimeMs: Long? = null

    private var distanceKm: Double = 0.0
    private var durationSec: Long = 0L
    private var movingSec: Long = 0L
    private var currentSpeedKmh: Double = 0.0
    private var maxSpeedKmh: Double = 0.0
    private var currentLeanDeg: Double = 0.0
    private var maxLeanDeg: Double = 0.0
    /** Non-negative magnitude of the peak leftward lean this session (positive deg = right). */
    private var maxLeanLeftDeg: Double = 0.0
    /** Non-negative magnitude of the peak rightward lean this session. */
    private var maxLeanRightDeg: Double = 0.0
    private var altitudeM: Double = 0.0
    private var elevGainM: Double = 0.0
    private var headingDeg: Float = 0f

    /** Per-bucket time-in-seconds counts for the lean-angle histogram (Q1). */
    private val leanBucketCounts = IntArray(LeanHistogram.BUCKET_COUNT)

    private val pathPoints = mutableListOf<TrackPoint>()
    private val speedOverTime = mutableListOf<Pair<Long, Double>>()
    private val elevOverDist = mutableListOf<Pair<Double, Double>>()

    /**
     * Integrates a new GPS [sample] into the session state and returns the updated [RecordingMetrics].
     *
     * Processing order:
     * 1. [currentSpeedKmh] is always updated from the sample so the live display reflects the
     *    latest reading even when the fix is subsequently dropped.
     * 2. **Accuracy gate** — if [LocationSample.accuracyM] is known (> 0) and exceeds
     *    [MAX_ACCURACY_M], the fix is fully dropped; no accumulator, max-speed, altitude, heading,
     *    or prev* anchor is touched.
     * 3. **Outlier gate** — if the implied ground speed between the previous accepted moving fix
     *    and this fix exceeds [MAX_PLAUSIBLE_SPEED_KMH], the fix is treated as a GPS teleport
     *    and dropped; prev* anchors are left at the earlier point.
     * 4. Accepted fix: [maxSpeedKmh] is bumped (after the gates so a rejected spike cannot
     *    inflate it), altitude and heading are updated, and the existing [MOVING_THRESHOLD_KMH]
     *    gate governs distance/elevation/path accumulation and the prev* anchors.
     */
    fun onLocation(sample: LocationSample): RecordingMetrics {
        val speedKmh = sample.speedMps * 3.6

        // (1) Always reflect the latest speed on-screen, even for dropped fixes.
        currentSpeedKmh = speedKmh

        // (2) Accuracy gate: drop fix when accuracy is known-bad (accuracyM > 0 means known).
        if (sample.accuracyM > 0.0 && sample.accuracyM > MAX_ACCURACY_M) {
            return snapshot()
        }

        // Capture prev anchors once for both the outlier check and the moving-gate below.
        val pLat = prevLat
        val pLng = prevLng
        val pTimeMs = prevTimeMs

        // (3) Outlier gate: drop implied-teleport fixes (requires a prior accepted moving fix).
        if (pLat != null && pLng != null && pTimeMs != null) {
            val dtSec = (sample.timeMs - pTimeMs) / 1000.0
            if (dtSec > 0) {
                val impliedKmh = haversine(pLat, pLng, sample.lat, sample.lng) / dtSec * 3600.0
                if (impliedKmh > MAX_PLAUSIBLE_SPEED_KMH) {
                    return snapshot()
                }
            }
        }

        // (4) Accepted fix: bump max-speed (gates ensure rejected spikes cannot inflate it),
        //     update live fields, then apply the moving-threshold gate for track accumulation.
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
        altitudeM = sample.altitudeM
        headingDeg = sample.bearingDeg

        if (speedKmh >= MOVING_THRESHOLD_KMH) {
            // Gap detection: a large elapsed time between two accepted moving fixes means GPS
            // dropped out (tunnel, bag, canyon). Do NOT add the bridging distance or elevation
            // gain for the gap — the fix IS recorded and prev* anchors advance normally so
            // movement after reacquisition accumulates from the correct position.
            val isDropout = pLat != null && pLng != null && pTimeMs != null &&
                (sample.timeMs - pTimeMs) / 1000.0 > GPS_GAP_SEC

            if (!isDropout && pLat != null && pLng != null) {
                distanceKm += haversine(pLat, pLng, sample.lat, sample.lng)
            }

            val pAlt = prevAlt
            if (!isDropout && pAlt != null && sample.altitudeM > pAlt) {
                elevGainM += sample.altitudeM - pAlt
            }

            prevLat = sample.lat
            prevLng = sample.lng
            prevAlt = sample.altitudeM
            prevTimeMs = sample.timeMs

            pathPoints += TrackPoint(sample.lat, sample.lng, sample.altitudeM, sample.timeMs)
            speedOverTime += durationSec to speedKmh
            elevOverDist += distanceKm to sample.altitudeM
        }

        return snapshot()
    }

    /**
     * Updates the lean-angle state with [deg] degrees from [LeanAngleCalculator].
     *
     * Tracks the session maximum absolute lean, and separately the maximum left and right lean
     * magnitudes. Positive [deg] means lean right; negative means lean left.
     */
    fun onLean(deg: Double) {
        currentLeanDeg = deg
        val absDeg = abs(deg)
        if (absDeg > maxLeanDeg) maxLeanDeg = absDeg
        if (deg < 0) { val mag = -deg; if (mag > maxLeanLeftDeg) maxLeanLeftDeg = mag }
        if (deg > 0) { if (deg > maxLeanRightDeg) maxLeanRightDeg = deg }
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
        leanBucketCounts[LeanHistogram.bucketIndex(abs(currentLeanDeg))] += elapsedSec.toInt()
    }

    /** Returns an immutable snapshot of the current [RecordingMetrics]. */
    fun snapshot(): RecordingMetrics {
        val avgSpeed = if (durationSec > 0) distanceKm / (durationSec / 3600.0) else 0.0
        val fuel = distanceKm * sessionFuelLper100km / 100.0
        val distanceSinceFullKm = distanceKm - anchorKm
        val fuelSinceAnchorL = distanceSinceFullKm * sessionFuelLper100km / 100.0
        val cap = tankCapacityL
        val remainingFuelL: Double?
        val remainingRangeKm: Double?
        val lowFuel: Boolean
        if (cap != null) {
            val remaining = (anchorLitres - fuelSinceAnchorL).coerceAtLeast(0.0)
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
            maxLeanLeftDeg = maxLeanLeftDeg,
            maxLeanRightDeg = maxLeanRightDeg,
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
        leanHistogramJson = LeanHistogram.encode(leanBucketCounts),
    )

    /**
     * Resets all accumulated state and sets the per-session consumption rate and tank capacity.
     *
     * Always call this before starting a new recording session. Session start implicitly
     * anchors the tank to full (i.e. [anchorKm] is set to 0.0 and [anchorLitres] to tank capacity).
     *
     * @param fuelLper100km Fuel consumption constant for the new session in L/100km.
     *                      Defaults to 5.0 when no bike consumption is configured.
     * @param tankCapacityL Configured tank capacity of the current bike in litres; null when
     *                      the bike has no capacity set (fill-to-full feature inert).
     */
    fun reset(fuelLper100km: Double = 5.0, tankCapacityL: Double? = null) {
        sessionFuelLper100km = fuelLper100km
        this.tankCapacityL = tankCapacityL
        anchorKm = 0.0
        anchorLitres = tankCapacityL ?: 0.0
        prevLat = null; prevLng = null; prevAlt = null; prevTimeMs = null
        distanceKm = 0.0; durationSec = 0L; movingSec = 0L
        currentSpeedKmh = 0.0; maxSpeedKmh = 0.0
        currentLeanDeg = 0.0; maxLeanDeg = 0.0
        maxLeanLeftDeg = 0.0; maxLeanRightDeg = 0.0
        altitudeM = 0.0; elevGainM = 0.0; headingDeg = 0f
        leanBucketCounts.fill(0)
        pathPoints.clear(); speedOverTime.clear(); elevOverDist.clear()
    }

    /**
     * Updates the fuel-model constants without disturbing any accumulated session state.
     *
     * Unlike [reset], this method only overwrites [sessionFuelLper100km] and [tankCapacityL];
     * [anchorKm], [anchorLitres], [distanceKm], and all other accumulators are left completely untouched.
     * This allows the live fuel estimate to be recomputed reactively whenever the bike
     * configuration resolves or changes — including after recording has already started.
     *
     * @param fuelLper100km New consumption constant in L/100km.
     * @param tankCapacityL New tank capacity in litres; null when the bike has no capacity set
     *                      (remaining-fuel feature inert).
     */
    fun updateFuelConfig(fuelLper100km: Double, tankCapacityL: Double?) {
        sessionFuelLper100km = fuelLper100km
        this.tankCapacityL = tankCapacityL
    }

    /**
     * Anchors the tank to full at the current odometer reading.
     *
     * After this call [snapshot] will report [RecordingMetrics.distanceSinceFullKm] as 0.0
     * and [RecordingMetrics.remainingFuelL] as the full tank capacity (when configured).
     * No-op when the engine is in an idle / never-started state.
     */
    fun fillToFull() {
        anchorKm = distanceKm
        anchorLitres = tankCapacityL ?: 0.0
    }

    /**
     * Re-anchors the remaining-fuel baseline using a rider-supplied correction.
     *
     * Computes the current estimated remaining fuel from the existing anchor, applies
     * [FuelAdjustmentCalculator] to produce the new anchor level, then sets
     * [anchorLitres] = result and [anchorKm] = current [distanceKm]. Subsequent
     * [snapshot] calls compute fuel consumption relative to the new anchor.
     *
     * No-op when [tankCapacityL] is null (fill-to-full feature inert for bikes without
     * a configured tank capacity).
     *
     * @param mode  Whether [value] is an absolute level or a signed delta.
     * @param value Correction value in litres.
     */
    fun applyFuelCorrection(mode: FuelAdjustmentMode, value: Double) {
        val cap = tankCapacityL ?: return
        val distanceSinceAnchor = distanceKm - anchorKm
        val fuelSinceAnchor = distanceSinceAnchor * sessionFuelLper100km / 100.0
        val currentRemaining = (anchorLitres - fuelSinceAnchor).coerceAtLeast(0.0)
        anchorLitres = FuelAdjustmentCalculator.newAnchorLitres(mode, value, currentRemaining, cap)
        anchorKm = distanceKm
    }

    /**
     * Exports a full snapshot of all accumulator state as an immutable [RecordingEngineState].
     *
     * Safe to call at any point during a session; the returned object is independent of
     * the engine's mutable lists (copies are taken). The fuel-model fields
     * ([RecordingEngineState.sessionFuelLper100km], [RecordingEngineState.tankCapacityL],
     * [RecordingEngineState.anchorKm], [RecordingEngineState.anchorLitres]) are included so that
     * a B20-resumed ride keeps its corrected fuel anchor.
     */
    fun exportState(): RecordingEngineState = RecordingEngineState(
        prevLat = prevLat,
        prevLng = prevLng,
        prevAlt = prevAlt,
        prevTimeMs = prevTimeMs,
        distanceKm = distanceKm,
        durationSec = durationSec,
        movingSec = movingSec,
        currentSpeedKmh = currentSpeedKmh,
        maxSpeedKmh = maxSpeedKmh,
        currentLeanDeg = currentLeanDeg,
        maxLeanDeg = maxLeanDeg,
        maxLeanLeftDeg = maxLeanLeftDeg,
        maxLeanRightDeg = maxLeanRightDeg,
        altitudeM = altitudeM,
        elevGainM = elevGainM,
        headingDeg = headingDeg,
        pathPoints = pathPoints.toList(),
        speedOverTime = speedOverTime.toList(),
        elevOverDist = elevOverDist.toList(),
        sessionFuelLper100km = sessionFuelLper100km,
        tankCapacityL = tankCapacityL,
        anchorKm = anchorKm,
        anchorLitres = anchorLitres,
        leanBucketCounts = leanBucketCounts.toList(),
    )

    /**
     * Overwrites all accumulator state from [state], resuming a previously exported session.
     *
     * Replaces the mutable lists in-place and restores all scalar fields exactly, including
     * the fuel-model fields ([sessionFuelLper100km], [tankCapacityL], [anchorKm], [anchorLitres]) so
     * that a B20-resumed ride continues with the correct fuel anchor.
     * After calling this, [snapshot] and [buildRoutePayload] reflect the restored values.
     *
     * @param state Previously exported [RecordingEngineState].
     */
    fun restore(state: RecordingEngineState) {
        prevLat = state.prevLat
        prevLng = state.prevLng
        prevAlt = state.prevAlt
        prevTimeMs = state.prevTimeMs
        distanceKm = state.distanceKm
        durationSec = state.durationSec
        movingSec = state.movingSec
        currentSpeedKmh = state.currentSpeedKmh
        maxSpeedKmh = state.maxSpeedKmh
        currentLeanDeg = state.currentLeanDeg
        maxLeanDeg = state.maxLeanDeg
        maxLeanLeftDeg = state.maxLeanLeftDeg
        maxLeanRightDeg = state.maxLeanRightDeg
        altitudeM = state.altitudeM
        elevGainM = state.elevGainM
        headingDeg = state.headingDeg
        sessionFuelLper100km = state.sessionFuelLper100km
        tankCapacityL = state.tankCapacityL
        anchorKm = state.anchorKm
        anchorLitres = state.anchorLitres
        leanBucketCounts.fill(0)
        state.leanBucketCounts.forEachIndexed { i, v -> if (i < leanBucketCounts.size) leanBucketCounts[i] = v }
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
        return pathPoints.joinToString(separator = ",", prefix = "[", postfix = "]") { pt ->
            buildString {
                append("""{"lat":${pt.lat},"lng":${pt.lng},"ele":${pt.ele}""")
                if (pt.t != null) append(""","t":${pt.t}""")
                append("}")
            }
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
