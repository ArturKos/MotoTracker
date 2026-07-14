package com.mototracker.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.R
import com.mototracker.core.format.ChartPolyline
import com.mototracker.core.format.GpxExporter
import com.mototracker.core.format.RouteThumbnail
import com.mototracker.core.format.RouteWeather
import com.mototracker.core.format.UnitFormatter
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.model.Wave
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.GpsCorrectionRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.repository.WaveRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.ui.map.TrackGeometry
import com.mototracker.ui.state.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * ViewModel for the Route Detail screen.
 *
 * Reads [routeId] from [SavedStateHandle] (nav arg key `"routeId"`) and combines
 * a live [RouteRepository.observeById] stream with [BikeRepository.observeAll],
 * [WaveRepository.observeForRoute], [AppSettingsSource.settings], and the user's
 * track-view selection into a single [StateFlow]<[RouteDetailUiState]>.
 *
 * Because the route source is now reactive, any change to the row (e.g. after OSRM
 * correction writes [Route.correctedPathJson]) automatically re-renders the screen
 * without a manual reload.
 *
 * One-shot [RouteDetailEvent]s are delivered via [events] using a [Channel] to ensure
 * they are consumed exactly once regardless of recomposition.
 *
 * @param savedStateHandle         Provides the `routeId` nav argument.
 * @param routeRepository          Source of the route to display (live stream).
 * @param bikeRepository           Source of bikes for name / sold-status resolution.
 * @param waveRepository           Source of Bluetooth wave meetups for this route.
 * @param settingsSource           Provides measurement units preference.
 * @param syncRepository           Manages the outbound sync queue for server upload.
 * @param gpsCorrectionRepository  Manages the OSRM GPS road-correction queue.
 */
@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository,
    private val bikeRepository: BikeRepository,
    private val waveRepository: WaveRepository,
    private val settingsSource: AppSettingsSource,
    private val syncRepository: SyncRepository,
    private val gpsCorrectionRepository: GpsCorrectionRepository,
) : ViewModel() {

    private val routeId: String = savedStateHandle["routeId"] ?: ""

    private var currentRoute: Route? = null

    /**
     * User's explicit track-view selection. `null` means "use the smart default":
     * [TrackView.CORRECTED] when a corrected trace exists, [TrackView.RAW] otherwise.
     * Resets to `null` after [deleteCorrectedTrace] so the default is re-evaluated.
     */
    private val _selectedTrackView = MutableStateFlow<TrackView?>(null)

    private val _events = Channel<RouteDetailEvent>(Channel.BUFFERED)

    /** One-shot UI events (export, share, server-send, correction). Collect in the Composable. */
    val events: Flow<RouteDetailEvent> = _events.receiveAsFlow()

    /** Live UI state exposed to [RouteDetailScreen]. */
    val uiState: StateFlow<RouteDetailUiState> = combine(
        routeRepository.observeById(routeId),
        bikeRepository.observeAll(),
        waveRepository.observeForRoute(routeId),
        settingsSource.settings,
        _selectedTrackView,
    ) { route, bikes, waves, settings, selectedView ->
        currentRoute = route
        buildUiState(route, bikes, waves, settings, selectedView)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RouteDetailUiState(),
    )

    // ── Actions ──────────────────────────────────────────────────────────────

    /**
     * Builds GPX content for the current route and emits [RouteDetailEvent.GpxSaved].
     *
     * The Composable is responsible for the actual file write / share-sheet invocation (🔬).
     * No-op if the route has not loaded yet.
     */
    fun exportGpx() {
        val route = currentRoute ?: return
        viewModelScope.launch {
            _events.send(
                RouteDetailEvent.GpxSaved(
                    content = GpxExporter.toGpx(route),
                    fileName = GpxExporter.fileName(route),
                )
            )
        }
    }

    /**
     * Emits [RouteDetailEvent.LinkCopied] with a deterministic route deep-link URL.
     *
     * The Composable is responsible for the actual clipboard write (🔬).
     * No-op if the route has not loaded yet.
     */
    fun shareRoute() {
        val route = currentRoute ?: return
        viewModelScope.launch {
            _events.send(RouteDetailEvent.LinkCopied(url = "mototracker://route/${route.id}"))
        }
    }

    /**
     * Enqueues the current route for server sync via [SyncRepository.enqueue] and emits
     * [RouteDetailEvent.ServerSent].
     *
     * No-op if the route has not loaded yet.
     */
    fun sendToServer() {
        val route = currentRoute ?: return
        viewModelScope.launch {
            syncRepository.enqueue(route.id)
            _events.send(RouteDetailEvent.ServerSent)
        }
    }

    /**
     * Enqueues this route for OSRM GPS road-correction and immediately attempts to process
     * the queue ([GpsCorrectionRepository.correctNow]).
     *
     * Emits [RouteDetailEvent.CorrectionQueued] when the request has been submitted.
     * Because [routeRepository.observeById] is live, any correction result written to Room
     * will automatically flow into [uiState] without further action.
     *
     * No-op if the route has not loaded yet.
     */
    fun correctNow() {
        val route = currentRoute ?: return
        viewModelScope.launch {
            gpsCorrectionRepository.enqueue(route.id)
            gpsCorrectionRepository.correctNow()
            _events.send(RouteDetailEvent.CorrectionQueued)
        }
    }

    /**
     * Clears the road-snapped trace via [RouteRepository.clearCorrectedTrace] and resets
     * the track-view selection to [TrackView.RAW].
     *
     * The raw GPS trace is permanent and is never touched by this action.
     * No-op if the route has not loaded yet.
     */
    fun deleteCorrectedTrace() {
        val route = currentRoute ?: return
        viewModelScope.launch {
            routeRepository.clearCorrectedTrace(route.id)
            _selectedTrackView.value = null
        }
    }

    /**
     * Updates the user's explicit track-view selection.
     *
     * @param view [TrackView.RAW] or [TrackView.CORRECTED]; selecting [TrackView.CORRECTED]
     *             when no corrected trace exists has no visible effect (the UiState keeps
     *             showing the raw track).
     */
    fun selectTrackView(view: TrackView) {
        _selectedTrackView.value = view
    }

    /**
     * Renames the current route.
     *
     * Trims [newName]; no-ops on blank or whitespace-only input. The live
     * [RouteRepository.observeById] stream re-emits after the update so the screen
     * title refreshes automatically without further action.
     *
     * @param newName New display name; trimmed before persisting.
     */
    fun rename(newName: String) {
        val route = currentRoute ?: return
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            routeRepository.rename(route.id, trimmed)
        }
    }

    /**
     * Assigns [bikeId] to the current route.
     *
     * The live [RouteRepository.observeById] stream re-emits after the update so
     * [uiState] refreshes automatically (same pattern as [rename]).
     * No-op when the route has not yet loaded.
     *
     * @param bikeId UUID of the motorcycle to assign, or `null` to clear.
     */
    fun setBike(bikeId: String?) {
        val route = currentRoute ?: return
        viewModelScope.launch {
            routeRepository.setBike(route.id, bikeId)
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun buildUiState(
        route: Route?,
        bikes: List<Bike>,
        waves: List<Wave>,
        settings: AppSettings,
        selectedView: TrackView?,
    ): RouteDetailUiState {
        if (route == null) return RouteDetailUiState(loading = false, routeNotFound = true)

        val units = if (settings.units == "imperial") Units.IMPERIAL else Units.METRIC
        val bikeMap = bikes.associateBy { it.id }
        val bike = route.bikeId?.let { bikeMap[it] }

        // Include all ACTIVE bikes plus the currently-assigned bike even if SOLD,
        // so the current selection is always representable in the picker.
        val assignableBikes = bikes
            .filter { it.status != BikeStatus.SOLD || it.id == route.bikeId }
            .map { BikePickerItemUi(id = it.id, name = it.name, sold = it.status == BikeStatus.SOLD) }

        val speedPts = ChartPolyline.speedPoints(route.speedJson)
        val elevPts = ChartPolyline.elevPoints(route.elevProfileJson)

        val elevGainLabel = if (route.elev > 0) {
            "↑ ${UnitFormatter.formatAltitude(route.elev, units)}"
        } else {
            ""
        }

        val meetingList = waves.map { wave ->
            MeetingUi(
                initials = wave.nick.take(2).uppercase(Locale.getDefault()),
                who = wave.nick,
                bikeName = wave.bikeName,
                place = wave.place,
                timeLabel = wave.timeLabel,
            )
        }

        val hasCorrectedTrace = route.correctedPathJson != null

        // Resolve effective view: explicit user selection, else default based on availability.
        val effectiveView = selectedView
            ?: if (hasCorrectedTrace) TrackView.CORRECTED else TrackView.RAW

        val trackPoints = when {
            effectiveView == TrackView.CORRECTED && hasCorrectedTrace ->
                TrackGeometry.parsePathJson(route.correctedPathJson)
            else ->
                TrackGeometry.parsePathJson(route.pathJson)
        }

        val correctionStatusLabelRes: Int? = when (route.correctionStatus) {
            CorrectionStatus.QUEUED         -> R.string.correction_status_queued
            CorrectionStatus.LOW_CONFIDENCE -> R.string.correction_status_low_confidence
            CorrectionStatus.DONE           -> R.string.correction_status_done
            CorrectionStatus.NONE           -> null
        }

        val confidenceLabel = route.confidence
            ?.let { String.format(Locale.US, "%.0f%%", it * 100.0) }
            ?: ""

        return RouteDetailUiState(
            loading = false,
            routeNotFound = false,
            name = route.name.ifBlank { route.id.take(8) },
            dateDisplay = formatDate(route.dateEpochMs),
            bikeName = bike?.name ?: "—",
            bikeSold = bike?.status == BikeStatus.SOLD,
            currentBikeId = route.bikeId,
            assignableBikes = assignableBikes,
            bikeChangeEnabled = assignableBikes.isNotEmpty(),
            distanceTile = StatTileUi(
                value = formatDistanceValue(route.km, units),
                unit = UnitFormatter.distanceUnitLabel(units),
            ),
            durationTile = StatTileUi(
                value = UnitFormatter.formatHms(route.durSec),
                unit = "h:m:s",
            ),
            avgTile = StatTileUi(
                value = formatSpeedValue(route.avg, units),
                unit = UnitFormatter.speedUnitLabel(units),
            ),
            maxTile = StatTileUi(
                value = formatSpeedValue(route.max, units),
                unit = UnitFormatter.speedUnitLabel(units),
            ),
            leanTile = StatTileUi(
                value = route.lean.roundToInt().toString(),
                unit = "°",
            ),
            fuelTile = StatTileUi(
                value = String.format(Locale.US, "%.1f", route.fuel),
                unit = "L",
            ),
            weather = RouteWeather.parse(route.wxJson),
            speedStroke = speedPts.stroke,
            speedFill = speedPts.fill,
            elevStroke = elevPts.stroke,
            elevFill = elevPts.fill,
            elevGainLabel = elevGainLabel,
            thumbnailPathD = RouteThumbnail.buildPathD(route.pathJson),
            trackPoints = trackPoints,
            meetings = meetingList,
            meetingsNone = meetingList.isEmpty(),
            queued = !route.synced,
            hasCorrectedTrace = hasCorrectedTrace,
            correctionStatus = route.correctionStatus,
            correctionStatusLabelRes = correctionStatusLabelRes,
            confidenceLabel = confidenceLabel,
            selectedTrackView = effectiveView,
        )
    }

    private fun formatDate(epochMs: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(epochMs))

    private fun formatDistanceValue(km: Double, units: Units): String {
        val value = if (units == Units.IMPERIAL) km * 0.621371 else km
        return String.format(Locale.US, "%.1f", value)
    }

    private fun formatSpeedValue(kmh: Double, units: Units): String {
        val value = if (units == Units.IMPERIAL) kmh * 0.621371 else kmh
        return value.roundToInt().toString()
    }
}
