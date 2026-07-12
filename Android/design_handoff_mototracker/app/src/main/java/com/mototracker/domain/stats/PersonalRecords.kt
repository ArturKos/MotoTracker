package com.mototracker.domain.stats

/**
 * Immutable snapshot of a rider's best-ever figures, computed over the full route set.
 *
 * All numeric values are in SI units (km for distance, km/h for speed, m for altitude)
 * regardless of the user's display preference. Unit conversion happens in the ViewModel.
 *
 * @param longestRideKm           Longest single-ride distance in km.
 * @param longestRideRouteId      Route ID of the longest ride; null when no routes exist.
 * @param fastestAvgSpeedKmh      Highest average speed of any single ride in km/h.
 * @param fastestAvgSpeedRouteId  Route ID of the fastest-average ride; null when no routes.
 * @param topSpeedKmh             All-time highest instantaneous (max) speed in km/h.
 * @param topSpeedRouteId         Route ID of the top-speed ride; null when no routes.
 * @param highestAscentM          Highest elevation gain of any single ride in metres.
 * @param highestAscentRouteId    Route ID of the highest-ascent ride; null when no routes.
 * @param bestMonthKm             Total distance ridden in the best calendar month, in km.
 * @param bestMonthYear           Calendar year of the best month; 0 when no routes.
 * @param bestMonthMonth          Calendar month of the best month (0=January … 11=December);
 *                                -1 when no routes have been recorded.
 * @param longestDayStreak        Longest run of consecutive local calendar days each
 *                                containing at least one recorded ride.
 * @param earnedBadges            Badges the rider has unlocked.
 */
data class PersonalRecords(
    val longestRideKm: Double = 0.0,
    val longestRideRouteId: String? = null,
    val fastestAvgSpeedKmh: Double = 0.0,
    val fastestAvgSpeedRouteId: String? = null,
    val topSpeedKmh: Double = 0.0,
    val topSpeedRouteId: String? = null,
    val highestAscentM: Double = 0.0,
    val highestAscentRouteId: String? = null,
    val bestMonthKm: Double = 0.0,
    val bestMonthYear: Int = 0,
    val bestMonthMonth: Int = -1,
    val longestDayStreak: Int = 0,
    val earnedBadges: List<Badge> = emptyList(),
)
