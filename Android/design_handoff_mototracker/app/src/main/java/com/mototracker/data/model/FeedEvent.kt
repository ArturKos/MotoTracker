package com.mototracker.data.model

/**
 * Type of a live-feed event from a group member.
 *
 * - [START]  — the rider started a new ride.
 * - [FINISH] — the rider finished their ride.
 * - [MAX]    — the rider reached a notable top speed.
 */
enum class FeedType { START, FINISH, MAX }

/**
 * A single live-feed event emitted by a riding-group member.
 *
 * The feed is gated behind internet connectivity; when offline the ViewModel
 * marks [feedAvailable] = false and the Composable shows the no-internet state.
 *
 * @param who       Display name of the rider who triggered the event.
 * @param bikeName  Bike model of that rider.
 * @param type      Category of this event — used for dot colour and action text.
 * @param value     Optional value string (e.g. "148 km/h") present only for [FeedType.MAX].
 * @param timeLabel Formatted timestamp shown in the feed row, e.g. "10:03".
 */
data class FeedEvent(
    val who: String,
    val bikeName: String,
    val type: FeedType,
    val value: String?,
    val timeLabel: String,
)
