package com.mototracker.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val MATCH_PATH = "/match/v1/driving/"
private const val CHUNK_SIZE = 100

/**
 * [GpsCorrectionClient] implementation that sends GPS traces to an OSRM instance
 * via its `/match/v1/driving/…` endpoint and parses the GeoJSON response.
 *
 * Long rides are automatically chunked into batches of [CHUNK_SIZE] points; the
 * road-snapped geometry and metrics from all chunks are stitched into a single
 * [CorrectionOutcome.Matched] result.
 *
 * **Coordinate order**: OSRM expects `lon,lat` in the URL path. The internal
 * [TrackPoint] and `pathJson` format both use `lat`/`lon` (or `lat`/`lng`), so
 * coordinates are reversed when building the request and reverted when parsing
 * the GeoJSON response.
 *
 * @param transport Injectable HTTP transport; replaced by a fake in unit tests.
 */
@Singleton
class OsrmGpsCorrectionClient @Inject constructor(
    private val transport: HttpTransport,
) : GpsCorrectionClient {

    override suspend fun match(
        osrmBaseUrl: String,
        points: List<TrackPoint>,
    ): Result<CorrectionOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            if (points.isEmpty()) return@runCatching CorrectionOutcome.NoMatch

            val chunks = points.chunked(CHUNK_SIZE)
            val allMatchedPoints = mutableListOf<TrackPoint>()
            var totalNonNullTracepoints = 0
            var weightedConfidenceSum = 0.0

            for (chunk in chunks) {
                val url = buildUrl(osrmBaseUrl, chunk)
                val response = transport.execute(HttpRequest(url = url, method = "GET")).getOrThrow()
                check(response.code in 200..299) { "OSRM HTTP ${response.code}" }

                val result = parseChunk(response.body, chunk.size) ?: continue
                allMatchedPoints += result.points
                totalNonNullTracepoints += result.nonNullTracepoints
                weightedConfidenceSum += result.confidence * chunk.size
            }

            if (allMatchedPoints.isEmpty()) return@runCatching CorrectionOutcome.NoMatch

            val overallConfidence = weightedConfidenceSum / points.size
            val matchedFraction = totalNonNullTracepoints.toDouble() / points.size

            CorrectionOutcome.Matched(
                points = allMatchedPoints,
                confidence = overallConfidence,
                matchedFraction = matchedFraction,
            )
        }
    }

    /**
     * Builds the OSRM match URL for [chunk].
     *
     * Coordinates are emitted as `lon,lat` pairs (OSRM convention) joined by ';'.
     * Optional `timestamps` query parameter is appended when any point carries a
     * non-null [TrackPoint.timestampSec].
     */
    private fun buildUrl(baseUrl: String, chunk: List<TrackPoint>): String {
        val coords = chunk.joinToString(";") { "${it.lon},${it.lat}" }
        val sb = StringBuilder("$baseUrl$MATCH_PATH$coords")
        sb.append("?geometries=geojson&overview=full&tidy=true&gaps=split")

        val hasTimestamps = chunk.any { it.timestampSec != null }
        if (hasTimestamps) {
            val timestamps = chunk.joinToString(";") { (it.timestampSec ?: 0L).toString() }
            sb.append("&timestamps=").append(timestamps)
        }

        return sb.toString()
    }

    /**
     * Parses a single OSRM match response body.
     *
     * Returns null when `matchings` is absent or empty. GeoJSON coordinates are
     * `[lon, lat]` arrays; they are converted back to [TrackPoint](`lat`, `lon`).
     *
     * @param body       Raw response body string.
     * @param inputCount Number of input points in this chunk (used as fallback for matchedFraction).
     */
    private fun parseChunk(body: String, inputCount: Int): ChunkResult? {
        val root = JSONObject(body)
        val matchings: JSONArray = root.optJSONArray("matchings") ?: return null
        if (matchings.length() == 0) return null

        val points = mutableListOf<TrackPoint>()
        var confidenceSum = 0.0

        for (i in 0 until matchings.length()) {
            val matching = matchings.getJSONObject(i)
            confidenceSum += matching.optDouble("confidence", 0.0)
            val coordinates = matching
                .optJSONObject("geometry")
                ?.optJSONArray("coordinates") ?: continue
            for (j in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(j)
                // GeoJSON: [lon, lat]
                points.add(TrackPoint(lat = coord.getDouble(1), lon = coord.getDouble(0)))
            }
        }

        if (points.isEmpty()) return null

        val avgConfidence = confidenceSum / matchings.length()

        val tracepoints: JSONArray? = root.optJSONArray("tracepoints")
        val nonNullCount = if (tracepoints != null) {
            (0 until tracepoints.length()).count { !tracepoints.isNull(it) }
        } else {
            inputCount
        }

        return ChunkResult(
            points = points,
            confidence = avgConfidence,
            nonNullTracepoints = nonNullCount,
        )
    }

    private data class ChunkResult(
        val points: List<TrackPoint>,
        val confidence: Double,
        val nonNullTracepoints: Int,
    )
}
