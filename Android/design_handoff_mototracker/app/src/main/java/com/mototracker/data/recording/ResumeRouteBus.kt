package com.mototracker.data.recording

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped bus that routes "continue this route" requests from the Route Detail screen
 * to [com.mototracker.ui.screens.record.RecordingViewModel].
 *
 * RECORD is an argument-less bottom-nav root and its ViewModel is nav-scoped, so a nav
 * argument is awkward. This singleton bus is the clean, testable channel that decouples
 * [com.mototracker.ui.screens.detail.RouteDetailViewModel] (producer) from
 * RecordingViewModel (consumer) without introducing a direct dependency between them.
 */
interface ResumeRouteBus {

    /** Stream of route IDs requested for resume-continuation. Collected by RecordingViewModel. */
    val requests: Flow<String>

    /**
     * Sends a request to resume-continue the route with the given [routeId].
     *
     * Suspends until the item is accepted by the channel's buffer. For the
     * [ChannelResumeRouteBus] implementation this is effectively instant.
     *
     * @param routeId UUID of the route to continue.
     */
    suspend fun request(routeId: String)
}

/**
 * [Channel]-backed singleton implementation of [ResumeRouteBus].
 *
 * Uses [Channel.BUFFERED] so that a request enqueued before RecordingViewModel has started
 * collecting (e.g. a cold-start race) is not dropped.
 */
@Singleton
class ChannelResumeRouteBus @Inject constructor() : ResumeRouteBus {

    private val _channel = Channel<String>(Channel.BUFFERED)

    override val requests: Flow<String> = _channel.receiveAsFlow()

    override suspend fun request(routeId: String) {
        _channel.send(routeId)
    }
}
