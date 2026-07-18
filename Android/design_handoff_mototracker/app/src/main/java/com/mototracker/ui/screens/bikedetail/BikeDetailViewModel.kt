package com.mototracker.ui.screens.bikedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.core.format.MoneyFormatter
import com.mototracker.core.format.UnitFormatter
import com.mototracker.core.time.TimeProvider
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.data.model.Bike
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.FuelAdjustmentRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.fuel.FuelAdjustmentMode
import com.mototracker.domain.stats.BikeStatsCalculator
import com.mototracker.ui.state.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
 * Also manages the off-ride fuel-correction dialog (R1): [openFuelCorrectionDialog],
 * [confirmFuelCorrection], and [dismissFuelCorrectionDialog] mutate a separate
 * [_dialogState] flow that is merged into the main [uiState] via a 4-way combine.
 *
 * @param savedStateHandle           Provides the `bikeId` nav argument.
 * @param routeRepository            Source of lightweight route summaries.
 * @param bikeRepository             Source of the complete bike list.
 * @param settingsSource             Provides unit (metric/imperial) and currency preferences.
 * @param fuelAdjustmentRepository   Persistence for off-ride fuel-level correction events (R1).
 * @param timeProvider               Wall-clock source (injectable for tests).
 */
@HiltViewModel
class BikeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routeRepository: RouteRepository,
    private val bikeRepository: BikeRepository,
    private val settingsSource: AppSettingsSource,
    private val fuelAdjustmentRepository: FuelAdjustmentRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val bikeId: String = checkNotNull(savedStateHandle["bikeId"])

    /** Dialog open state: (open, currentRemainingL). Separate from the 3-flow data combine. */
    private val _dialogState = MutableStateFlow(false to 0.0)

    /** Live UI state exposed to the Bike Detail screen. */
    val uiState: StateFlow<BikeDetailUiState> = combine(
        routeRepository.observeSummaries(),
        bikeRepository.observeAll(),
        settingsSource.settings,
        _dialogState,
    ) { summaries, bikes, settings, (dialogOpen, currentRemaining) ->
        val bike = bikes.find { it.id == bikeId }
        if (bike == null) {
            BikeDetailUiState(
                isLoading = false,
                showFuelCorrectionDialog = dialogOpen,
                fuelCorrectionCurrentRemaining = currentRemaining,
            )
        } else {
            build(summaries, bike, settings).copy(
                showFuelCorrectionDialog = dialogOpen,
                fuelCorrectionCurrentRemaining = currentRemaining,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BikeDetailUiState(),
    )

    /**
     * Opens the off-ride fuel-correction dialog (R1).
     *
     * Pre-fills `currentRemaining` with the most-recent persisted correction's [litres] value
     * when one exists; falls back to the bike's tank capacity (full tank assumption) when no
     * prior correction has been recorded.
     */
    fun openFuelCorrectionDialog() {
        viewModelScope.launch {
            val bike = bikeRepository.observeAll().stateIn(viewModelScope).value
                .find { it.id == bikeId }
            val latestAdjustment = fuelAdjustmentRepository.latestForBike(bikeId)
            val currentRemaining = latestAdjustment?.litres ?: bike?.tankCapacityL ?: 0.0
            _dialogState.update { true to currentRemaining }
        }
    }

    /**
     * Applies the off-ride fuel correction (R1).
     *
     * Persists a [com.mototracker.domain.fuel.FuelAdjustmentEvent] with `routeId = null`
     * (no active route) and dismisses the dialog.
     *
     * @param mode  Whether the correction sets an absolute level or applies a signed delta.
     * @param value Correction value in litres.
     */
    fun confirmFuelCorrection(mode: FuelAdjustmentMode, value: Double) {
        _dialogState.update { false to 0.0 }
        viewModelScope.launch {
            runCatching {
                fuelAdjustmentRepository.addAdjustment(
                    bikeId = bikeId,
                    routeId = null,
                    epochMs = timeProvider.nowEpochMs(),
                    mode = mode,
                    litres = value,
                )
            }
        }
    }

    /** Closes the fuel-correction dialog without saving (R1). */
    fun dismissFuelCorrectionDialog() {
        _dialogState.update { false to 0.0 }
    }

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
            tankCapacityL = bike.tankCapacityL,
        )
    }
}
