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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for the Routes list screen.
 *
 * Combines [RouteRepository.observeAll], [BikeRepository.observeAll], the
 * units setting from [AppSettingsSource], and the in-memory [RoutesFilter] into
 * a single [RoutesUiState] stream. All list transformation is pure and testable —
 * no Room/DAO involvement; filtering happens on the already-observed list.
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

    private val _filter = MutableStateFlow(RoutesFilter())

    /** Live UI state exposed to the Routes screen. */
    val uiState: StateFlow<RoutesUiState> = combine(
        routeRepository.observeAll(),
        bikeRepository.observeAll(),
        settingsSource.settings,
        _filter,
    ) { routes, bikes, settings, filter ->
        buildUiState(routes, bikes, settings, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoutesUiState(),
    )

    // ── Public filter / sort API ──────────────────────────────────────────────

    /** Update the name search query. Pass an empty string to remove the name filter. */
    fun setQuery(q: String) { _filter.update { it.copy(query = q) } }

    /** Restrict the list to routes belonging to [bikeId]; pass `null` to show all bikes. */
    fun setBikeFilter(bikeId: String?) { _filter.update { it.copy(bikeId = bikeId) } }

    /**
     * Set an inclusive date-range filter.
     *
     * @param fromEpochMs Lower bound (inclusive) in epoch ms; `null` = no lower bound.
     * @param toEpochMs   Upper bound (inclusive) in epoch ms; `null` = no upper bound.
     */
    fun setDateRange(fromEpochMs: Long?, toEpochMs: Long?) {
        _filter.update { it.copy(fromEpochMs = fromEpochMs, toEpochMs = toEpochMs) }
    }

    /** Change the primary sort key and direction. */
    fun setSort(key: RouteSortKey, dir: SortDirection) {
        _filter.update { it.copy(sortKey = key, sortDir = dir) }
    }

    /** Reset all filter / sort criteria to defaults (DATE DESC, no name / bike / date filter). */
    fun clearFilters() { _filter.value = RoutesFilter() }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun buildUiState(
        routes: List<Route>,
        bikes: List<Bike>,
        settings: AppSettings,
        filter: RoutesFilter,
    ): RoutesUiState {
        val units = if (settings.units == "imperial") Units.IMPERIAL else Units.METRIC
        val bikeMap = bikes.associateBy { it.id }

        val filteredRoutes = applyFilter(routes, filter)
        val totalKm = filteredRoutes.sumOf { it.km }
        val cards = filteredRoutes.map { route -> toCard(route, bikeMap, units) }
        val availableBikes = bikes.map { BikeFilterOption(it.id, it.name) }

        return RoutesUiState(
            routeCount = filteredRoutes.size,
            totalRouteCount = routes.size,
            totalKmDisplay = UnitFormatter.formatDistance(totalKm, units),
            distanceUnitLabel = UnitFormatter.distanceUnitLabel(units),
            cards = cards,
            filter = filter,
            availableBikes = availableBikes,
        )
    }

    /**
     * Apply [filter] to [routes] and return the matching, sorted list.
     *
     * Filtering order:
     * 1. Name substring (Locale-independent lowercase, trimmed; empty = no filter).
     * 2. Bike ID equality when [RoutesFilter.bikeId] is non-null.
     * 3. Date range — [RoutesFilter.fromEpochMs] and [RoutesFilter.toEpochMs] are inclusive;
     *    a null bound means open-ended.
     *
     * Sorting: primary key per [RoutesFilter.sortKey] in [RoutesFilter.sortDir] direction.
     * Stable tie-break: `dateEpochMs DESC` then `id ASC` for deterministic output.
     */
    private fun applyFilter(routes: List<Route>, filter: RoutesFilter): List<Route> {
        val trimmedQuery = filter.query.trim().lowercase(Locale.ROOT)
        val filtered = routes.filter { route ->
            (trimmedQuery.isEmpty() || route.name.lowercase(Locale.ROOT).contains(trimmedQuery))
                && (filter.bikeId == null || route.bikeId == filter.bikeId)
                && (filter.fromEpochMs == null || route.dateEpochMs >= filter.fromEpochMs)
                && (filter.toEpochMs == null || route.dateEpochMs <= filter.toEpochMs)
        }
        return filtered.sortedWith { a, b ->
            val primary = when (filter.sortKey) {
                RouteSortKey.DATE -> a.dateEpochMs.compareTo(b.dateEpochMs)
                RouteSortKey.DISTANCE -> a.km.compareTo(b.km)
                RouteSortKey.DURATION -> a.durSec.compareTo(b.durSec)
                RouteSortKey.MAX_SPEED -> a.max.compareTo(b.max)
            }
            val directed = if (filter.sortDir == SortDirection.DESC) -primary else primary
            if (directed != 0) return@sortedWith directed
            // Stable tie-break: dateEpochMs DESC, then id ASC
            val dateCmp = b.dateEpochMs.compareTo(a.dateEpochMs)
            if (dateCmp != 0) dateCmp else a.id.compareTo(b.id)
        }
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
