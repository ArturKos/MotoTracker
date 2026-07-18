package com.mototracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker

/**
 * Represents the three possible states of the outbound sync queue shown in the
 * top app bar chip per README §Wspólne elementy.
 */
sealed class SyncState {

    /** No network / offline mode — chip coloured with [MotoTracker.colors.accent2]. */
    object Offline : SyncState()

    /**
     * Routes are queued but not yet sent to the server.
     *
     * @param n Number of routes waiting in the sync queue.
     */
    data class Queued(val n: Int) : SyncState()

    /** All routes successfully synchronised — chip dimmed/neutral. */
    object Synced : SyncState()

    /**
     * Maps this state to a display label using the provided string providers.
     *
     * This function is pure (no Android context / Compose required) so that it
     * can be exercised in plain JUnit unit tests.
     *
     * @param offlineLabel  String to show when [Offline] (e.g. "OFFLINE").
     * @param queuedLabel   Function producing a string for [Queued] given the queue count.
     * @param syncedLabel   String to show when [Synced] (e.g. "SYNC ✓").
     */
    fun toDisplayLabel(
        offlineLabel: String,
        queuedLabel: (Int) -> String,
        syncedLabel: String,
    ): String = when (this) {
        is Offline -> offlineLabel
        is Queued  -> queuedLabel(n)
        is Synced  -> syncedLabel
    }
}

/**
 * Derives the [SyncState] to display in the top-app-bar sync chip from three
 * independently-observed sources (U1: consolidated from 3 flags to 2).
 *
 * Decision order (first matching rule wins):
 * 1. [SyncState.Offline] — when the device has no network or [noInternet] is `true`.
 * 2. [SyncState.Queued] — when there are routes waiting in the sync queue.
 * 3. [SyncState.Synced] — the default/happy-path state.
 *
 * This is a pure top-level function with no Android dependencies so it can be exercised
 * in plain JUnit tests without Robolectric.
 *
 * @param isOnline     `true` when the device has an active internet connection.
 * @param noInternet   `true` when the user has blocked all outbound network activity.
 * @param pendingCount Number of routes waiting in the outbound sync queue.
 * @return The [SyncState] that should be shown in the UI.
 */
fun deriveSyncState(
    isOnline: Boolean,
    noInternet: Boolean,
    pendingCount: Int,
): SyncState = when {
    !isOnline || noInternet -> SyncState.Offline
    pendingCount > 0 -> SyncState.Queued(pendingCount)
    else -> SyncState.Synced
}

/**
 * Stateless chip composable that renders the current [SyncState] in the top app
 * bar per README §Chip synchronizacji:
 * - [SyncState.Offline]  → text + background tinted with [MotoTracker.colors.accent2]
 * - [SyncState.Queued]   → text + background tinted with [MotoTracker.colors.accent]
 * - [SyncState.Synced]   → text + background tinted with [MotoTracker.colors.dim]
 *
 * @param state    The current synchronisation state.
 * @param modifier Optional [Modifier] applied to the outer [Surface].
 */
@Composable
fun SyncChip(state: SyncState, modifier: Modifier = Modifier) {
    val colors = MotoTracker.colors

    val (label, tint) = when (state) {
        is SyncState.Offline -> stringResource(R.string.sync_offline) to colors.accent2
        is SyncState.Queued  -> stringResource(R.string.sync_queued, state.n) to colors.accent
        is SyncState.Synced  -> stringResource(R.string.sync_synced) to colors.dim
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = tint.copy(alpha = 0.18f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MotoTracker.typography.label,
            color = tint,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
