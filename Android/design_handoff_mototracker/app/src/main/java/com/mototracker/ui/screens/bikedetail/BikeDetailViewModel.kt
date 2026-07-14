package com.mototracker.ui.screens.bikedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.core.format.MoneyFormatter
import com.mototracker.core.format.UnitFormatter
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.stats.BikeStatsCalculator
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
 * ViewModel for the Bike Detail screen (E2).
 *
 * Reads `bikeId` from [SavedStateHandle] (nav arg key `"bikeId"`) and combines
 * [RouteRepository.observeSummaries] with [BikeRepository.observeAll] and
 * [AppSettingsSource.settings] into a single [StateFlow]<[BikeDetailUiState]>.
 *
 * Delegates aggregation to [BikeStatsCalculator] and formatting to [UnitFormatter]
 * and [MoneyFormatter], matching the pattern of [com.mototracker.ui.screens.stats.StatsViewModel].
 *
 * @param savedStateHandle  Provides the `bikeId` nav argument.
 * @param routeRepository   Source of lightweight route summaries.
 * @param bikeRepository    Source of the complete bike list.
 * @param settingsSource    Provides unit (metric/imperial) and currency preferences.
 */
@HiltViewModel
class BikeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository,
    private val bikeRepository: BikeRepository,
    private val settingsSource: AppSettingsSource,
) : ViewModel() {

    private val bikeId: String = checkNotNull(savedStateHandle["bikeId"])

    /** Live UI state exposed to the Bike Detail screen. */
    val uiState: StateFlow<BikeDetailUiState> = combine(
        routeRepository.observeSummaries(),
        bikeRepository.observeAll(),
        settingsSource.settings,
    ) { summaries, bikes, settings ->
        val bike = bikes.find { it.id == bikeId }
        if (bike == null) BikeDetailUiState(isLoading = false)
        else build(summaries, bike, settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BikeDetailUiState(),
    )

    private fun build(
        summaries: List<RouteSummaryModel>,
        bike: Bike,
        settings: AppSettings,
    ): BikeDetailUiState {
        val units = if (settings.units == "imperial") Units.IMPERIAL else Units.METRIC
        val currency = settings.currency
        val stats = BikeStatsCalculator.compute(summaries, bike)

        val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())
        val routes = summaries
            .filter { it.bikeId == bike.id }
            .sortedByDescending { it.dateEpochMs }
            .map { s ->
                BikeRouteRowUi(
                    id = s.id,
                    name = s.name,
                    dateDisplay = dateFormat.format(Date(s.dateEpochMs)),
                    distanceDisplay = UnitFormatter.formatDistance(s.km, units),
                )
            }

        val totalCostDisplay = stats.totalCostOrNull?.let { cost ->
            MoneyFormatter.format(cost, currency)
        }

        return BikeDetailUiState(
            bikeName = bike.name,
            isSold = bike.status == BikeStatus.SOLD,
            rideCountDisplay = stats.rideCount.toString(),
            totalDistanceDisplay = UnitFormatter.formatDistance(stats.totalDistanceKm, units),
            totalTimeDisplay = UnitFormatter.formatHm(stats.totalElapsedSec),
            totalFuelDisplay = String.format(Locale.US, "%.1f L", stats.totalFuelL),
            totalCostDisplay = totalCostDisplay,
            longestRideDisplay = UnitFormatter.formatDistance(stats.longestRideKm, units),
            topSpeedDisplay = UnitFormatter.formatSpeed(stats.topSpeedKmh, units),
            routes = routes,
            isLoading = false,
        )
    }
}
