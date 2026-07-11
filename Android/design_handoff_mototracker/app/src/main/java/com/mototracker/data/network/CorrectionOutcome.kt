package com.mototracker.data.network

/**
 * Result of an OSRM map-matching attempt for a GPS trace.
 */
sealed class CorrectionOutcome {

    /**
     * OSRM returned at least one matching; the trace was partially or fully snapped to roads.
     *
     * @param points           Road-snapped points in the same order as the input.
     * @param confidence       Weighted-average OSRM confidence score (0–1).
     * @param matchedFraction  Fraction of input [TrackPoint]s that had a non-null tracepoint
     *                         (i.e., were successfully matched), in the range 0–1.
     */
    data class Matched(
        val points: List<TrackPoint>,
        val confidence: Double,
        val matchedFraction: Double,
    ) : CorrectionOutcome()

    /** OSRM returned an empty `matchings` array; no road-snap is possible for this trace. */
    object NoMatch : CorrectionOutcome()
}
