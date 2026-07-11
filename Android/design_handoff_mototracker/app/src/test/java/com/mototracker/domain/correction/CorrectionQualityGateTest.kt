package com.mototracker.domain.correction

import com.mototracker.data.network.CorrectionOutcome
import com.mototracker.data.network.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class CorrectionQualityGateTest {

    private val gate = CorrectionQualityGate()

    private fun matched(confidence: Double, matchedFraction: Double) =
        CorrectionOutcome.Matched(
            points = listOf(TrackPoint(1.0, 2.0)),
            confidence = confidence,
            matchedFraction = matchedFraction,
        )

    // ── NoMatch → REJECT ────────────────────────────────────────────────────

    @Test
    fun `NoMatch always returns REJECT`() {
        assertEquals(
            CorrectionQualityGate.Verdict.REJECT,
            gate.evaluate(CorrectionOutcome.NoMatch),
        )
    }

    @Test
    fun `NoMatch returns REJECT with explicit thresholds`() {
        assertEquals(
            CorrectionQualityGate.Verdict.REJECT,
            gate.evaluate(CorrectionOutcome.NoMatch, 0.0, 0.0),
        )
    }

    // ── Both thresholds exactly met → ACCEPT ────────────────────────────────

    @Test
    fun `ACCEPT when confidence and matchedFraction exactly equal default thresholds`() {
        assertEquals(
            CorrectionQualityGate.Verdict.ACCEPT,
            gate.evaluate(
                matched(
                    CorrectionQualityGate.DEFAULT_CONFIDENCE_THRESHOLD,
                    CorrectionQualityGate.DEFAULT_MATCH_FRACTION_THRESHOLD,
                ),
            ),
        )
    }

    @Test
    fun `ACCEPT when both values exceed default thresholds`() {
        assertEquals(
            CorrectionQualityGate.Verdict.ACCEPT,
            gate.evaluate(matched(confidence = 0.9, matchedFraction = 0.95)),
        )
    }

    // ── Below confidence threshold → LOW_CONFIDENCE ─────────────────────────

    @Test
    fun `LOW_CONFIDENCE when confidence below threshold but matchedFraction ok`() {
        assertEquals(
            CorrectionQualityGate.Verdict.LOW_CONFIDENCE,
            gate.evaluate(matched(confidence = 0.5, matchedFraction = 0.9)),
        )
    }

    // ── Below matchedFraction threshold → LOW_CONFIDENCE ───────────────────

    @Test
    fun `LOW_CONFIDENCE when matchedFraction below threshold but confidence ok`() {
        assertEquals(
            CorrectionQualityGate.Verdict.LOW_CONFIDENCE,
            gate.evaluate(matched(confidence = 0.8, matchedFraction = 0.5)),
        )
    }

    // ── Both below threshold → LOW_CONFIDENCE ───────────────────────────────

    @Test
    fun `LOW_CONFIDENCE when both confidence and matchedFraction below thresholds`() {
        assertEquals(
            CorrectionQualityGate.Verdict.LOW_CONFIDENCE,
            gate.evaluate(matched(confidence = 0.3, matchedFraction = 0.3)),
        )
    }

    // ── Explicit threshold overrides ─────────────────────────────────────────

    @Test
    fun `ACCEPT with explicit lower thresholds that are met`() {
        assertEquals(
            CorrectionQualityGate.Verdict.ACCEPT,
            gate.evaluate(
                matched(confidence = 0.5, matchedFraction = 0.7),
                confidenceThreshold = 0.4,
                matchFractionThreshold = 0.6,
            ),
        )
    }

    @Test
    fun `LOW_CONFIDENCE with explicit higher thresholds not met`() {
        assertEquals(
            CorrectionQualityGate.Verdict.LOW_CONFIDENCE,
            gate.evaluate(
                matched(confidence = 0.8, matchedFraction = 0.8),
                confidenceThreshold = 0.9,
                matchFractionThreshold = 0.9,
            ),
        )
    }

    // ── Constructor threshold override ───────────────────────────────────────

    @Test
    fun `custom constructor thresholds are used by default evaluate`() {
        val strictGate = CorrectionQualityGate(confidenceThreshold = 0.95, matchFractionThreshold = 0.99)
        assertEquals(
            CorrectionQualityGate.Verdict.LOW_CONFIDENCE,
            strictGate.evaluate(matched(confidence = 0.9, matchedFraction = 0.9)),
        )
    }

    @Test
    fun `lenient constructor thresholds allow low scores to pass`() {
        val lenientGate = CorrectionQualityGate(confidenceThreshold = 0.1, matchFractionThreshold = 0.1)
        assertEquals(
            CorrectionQualityGate.Verdict.ACCEPT,
            lenientGate.evaluate(matched(confidence = 0.2, matchedFraction = 0.2)),
        )
    }
}
