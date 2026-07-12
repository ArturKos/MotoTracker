package com.mototracker.domain.stats

import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PersonalRecordsCalculatorTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private fun route(
        id: String = "r",
        km: Double = 10.0,
        avg: Double = 40.0,
        max: Double = 80.0,
        elev: Double = 100.0,
        dateEpochMs: Long = epochMs(2026, Calendar.JUNE, 1),
    ) = Route(
        id = id,
        name = "Route $id",
        dateEpochMs = dateEpochMs,
        bikeId = null,
        km = km,
        durSec = 3600L,
        avg = avg,
        max = max,
        lean = 15.0,
        elev = elev,
        fuel = 1.0,
        synced = false,
        wxJson = null,
        pathJson = null,
        speedJson = null,
        elevProfileJson = null,
        notes = null,
        correctionStatus = CorrectionStatus.NONE,
    )

    /**
     * Returns epoch-ms for noon on [year]/[month]/[day] in the local timezone.
     * [month] follows [Calendar] convention (0 = January).
     */
    private fun epochMs(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ─────────────────────────────────────────────────────────────────────────
    // Empty input
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `empty routes returns zeroed PersonalRecords`() {
        val result = PersonalRecordsCalculator.compute(emptyList())
        assertEquals(0.0, result.longestRideKm, 0.0)
        assertEquals(0.0, result.fastestAvgSpeedKmh, 0.0)
        assertEquals(0.0, result.topSpeedKmh, 0.0)
        assertEquals(0.0, result.highestAscentM, 0.0)
        assertEquals(0.0, result.bestMonthKm, 0.0)
        assertEquals(-1, result.bestMonthMonth)
        assertEquals(0, result.longestDayStreak)
        assertTrue(result.earnedBadges.isEmpty())
        assertTrue(result.longestRideRouteId == null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Single route
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `single route sets all maxima to that route`() {
        val r = route(id = "solo", km = 55.0, avg = 50.0, max = 110.0, elev = 300.0)
        val result = PersonalRecordsCalculator.compute(listOf(r))

        assertEquals(55.0, result.longestRideKm, 0.001)
        assertEquals("solo", result.longestRideRouteId)
        assertEquals(50.0, result.fastestAvgSpeedKmh, 0.001)
        assertEquals("solo", result.fastestAvgSpeedRouteId)
        assertEquals(110.0, result.topSpeedKmh, 0.001)
        assertEquals("solo", result.topSpeedRouteId)
        assertEquals(300.0, result.highestAscentM, 0.001)
        assertEquals("solo", result.highestAscentRouteId)
        assertEquals(55.0, result.bestMonthKm, 0.001)
        assertEquals(1, result.longestDayStreak)
    }

    @Test
    fun `single route earns only FIRST_RIDE badge`() {
        val result = PersonalRecordsCalculator.compute(listOf(route()))
        assertEquals(listOf(Badge.FIRST_RIDE), result.earnedBadges)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-route — correct maxima selection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `multi routes picks correct longestRideKm and routeId`() {
        val r1 = route(id = "a", km = 80.0)
        val r2 = route(id = "b", km = 200.0)
        val r3 = route(id = "c", km = 50.0)
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2, r3))
        assertEquals(200.0, result.longestRideKm, 0.001)
        assertEquals("b", result.longestRideRouteId)
    }

    @Test
    fun `multi routes picks correct fastestAvgSpeed and routeId`() {
        val r1 = route(id = "a", avg = 45.0)
        val r2 = route(id = "b", avg = 80.0)
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2))
        assertEquals(80.0, result.fastestAvgSpeedKmh, 0.001)
        assertEquals("b", result.fastestAvgSpeedRouteId)
    }

    @Test
    fun `multi routes picks correct topSpeedKmh and routeId`() {
        val r1 = route(id = "a", max = 100.0)
        val r2 = route(id = "b", max = 175.0)
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2))
        assertEquals(175.0, result.topSpeedKmh, 0.001)
        assertEquals("b", result.topSpeedRouteId)
    }

    @Test
    fun `multi routes picks correct highestAscentM and routeId`() {
        val r1 = route(id = "a", elev = 500.0)
        val r2 = route(id = "b", elev = 2000.0)
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2))
        assertEquals(2000.0, result.highestAscentM, 0.001)
        assertEquals("b", result.highestAscentRouteId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Best month
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `bestMonth picks the month with highest total km`() {
        val june = route(id = "a", km = 300.0, dateEpochMs = epochMs(2026, Calendar.JUNE, 10))
        val july = route(id = "b", km = 100.0, dateEpochMs = epochMs(2026, Calendar.JULY, 5))
        val july2 = route(id = "c", km = 80.0, dateEpochMs = epochMs(2026, Calendar.JULY, 20))
        // June = 300 km, July = 180 km → best = June
        val result = PersonalRecordsCalculator.compute(listOf(june, july, july2))
        assertEquals(300.0, result.bestMonthKm, 0.001)
        assertEquals(2026, result.bestMonthYear)
        assertEquals(Calendar.JUNE, result.bestMonthMonth)
    }

    @Test
    fun `bestMonth accumulates multiple rides in same month`() {
        val r1 = route(id = "a", km = 120.0, dateEpochMs = epochMs(2026, Calendar.MAY, 1))
        val r2 = route(id = "b", km = 180.0, dateEpochMs = epochMs(2026, Calendar.MAY, 15))
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2))
        assertEquals(300.0, result.bestMonthKm, 0.001)
        assertEquals(Calendar.MAY, result.bestMonthMonth)
    }

    @Test
    fun `bestMonth distinguishes same month across different years`() {
        val may2025 = route(id = "a", km = 400.0, dateEpochMs = epochMs(2025, Calendar.MAY, 10))
        val may2026 = route(id = "b", km = 200.0, dateEpochMs = epochMs(2026, Calendar.MAY, 10))
        val result = PersonalRecordsCalculator.compute(listOf(may2025, may2026))
        assertEquals(400.0, result.bestMonthKm, 0.001)
        assertEquals(2025, result.bestMonthYear)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Day streak — consecutive days
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `streak of 1 for single ride`() {
        val result = PersonalRecordsCalculator.compute(listOf(route()))
        assertEquals(1, result.longestDayStreak)
    }

    @Test
    fun `streak of 3 for three consecutive days`() {
        val r1 = route(id = "a", dateEpochMs = epochMs(2026, Calendar.JUNE, 1))
        val r2 = route(id = "b", dateEpochMs = epochMs(2026, Calendar.JUNE, 2))
        val r3 = route(id = "c", dateEpochMs = epochMs(2026, Calendar.JUNE, 3))
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2, r3))
        assertEquals(3, result.longestDayStreak)
    }

    @Test
    fun `gap in streak resets the counter`() {
        val r1 = route(id = "a", dateEpochMs = epochMs(2026, Calendar.JUNE, 1))
        val r2 = route(id = "b", dateEpochMs = epochMs(2026, Calendar.JUNE, 2))
        // gap: skip June 3
        val r3 = route(id = "c", dateEpochMs = epochMs(2026, Calendar.JUNE, 4))
        val r4 = route(id = "d", dateEpochMs = epochMs(2026, Calendar.JUNE, 5))
        val r5 = route(id = "e", dateEpochMs = epochMs(2026, Calendar.JUNE, 6))
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2, r3, r4, r5))
        assertEquals(3, result.longestDayStreak)
    }

    @Test
    fun `two rides on same day count as one day for streak`() {
        val r1 = route(id = "a", dateEpochMs = epochMs(2026, Calendar.JUNE, 1))
        val r2 = route(id = "b", dateEpochMs = epochMs(2026, Calendar.JUNE, 1))
        val r3 = route(id = "c", dateEpochMs = epochMs(2026, Calendar.JUNE, 2))
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2, r3))
        assertEquals(2, result.longestDayStreak)
    }

    @Test
    fun `streak across month boundary is counted correctly`() {
        val r1 = route(id = "a", dateEpochMs = epochMs(2026, Calendar.MAY, 30))
        val r2 = route(id = "b", dateEpochMs = epochMs(2026, Calendar.MAY, 31))
        val r3 = route(id = "c", dateEpochMs = epochMs(2026, Calendar.JUNE, 1))
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2, r3))
        assertEquals(3, result.longestDayStreak)
    }

    @Test
    fun `streak picks the longest run when multiple runs exist`() {
        // Run 1: Jan 1-2 (length 2), Run 2: Jan 10-12 (length 3)
        val r1 = route(id = "a", dateEpochMs = epochMs(2026, Calendar.JANUARY, 1))
        val r2 = route(id = "b", dateEpochMs = epochMs(2026, Calendar.JANUARY, 2))
        val r3 = route(id = "c", dateEpochMs = epochMs(2026, Calendar.JANUARY, 10))
        val r4 = route(id = "d", dateEpochMs = epochMs(2026, Calendar.JANUARY, 11))
        val r5 = route(id = "e", dateEpochMs = epochMs(2026, Calendar.JANUARY, 12))
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2, r3, r4, r5))
        assertEquals(3, result.longestDayStreak)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Badge thresholds — boundary tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `FIRST_RIDE awarded for 1 ride`() {
        val result = PersonalRecordsCalculator.compute(listOf(route()))
        assertTrue(Badge.FIRST_RIDE in result.earnedBadges)
    }

    @Test
    fun `CENTURY awarded when any single ride reaches exactly 100 km`() {
        val result = PersonalRecordsCalculator.compute(listOf(route(km = 100.0)))
        assertTrue(Badge.CENTURY in result.earnedBadges)
    }

    @Test
    fun `CENTURY NOT awarded for 99_9 km`() {
        val result = PersonalRecordsCalculator.compute(listOf(route(km = 99.9)))
        assertTrue(Badge.CENTURY !in result.earnedBadges)
    }

    @Test
    fun `THOUSAND_CLUB awarded when total reaches exactly 1000 km`() {
        val r1 = route(id = "a", km = 600.0)
        val r2 = route(id = "b", km = 400.0)
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2))
        assertTrue(Badge.THOUSAND_CLUB in result.earnedBadges)
    }

    @Test
    fun `THOUSAND_CLUB NOT awarded for 999 km total`() {
        val r1 = route(id = "a", km = 500.0)
        val r2 = route(id = "b", km = 499.0)
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2))
        assertTrue(Badge.THOUSAND_CLUB !in result.earnedBadges)
    }

    @Test
    fun `SPEED_DEMON awarded when max speed exactly 150 kmh`() {
        val result = PersonalRecordsCalculator.compute(listOf(route(max = 150.0)))
        assertTrue(Badge.SPEED_DEMON in result.earnedBadges)
    }

    @Test
    fun `SPEED_DEMON NOT awarded for 149_9 kmh`() {
        val result = PersonalRecordsCalculator.compute(listOf(route(max = 149.9)))
        assertTrue(Badge.SPEED_DEMON !in result.earnedBadges)
    }

    @Test
    fun `MOUNTAIN_GOAT awarded when total ascent exactly 5000 m`() {
        val r1 = route(id = "a", elev = 3000.0)
        val r2 = route(id = "b", elev = 2000.0)
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2))
        assertTrue(Badge.MOUNTAIN_GOAT in result.earnedBadges)
    }

    @Test
    fun `MOUNTAIN_GOAT NOT awarded for 4999 m total`() {
        val r1 = route(id = "a", elev = 3000.0)
        val r2 = route(id = "b", elev = 1999.0)
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2))
        assertTrue(Badge.MOUNTAIN_GOAT !in result.earnedBadges)
    }

    @Test
    fun `MARATHON_MONTH awarded when best month has exactly 500 km`() {
        val r1 = route(id = "a", km = 300.0, dateEpochMs = epochMs(2026, Calendar.JUNE, 1))
        val r2 = route(id = "b", km = 200.0, dateEpochMs = epochMs(2026, Calendar.JUNE, 15))
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2))
        assertTrue(Badge.MARATHON_MONTH in result.earnedBadges)
    }

    @Test
    fun `MARATHON_MONTH NOT awarded for 499 km best month`() {
        val r1 = route(id = "a", km = 300.0, dateEpochMs = epochMs(2026, Calendar.JUNE, 1))
        val r2 = route(id = "b", km = 199.0, dateEpochMs = epochMs(2026, Calendar.JUNE, 15))
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2))
        assertTrue(Badge.MARATHON_MONTH !in result.earnedBadges)
    }

    @Test
    fun `STREAK_3 awarded for exactly 3 consecutive days`() {
        val r1 = route(id = "a", dateEpochMs = epochMs(2026, Calendar.JUNE, 1))
        val r2 = route(id = "b", dateEpochMs = epochMs(2026, Calendar.JUNE, 2))
        val r3 = route(id = "c", dateEpochMs = epochMs(2026, Calendar.JUNE, 3))
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2, r3))
        assertTrue(Badge.STREAK_3 in result.earnedBadges)
    }

    @Test
    fun `STREAK_3 NOT awarded for 2-day streak`() {
        val r1 = route(id = "a", dateEpochMs = epochMs(2026, Calendar.JUNE, 1))
        val r2 = route(id = "b", dateEpochMs = epochMs(2026, Calendar.JUNE, 2))
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2))
        assertTrue(Badge.STREAK_3 !in result.earnedBadges)
    }

    @Test
    fun `all badges earned when all thresholds are met`() {
        // 3-day streak (Jan 1-3), single ride 100+ km, total 1000+ km,
        // max speed 150+ km/h, total ascent 5000+ m, month km 500+
        val r1 = route(id = "a", km = 600.0, max = 160.0, elev = 3000.0,
            dateEpochMs = epochMs(2026, Calendar.JANUARY, 1))
        val r2 = route(id = "b", km = 500.0, elev = 2000.0,
            dateEpochMs = epochMs(2026, Calendar.JANUARY, 2))
        val r3 = route(id = "c", km = 50.0,
            dateEpochMs = epochMs(2026, Calendar.JANUARY, 3))
        val result = PersonalRecordsCalculator.compute(listOf(r1, r2, r3))
        assertEquals(Badge.values().toList(), result.earnedBadges)
    }
}
