package com.mototracker.data.repository

import com.mototracker.data.model.FeedEvent
import com.mototracker.data.model.FeedType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Static-seed implementation of [FeedRepository].
 *
 * Returns the prototype feed from `MotoTracker.dc.html` `state.feed`.
 * Real server-push is out of scope (section C); this seam lets the ViewModel
 * and UI exercise the full feed rendering path in tests and on-device.
 *
 * @see FeedRepository
 */
@Singleton
class FeedRepositoryImpl @Inject constructor() : FeedRepository {

    override fun observeFeed(): Flow<List<FeedEvent>> = flowOf(SEED_FEED)

    private companion object {
        val SEED_FEED = listOf(
            FeedEvent(
                who = "Marek",
                bikeName = "MT-09",
                type = FeedType.START,
                value = null,
                timeLabel = "09:12",
            ),
            FeedEvent(
                who = "Ola",
                bikeName = "CBR 600",
                type = FeedType.MAX,
                value = "148 km/h",
                timeLabel = "10:03",
            ),
            FeedEvent(
                who = "Piotr",
                bikeName = "GSX-R 750",
                type = FeedType.FINISH,
                value = null,
                timeLabel = "12:40",
            ),
        )
    }
}
