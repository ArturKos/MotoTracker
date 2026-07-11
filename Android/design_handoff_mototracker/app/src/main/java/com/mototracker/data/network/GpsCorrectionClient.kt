package com.mototracker.data.network

/**
 * Injectable seam for OSRM GPS map-matching.
 *
 * Implementations build the OSRM `/match/v1/driving/…` request, handle chunking
 * for long rides, and parse the GeoJSON response into a [CorrectionOutcome].
 *
 * Callers should treat [Result.failure] as a transient error suitable for retry
 * (non-2xx response or transport error).
 */
interface GpsCorrectionClient {

    /**
     * Sends [points] to the OSRM match endpoint and returns the road-snapped result.
     *
     * Long traces are chunked automatically; the caller receives a single stitched
     * [CorrectionOutcome.Matched] spanning all chunks, or [CorrectionOutcome.NoMatch]
     * when no chunk produced any matchings.
     *
     * @param osrmBaseUrl Base URL of the OSRM instance, e.g. `"http://192.168.1.142:5001"`.
     * @param points      Ordered list of GPS samples; timestamps are included when present.
     * @return [Result.success] wrapping a [CorrectionOutcome], or [Result.failure] on any
     *         transport error or non-2xx HTTP status.
     */
    suspend fun match(osrmBaseUrl: String, points: List<TrackPoint>): Result<CorrectionOutcome>
}
