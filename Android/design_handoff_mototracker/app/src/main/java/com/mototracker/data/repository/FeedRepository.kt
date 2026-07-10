package com.mototracker.data.repository

import com.mototracker.data.model.FeedEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the live-feed of riding-group activity.
 *
 * The feed requires an active internet connection; the ViewModel is responsible
 * for gating [observeFeed] output with the network state — this interface simply
 * provides the data stream without caring about connectivity.
 *
 * A real server-push implementation is out of scope for section C; the current
 * implementation returns the prototype's seed data as a static seam.
 */
interface FeedRepository {

    /**
     * Returns a stream of recent [FeedEvent]s from the user's riding group.
     *
     * The stream does not complete while the subscriber is active; it re-emits
     * whenever the feed changes (today: never — static seed; future: server push).
     */
    fun observeFeed(): Flow<List<FeedEvent>>
}
