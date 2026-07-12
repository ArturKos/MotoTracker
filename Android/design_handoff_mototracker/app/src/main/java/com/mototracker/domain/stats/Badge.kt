package com.mototracker.domain.stats

/**
 * Achievement badges earned by reaching certain riding milestones.
 *
 * Every badge threshold is evaluated purely over the stored route set —
 * no server round-trip is required.
 */
enum class Badge {

    /** Awarded after recording at least 1 ride. */
    FIRST_RIDE,

    /** Awarded when any single ride reaches 100 km. */
    CENTURY,

    /** Awarded when cumulative distance across all rides reaches 1 000 km. */
    THOUSAND_CLUB,

    /** Awarded when any ride's maximum speed reaches 150 km/h. */
    SPEED_DEMON,

    /** Awarded when total elevation gain across all rides reaches 5 000 m. */
    MOUNTAIN_GOAT,

    /** Awarded when the best month's total distance reaches 500 km. */
    MARATHON_MONTH,

    /** Awarded when the longest consecutive calendar-day riding streak reaches 3 days. */
    STREAK_3,
}
