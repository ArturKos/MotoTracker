package com.mototracker.ui.screens.detail

/**
 * One-shot UI events emitted by [RouteDetailViewModel] for the [RouteDetailScreen] to consume.
 *
 * Delivered via a [kotlinx.coroutines.channels.Channel] so each event is consumed exactly once
 * regardless of recomposition.
 */
sealed interface RouteDetailEvent {

    /**
     * GPX content has been produced and is ready for the UI to write/share.
     *
     * The actual file-write or share-sheet invocation is done in the Composable via Android APIs
     * and is on-device only (🔬). The ViewModel only produces the content string.
     *
     * @param content  The full GPX 1.1 XML string.
     * @param fileName Suggested filename, e.g. `"alpine-tour-abc12345.gpx"`.
     */
    data class GpxSaved(val content: String, val fileName: String) : RouteDetailEvent

    /**
     * A deterministic route share URL has been produced and is ready for the clipboard.
     *
     * The actual clipboard write is done in the Composable (on-device only, 🔬).
     *
     * @param url The route deep-link string, e.g. `"mototracker://route/<id>"`.
     */
    data class LinkCopied(val url: String) : RouteDetailEvent

    /** The route has been submitted to the sync queue for upload to the GPStrack server. */
    data object ServerSent : RouteDetailEvent
}
