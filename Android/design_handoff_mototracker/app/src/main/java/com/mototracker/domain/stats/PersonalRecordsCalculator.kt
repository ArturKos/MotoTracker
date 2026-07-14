package com.mototracker.domain.stats

import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import java.util.Calendar

/**
 * Stateless calculator that derives [PersonalRecords] from a route list.
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
     * Computes [PersonalRecords] for [summaries].
     *
     * Accepts the lightweight [RouteSummaryModel] so that list/stats screens never need
     * to load full trace blobs.
     *
     * @param summaries All route summaries, in any order.
     * @return Aggregated personal records and earned badges.
     */
    fun computeFromSummaries(summaries: List<RouteSummaryModel>): PersonalRecords {
        if (summaries.isEmpty()) return PersonalRecords()
        return computeCore(
            n = summaries.size,
            getKm = { summaries[it].km },
            getAvg = { summaries[it].avg },
            getMax = { summaries[it].max },
            getElev = { summaries[it].elev },
            getDateMs = { summaries[it].dateEpochMs },
            getId = { summaries[it].id },
        )
    }

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
        return computeCore(
            n = routes.size,
            getKm = { routes[it].km },
            getAvg = { routes[it].avg },
            getMax = { routes[it].max },
            getElev = { routes[it].elev },
            getDateMs = { routes[it].dateEpochMs },
            getId = { routes[it].id },
        )
    }

    private fun computeCore(
        n: Int,
        getKm: (Int) -> Double,
        getAvg: (Int) -> Double,
        getMax: (Int) -> Double,
        getElev: (Int) -> Double,
        getDateMs: (Int) -> Long,
        getId: (Int) -> String,
    ): PersonalRecords {
        var longestRideIdx = 0
        var fastestAvgIdx = 0
        var topSpeedIdx = 0
        var highestAscentIdx = 0

        for (i in 0 until n) {
            if (getKm(i) > getKm(longestRideIdx)) longestRideIdx = i
            if (getAvg(i) > getAvg(fastestAvgIdx)) fastestAvgIdx = i
            if (getMax(i) > getMax(topSpeedIdx)) topSpeedIdx = i
            if (getElev(i) > getElev(highestAscentIdx)) highestAscentIdx = i
        }

        // Bucket km by (year, month), matching StatsViewModel.buildMonthBars logic.
        val kmByMonth = mutableMapOf<Pair<Int, Int>, Double>()
        for (i in 0 until n) {
            val cal = Calendar.getInstance().apply { timeInMillis = getDateMs(i) }
            val key = cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
            kmByMonth[key] = (kmByMonth[key] ?: 0.0) + getKm(i)
        }
        val bestEntry = kmByMonth.maxByOrNull { it.value }!!

        val longestDayStreak = computeDayStreak(n, getDateMs)

        var totalKm = 0.0
        var totalAscentM = 0.0
        for (i in 0 until n) {
            totalKm += getKm(i)
            totalAscentM += getElev(i)
        }

        return PersonalRecords(
            longestRideKm = getKm(longestRideIdx),
            longestRideRouteId = getId(longestRideIdx),
            fastestAvgSpeedKmh = getAvg(fastestAvgIdx),
            fastestAvgSpeedRouteId = getId(fastestAvgIdx),
            topSpeedKmh = getMax(topSpeedIdx),
            topSpeedRouteId = getId(topSpeedIdx),
            highestAscentM = getElev(highestAscentIdx),
            highestAscentRouteId = getId(highestAscentIdx),
            bestMonthKm = bestEntry.value,
            bestMonthYear = bestEntry.key.first,
            bestMonthMonth = bestEntry.key.second,
            longestDayStreak = longestDayStreak,
            earnedBadges = buildBadges(
                rideCount = n,
                longestRideKm = getKm(longestRideIdx),
                topSpeedKmh = getMax(topSpeedIdx),
                totalKm = totalKm,
                totalAscentM = totalAscentM,
                bestMonthKm = bestEntry.value,
                longestDayStreak = longestDayStreak,
            ),
        )
    }

    /**
     * Returns the longest run of consecutive local calendar days that each contain
     * at least one ride.
     *
     * Days are normalised to a UTC day-number (ms since epoch ÷ 86 400 000) after
     * resetting the time-of-day to midnight in the device's local timezone so that
     * a single ride always maps to exactly one calendar day regardless of its start time.
     */
    private fun computeDayStreak(n: Int, getDateMs: (Int) -> Long): Int {
        if (n == 0) return 0

        val dayNumbers: Set<Long> = (0 until n).mapTo(mutableSetOf()) { i ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = getDateMs(i)
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
