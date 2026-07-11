package com.mototracker.domain.correction

import com.mototracker.data.network.CorrectionOutcome

/**
 * Pure quality gate that classifies an OSRM [CorrectionOutcome] against configurable
 * confidence and match-fraction thresholds.
 *
 * Thresholds are injected via the constructor (with sane defaults) so they can be
 * overridden per call-site or in tests without subclassing.
 *
 * @param confidenceThreshold    Minimum OSRM confidence score required to accept the match (0–1).
 * @param matchFractionThreshold Minimum fraction of input points that must be matched (0–1).
 */
class CorrectionQualityGate(
    val confidenceThreshold: Double = DEFAULT_CONFIDENCE_THRESHOLD,
    val matchFractionThreshold: Double = DEFAULT_MATCH_FRACTION_THRESHOLD,
) {

    companion object {
        /** Default minimum OSRM confidence for an ACCEPT verdict. */
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.6

        /** Default minimum matched-point fraction for an ACCEPT verdict. */
        const val DEFAULT_MATCH_FRACTION_THRESHOLD = 0.8
    }

    /**
     * Result of a quality-gate evaluation.
     */
    enum class Verdict {
        /** Both thresholds met; the corrected path should replace the raw trace. */
        ACCEPT,

        /** Matching succeeded but at least one threshold was not met; keep raw trace. */
        LOW_CONFIDENCE,

        /** No match was found; the raw trace is unchanged. */
        REJECT,
    }

    /**
     * Evaluates [outcome] using the thresholds supplied at construction time.
     *
     * @param outcome The OSRM map-matching result to classify.
     * @return [Verdict.ACCEPT] when both thresholds are met,
     *         [Verdict.LOW_CONFIDENCE] when a match was found but thresholds are not met,
     *         [Verdict.REJECT] when [CorrectionOutcome.NoMatch] was returned.
     */
    fun evaluate(outcome: CorrectionOutcome): Verdict =
        evaluate(outcome, confidenceThreshold, matchFractionThreshold)

    /**
     * Evaluates [outcome] against explicit [confidenceThreshold] and [matchFractionThreshold].
     *
     * Allows callers to override the instance-level thresholds for a single evaluation.
     *
     * @param outcome                The OSRM map-matching result to classify.
     * @param confidenceThreshold    Minimum confidence score for ACCEPT.
     * @param matchFractionThreshold Minimum matched-point fraction for ACCEPT.
     */
    fun evaluate(
        outcome: CorrectionOutcome,
        confidenceThreshold: Double,
        matchFractionThreshold: Double,
    ): Verdict = when (outcome) {
        is CorrectionOutcome.NoMatch -> Verdict.REJECT
        is CorrectionOutcome.Matched -> {
            if (outcome.confidence >= confidenceThreshold &&
                outcome.matchedFraction >= matchFractionThreshold
            ) {
                Verdict.ACCEPT
            } else {
                Verdict.LOW_CONFIDENCE
            }
        }
    }
}
