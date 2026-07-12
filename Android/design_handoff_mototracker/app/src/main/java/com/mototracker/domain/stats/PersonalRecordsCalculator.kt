package com.mototracker.domain.stats

import com.mototracker.data.model.Route
import java.util.Calendar

/**
 * Stateless calculator that derives [PersonalRecords] from a list of [Route]s.
 *
 * Implemented as a Kotlin [object] (no mutable state, no external dependencies) so it
 * can be called directly without DI — following the same pattern as
 * [com.mototracker.core.format.UnitFormatter].
 *
 * Month bucketing uses `Calendar.YEAR` / `Calendar.MONTH` to stay consistent with
 * `StatsViewModel.buildMonthBars`.
 */
object PersonalRecordsCalculator {

    /**
     * Computes [PersonalRecords] for [routes].
     *
     * Returns a zeroed [PersonalRecords] (all-zero values, [PersonalRecords.bestMonthMonth] = -1,
     * empty badge list) when [routes] is empty.
     *
     * @param routes All recorded routes, in any order.
     * @return Aggregated personal records and earned badges.
     */
    fun compute(routes: List<Route>): PersonalRecords {
        if (routes.isEmpty()) return PersonalRecords()

        val longestRide = routes.maxByOrNull { it.km }!!
        val fastestAvgRide = routes.maxByOrNull { it.avg }!!
        val topSpeedRide = routes.maxByOrNull { it.max }!!
        val highestAscentRide = routes.maxByOrNull { it.elev }!!

        // Bucket km by (year, month), matching StatsViewModel.buildMonthBars logic.
        val kmByMonth = mutableMapOf<Pair<Int, Int>, Double>()
        for (route in routes) {
            val cal = Calendar.getInstance().apply { timeInMillis = route.dateEpochMs }
            val key = cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
            kmByMonth[key] = (kmByMonth[key] ?: 0.0) + route.km
        }
        val bestEntry = kmByMonth.maxByOrNull { it.value }!!

        val longestDayStreak = computeDayStreak(routes)

        val totalKm = routes.sumOf { it.km }
        val totalAscentM = routes.sumOf { it.elev }

        return PersonalRecords(
            longestRideKm = longestRide.km,
            longestRideRouteId = longestRide.id,
            fastestAvgSpeedKmh = fastestAvgRide.avg,
            fastestAvgSpeedRouteId = fastestAvgRide.id,
            topSpeedKmh = topSpeedRide.max,
            topSpeedRouteId = topSpeedRide.id,
            highestAscentM = highestAscentRide.elev,
            highestAscentRouteId = highestAscentRide.id,
            bestMonthKm = bestEntry.value,
            bestMonthYear = bestEntry.key.first,
            bestMonthMonth = bestEntry.key.second,
            longestDayStreak = longestDayStreak,
            earnedBadges = buildBadges(
                rideCount = routes.size,
                longestRideKm = longestRide.km,
                topSpeedKmh = topSpeedRide.max,
                totalKm = totalKm,
                totalAscentM = totalAscentM,
                bestMonthKm = bestEntry.value,
                longestDayStreak = longestDayStreak,
            ),
        )
    }

    /**
     * Returns the longest run of consecutive local calendar days that each contain
     * at least one ride in [routes].
     *
     * Days are normalised to a UTC day-number (ms since epoch ÷ 86 400 000) after
     * resetting the time-of-day to midnight in the device's local timezone so that
     * a single ride always maps to exactly one calendar day regardless of its start time.
     */
    private fun computeDayStreak(routes: List<Route>): Int {
        if (routes.isEmpty()) return 0

        val dayNumbers: Set<Long> = routes.mapTo(mutableSetOf()) { route ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = route.dateEpochMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis / (24L * 60L * 60L * 1_000L)
        }

        val sorted = dayNumbers.sorted()
        var maxRun = 1
        var currentRun = 1
        for (i in 1 until sorted.size) {
            if (sorted[i] == sorted[i - 1] + 1L) {
                currentRun++
                if (currentRun > maxRun) maxRun = currentRun
            } else {
                currentRun = 1
            }
        }
        return maxRun
    }

    /**
     * Builds the list of earned [Badge]s from pre-aggregated statistics.
     *
     * Badges are evaluated in declaration order; any badge whose threshold is not met is omitted.
     */
    private fun buildBadges(
        rideCount: Int,
        longestRideKm: Double,
        topSpeedKmh: Double,
        totalKm: Double,
        totalAscentM: Double,
        bestMonthKm: Double,
        longestDayStreak: Int,
    ): List<Badge> = buildList {
        if (rideCount >= 1) add(Badge.FIRST_RIDE)
        if (longestRideKm >= 100.0) add(Badge.CENTURY)
        if (totalKm >= 1_000.0) add(Badge.THOUSAND_CLUB)
        if (topSpeedKmh >= 150.0) add(Badge.SPEED_DEMON)
        if (totalAscentM >= 5_000.0) add(Badge.MOUNTAIN_GOAT)
        if (bestMonthKm >= 500.0) add(Badge.MARATHON_MONTH)
        if (longestDayStreak >= 3) add(Badge.STREAK_3)
    }
}
