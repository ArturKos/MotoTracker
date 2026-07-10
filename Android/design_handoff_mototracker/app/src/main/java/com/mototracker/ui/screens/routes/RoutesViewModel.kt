package com.mototracker.ui.screens.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.core.format.RouteThumbnail
import com.mototracker.core.format.UnitFormatter
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.Route
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.ui.state.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the Routes list screen.
 *
 * Combines [RouteRepository.observeAll], [BikeRepository.observeAll], and the
 * units setting from [AppSettingsSource] into a single [RoutesUiState] stream.
 *
 * @param routeRepository  Provides the live route list.
 * @param bikeRepository   Provides the live bike list for name resolution.
 * @param settingsSource   Provides the user's unit preference.
 */
@HiltViewModel
class RoutesViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val bikeRepository: BikeRepository,
    private val settingsSource: AppSettingsSource,
) : ViewModel() {

    /** Live UI state exposed to the Routes screen. */
    val uiState: StateFlow<RoutesUiState> = combine(
        routeRepository.observeAll(),
        bikeRepository.observeAll(),
        settingsSource.settings,
    ) { routes, bikes, settings ->
        buildUiState(routes, bikes, settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoutesUiState(),
    )

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun buildUiState(
        routes: List<Route>,
        bikes: List<Bike>,
        settings: AppSettings,
    ): RoutesUiState {
        val units = if (settings.units == "imperial") Units.IMPERIAL else Units.METRIC
        val bikeMap = bikes.associateBy { it.id }

        val totalKm = routes.sumOf { it.km }
        val cards = routes.map { route -> toCard(route, bikeMap, units) }

        return RoutesUiState(
            routeCount = routes.size,
            totalKmDisplay = UnitFormatter.formatDistance(totalKm, units),
            distanceUnitLabel = UnitFormatter.distanceUnitLabel(units),
            cards = cards,
        )
    }

    private fun toCard(
        route: Route,
        bikeMap: Map<String, Bike>,
        units: Units,
    ): RouteCardUi {
        val bike = route.bikeId?.let { bikeMap[it] }
        val bikeName = bike?.name ?: "—"
        val bikeSold = bike?.status == BikeStatus.SOLD

        return RouteCardUi(
            id = route.id,
            name = route.name.ifBlank { route.id.take(8) },
            dateDisplay = formatDate(route.dateEpochMs),
            bikeName = bikeName,
            bikeSold = bikeSold,
            distanceDisplay = UnitFormatter.formatDistance(route.km, units),
            distanceUnitLabel = UnitFormatter.distanceUnitLabel(units),
            durationDisplay = UnitFormatter.formatHms(route.durSec),
            maxSpeedDisplay = UnitFormatter.formatSpeed(route.max, units),
            thumbnailPathD = RouteThumbnail.buildPathD(route.pathJson),
            synced = route.synced,
        )
    }

    private fun formatDate(epochMs: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            .format(Date(epochMs))
}
