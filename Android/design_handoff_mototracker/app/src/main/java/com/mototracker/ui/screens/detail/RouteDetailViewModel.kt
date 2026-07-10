package com.mototracker.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.core.format.ChartPolyline
import com.mototracker.core.format.RouteThumbnail
import com.mototracker.core.format.RouteWeather
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.model.Wave
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.WaveRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.ui.state.Units
import com.mototracker.core.format.UnitFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * ViewModel for the Route Detail screen.
 *
 * Reads [routeId] from [SavedStateHandle] (nav arg key `"routeId"`) and combines
 * a one-shot [RouteRepository.getById] with live [BikeRepository.observeAll],
 * [WaveRepository.observeForRoute], and [AppSettingsSource.settings] into a single
 * [StateFlow]<[RouteDetailUiState]>.
 *
 * @param savedStateHandle   Provides the `routeId` nav argument.
 * @param routeRepository    Source of the route to display.
 * @param bikeRepository     Source of bikes for name / sold-status resolution.
 * @param waveRepository     Source of Bluetooth wave meetups for this route.
 * @param settingsSource     Provides measurement units preference.
 */
@HiltViewModel
class RouteDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository,
    private val bikeRepository: BikeRepository,
    private val waveRepository: WaveRepository,
    private val settingsSource: AppSettingsSource,
) : ViewModel() {

    private val routeId: String = savedStateHandle["routeId"] ?: ""

    /** Live UI state exposed to [RouteDetailScreen]. */
    val uiState: StateFlow<RouteDetailUiState> = combine(
        flow { emit(routeRepository.getById(routeId)) },
        bikeRepository.observeAll(),
        waveRepository.observeForRoute(routeId),
        settingsSource.settings,
    ) { route, bikes, waves, settings ->
        buildUiState(route, bikes, waves, settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RouteDetailUiState(),
    )

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun buildUiState(
        route: Route?,
        bikes: List<Bike>,
        waves: List<Wave>,
        settings: AppSettings,
    ): RouteDetailUiState {
        if (route == null) return RouteDetailUiState(loading = false, routeNotFound = true)

        val units = if (settings.units == "imperial") Units.IMPERIAL else Units.METRIC
        val bikeMap = bikes.associateBy { it.id }
        val bike = route.bikeId?.let { bikeMap[it] }

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

        return RouteDetailUiState(
            loading = false,
            routeNotFound = false,
            name = route.name.ifBlank { route.id.take(8) },
            dateDisplay = formatDate(route.dateEpochMs),
            bikeName = bike?.name ?: "—",
            bikeSold = bike?.status == BikeStatus.SOLD,
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
            meetings = meetingList,
            meetingsNone = meetingList.isEmpty(),
            queued = !route.synced,
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
