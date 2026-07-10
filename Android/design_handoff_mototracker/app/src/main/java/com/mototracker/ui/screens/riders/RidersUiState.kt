package com.mototracker.ui.screens.riders

import com.mototracker.data.model.FeedType

/**
 * Dot colour for a feed event row, derived from [FeedType].
 *
 * - [ACCENT]  — start events (brand accent, teal).
 * - [ACCENT2] — max-speed events (secondary accent, orange/red).
 * - [DIM]     — finish events (muted / dim colour).
 */
enum class FeedDotColor { ACCENT, ACCENT2, DIM }

/**
 * Presentation model for a single riding-group member in the GROUP section.
 *
 * @param initial  First character of [name], uppercased, shown in the avatar circle.
 * @param name     Display name (or phone number when no name is available).
 * @param phone    Phone number shown in secondary line.
 * @param bikeName Bike model name; may be empty if not yet filled in.
 */
data class GroupMemberUi(
    val initial: String,
    val name: String,
    val phone: String,
    val bikeName: String,
)

/**
 * Presentation model for a single row in the LIVE FEED section.
 *
 * @param who        Display name of the rider who triggered this event.
 * @param value      Speed value string; non-null only for [FeedType.MAX].
 * @param isMax      `true` when [type] is [FeedType.MAX] — drives the accent2-coloured value label.
 * @param bikeName   Bike model of the rider.
 * @param timeLabel  Formatted timestamp, e.g. "10:03".
 * @param dotColor   Colour indicator for the event-type dot.
 * @param type       Original [FeedType] — the Composable uses this to pick the string resource.
 */
data class FeedEventUi(
    val who: String,
    val value: String?,
    val isMax: Boolean,
    val bikeName: String,
    val timeLabel: String,
    val dotColor: FeedDotColor,
    val type: FeedType,
)

/**
 * Presentation model for a single row in the WAVES section.
 *
 * @param nick      The other rider's display nickname.
 * @param bikeName  The other rider's bike model.
 * @param place     Human-readable location label where the wave occurred.
 * @param timeLabel Formatted timestamp of the wave, e.g. "14:32".
 */
data class WaveUi(
    val nick: String,
    val bikeName: String,
    val place: String,
    val timeLabel: String,
)

/**
 * One-shot events emitted by [RidersViewModel] to the Composable layer.
 */
sealed interface RidersEvent {
    /** Emitted after [RidersViewModel.onAddByPhone] successfully persists a new member. */
    data object InviteSent : RidersEvent
}

/**
 * Immutable UI state for the Riders screen.
 *
 * All mapping is done by the ViewModel so the Composable acts as a pure renderer.
 *
 * @param members        Riding-group members list.
 * @param memberCount    Total count of [members]; pre-computed for the header label.
 * @param feedAvailable  `true` when online AND offline-only mode is off — gates the feed section.
 * @param feed           Live-feed event rows; only shown when [feedAvailable] is `true`.
 * @param waves          Bluetooth wave rows (from DB — empty until BT is real, 🔬).
 */
data class RidersUiState(
    val members: List<GroupMemberUi> = emptyList(),
    val memberCount: Int = 0,
    val feedAvailable: Boolean = false,
    val feed: List<FeedEventUi> = emptyList(),
    val waves: List<WaveUi> = emptyList(),
)
