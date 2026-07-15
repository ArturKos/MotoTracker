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
     * The actual file-write is performed by [RouteDetailScreen] via SAF
     * ([androidx.activity.result.contract.ActivityResultContracts.CreateDocument] /
     * `ContentResolver.openOutputStream`), which requires no storage permission on any API level
     * (works on API 28). The ViewModel only produces the content string.
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

    /**
     * GPS road-correction has been enqueued and an immediate attempt triggered.
     *
     * The Composable should show a brief informational toast to the user.
     */
    data object CorrectionQueued : RouteDetailEvent

    /**
     * The route has been permanently deleted from local storage.
     *
     * The Composable should navigate away (typically [androidx.navigation.NavController.popBackStack])
     * since the detail screen no longer has a subject to display.
     */
    data object RouteDeleted : RouteDetailEvent

    /** A refuel event was added successfully (G5). The Composable may show a brief toast. */
    data object RefuelAdded : RouteDetailEvent

    /** A refuel event was deleted (G5). The Composable may show a brief toast. */
    data object RefuelDeleted : RouteDetailEvent

    /**
     * The rider requested to continue this route from the detail screen (J5).
     *
     * The [ResumeRouteBus] request has already been sent; the Composable should navigate
     * to the RECORD tab so the user can tap Resume to begin recording.
     *
     * @param routeId UUID of the route to continue.
     */
    data class ResumeRoute(val routeId: String) : RouteDetailEvent
}
