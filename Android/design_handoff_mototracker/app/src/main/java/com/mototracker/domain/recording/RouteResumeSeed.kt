package com.mototracker.domain.recording

import com.mototracker.data.model.Route
import com.mototracker.ui.map.TrackGeometry
import org.json.JSONArray

/**
 * Converts a persisted [Route] into a [RecordingEngineState] that can be fed into
 * [RecordingEngine.restore] when the rider continues an existing saved route.
 *
 * This is a pure, stateless object — no Android runtime dependencies — making it
 * fully unit-testable without a device.
 */
object RouteResumeSeed {

    /**
     * Builds a [RecordingEngineState] seeded from the metrics and trace data stored
     * in [route].
     *
     * Mapping rules:
     * - `pathPoints`    — parsed from [Route.pathJson] (`[{"lat","lng"}]`).
     * - `speedOverTime` — parsed from [Route.speedJson] (`[{"t":Long,"v":Double}]`).
     * - `elevOverDist`  — parsed from [Route.elevProfileJson] (`[{"d":Double,"a":Double}]`).
     * - `distanceKm`    — [Route.km] (exact stored value, not re-derived from path).
     * - `durationSec`   — [Route.durSec].
     * - `movingSec`     — equals `durSec` (best-effort; not separately stored).
     * - `maxSpeedKmh`   — [Route.max].
     * - `maxLeanDeg`    — [Route.lean].
     * - `maxLeanLeftDeg`  — [Route.maxLeanLeftDeg].
     * - `maxLeanRightDeg` — [Route.maxLeanRightDeg].
     * - `elevGainM`     — [Route.elev].
     * - `prevLat`/`prevLng` — the **last** path point so the haversine accumulator continues
     *   correctly from where the original recording ended.
     * - `prevAlt`       — altitude of the last elevation profile entry; `null` when empty.
     * - `altitudeM`     — same as `prevAlt`, or 0.0 when no elevation data is stored.
     * - Fuel-model fields (`sessionFuelLper100km`, `tankCapacityL`) are left at defaults;
     *   call [RecordingEngine.updateFuelConfig] immediately after [RecordingEngine.restore]
     *   to apply the current bike's config.
     * - `fillAnchorKm`  — 0.0 (fuel accounting spans the whole resumed route from the
     *   beginning; the engine re-anchors on each confirmed refuel).
     *
     * @param route The saved route whose accumulated data will seed the engine state.
     * @return A fully-populated [RecordingEngineState] ready for [RecordingEngine.restore].
     */
    fun fromRoute(route: Route): RecordingEngineState {
        val pathPoints = TrackGeometry.parsePathJson(route.pathJson)
            .map { it.lat to it.lon }

        val speedOverTime = parseSpeedJson(route.speedJson)
        val elevOverDist = parseElevJson(route.elevProfileJson)

        val lastPoint = pathPoints.lastOrNull()
        val lastAlt = elevOverDist.lastOrNull()?.second

        return RecordingEngineState(
            prevLat = lastPoint?.first,
            prevLng = lastPoint?.second,
            prevAlt = lastAlt,
            distanceKm = route.km,
            durationSec = route.durSec,
            movingSec = route.durSec,
            currentSpeedKmh = 0.0,
            maxSpeedKmh = route.max,
            currentLeanDeg = 0.0,
            maxLeanDeg = route.lean,
            maxLeanLeftDeg = route.maxLeanLeftDeg,
            maxLeanRightDeg = route.maxLeanRightDeg,
            altitudeM = lastAlt ?: 0.0,
            elevGainM = route.elev,
            headingDeg = 0f,
            pathPoints = pathPoints,
            speedOverTime = speedOverTime,
            elevOverDist = elevOverDist,
            sessionFuelLper100km = 5.0,
            tankCapacityL = null,
            fillAnchorKm = 0.0,
        )
    }

    // ── JSON parsers ─────────────────────────────────────────────────────────

    private fun parseSpeedJson(speedJson: String?): List<Pair<Long, Double>> {
        if (speedJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(speedJson)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                obj.getLong("t") to obj.getDouble("v")
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseElevJson(elevJson: String?): List<Pair<Double, Double>> {
        if (elevJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(elevJson)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                obj.getDouble("d") to obj.getDouble("a")
            }
        } catch (_: Exception) { emptyList() }
    }
}
