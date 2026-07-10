package com.mototracker.domain.recording

/**
 * Final output of a recording session — metrics plus serialised JSON payloads
 * suitable for persisting to [com.mototracker.data.model.Route].
 *
 * @param metrics          Cumulative metrics at session end.
 * @param pathJson         JSON array of `{"lat":…,"lng":…}` coordinate objects.
 * @param speedJson        JSON array of `{"t":…,"v":…}` speed-over-time objects (t = seconds, v = km/h).
 * @param elevProfileJson  JSON array of `{"d":…,"a":…}` elevation-over-distance objects (d = km, a = metres).
 */
data class RecordingResult(
    val metrics: RecordingMetrics,
    val pathJson: String,
    val speedJson: String,
    val elevProfileJson: String,
)
