package com.mototracker.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mototracker.R
import com.mototracker.core.format.MoneyFormatter
import com.mototracker.core.format.UnitFormatter
import com.mototracker.data.model.Bike
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.repository.BikeRepository
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.domain.stats.Badge
import com.mototracker.domain.stats.FuelCostCalculator
import com.mototracker.domain.stats.LeanHistogram
import com.mototracker.domain.stats.PersonalRecordsCalculator
import com.mototracker.ui.state.Units
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * ViewModel for the Statistics screen.
 *
 * Combines [RouteRepository.observeSummaries], [BikeRepository.observeAll], and
 * [AppSettingsSource.settings] into a single [StatsUiState] stream. The lightweight
 * summary stream avoids loading GPS trace blobs for what is a scalar aggregation screen.
 *
 * @param routeRepository  Provides the live route summary list.
 * @param bikeRepository   Provides the live bike list for fuel-price fallback resolution (Q1).
 * @param settingsSource   Provides the user's unit and currency preferences.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val bikeRepository: BikeRepository,
    private val settingsSource: AppSettingsSource,
) : ViewModel() {

    /** Live UI state exposed to the Stats screen. */
    val uiState: StateFlow<StatsUiState> = combine(
        routeRepository.observeSummaries(),
        bikeRepository.observeAll(),
        settingsSource.settings,
    ) { summaries, bikes, settings ->
        build(summaries, bikes, settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun build(
        summaries: List<RouteSummaryModel>,
        bikes: List<Bike>,
        settings: AppSettings,
    ): StatsUiState {
        val units = if (settings.units == "imperial") Units.IMPERIAL else Units.METRIC

        val totalKm = summaries.sumOf { it.km }
        val totalSec = summaries.sumOf { it.durSec }
        val topSpeedKmh = summaries.maxOfOrNull { it.max } ?: 0.0
        val avgLeanDeg = if (summaries.isEmpty()) 0.0 else summaries.map { it.lean }.average()
        val avgSpeedKmh = if (summaries.isEmpty()) 0.0 else summaries.map { it.avg }.average()
        val totalClimbM = summaries.sumOf { it.elev }

        val avgSpeedDisplay = UnitFormatter.formatSpeed(avgSpeedKmh, units)
        val avgSpeedFractionKmh = avgSpeedKmh / 100.0

        val yearLabel = if (summaries.isEmpty()) "" else {
            val newestMs = summaries.maxOf { it.dateEpochMs }
            Calendar.getInstance().apply { timeInMillis = newestMs }.get(Calendar.YEAR).toString()
        }

        val personalRecords = PersonalRecordsCalculator.computeFromSummaries(summaries)

        // Q1 — Total fuel + cost
        val bikePriceById: Map<String, Double?> = bikes.associate { it.id to it.fuelPricePerL }
        val fuelTotals = FuelCostCalculator.compute(summaries, bikePriceById)
        val totalFuelDisplay = String.format(Locale.US, "%.1f L", fuelTotals.totalFuelL)
        val totalCostDisplay = fuelTotals.totalCost
            ?.let { MoneyFormatter.format(it, settings.currency) }
            ?: "—"

        // Q1 — Lean histogram
        val decodedHistograms = summaries.mapNotNull { LeanHistogram.decode(it.leanHistogramJson) }
        val hasLeanHistogram = decodedHistograms.isNotEmpty()
        val aggregatedCounts = LeanHistogram.aggregate(decodedHistograms)
        val leanHistogram = buildLeanHistogram(aggregatedCounts)

        return StatsUiState(
            totalDistanceDisplay = UnitFormatter.formatDistance(totalKm, units),
            distanceUnitLabel = UnitFormatter.distanceUnitLabel(units),
            timeInSaddleDisplay = UnitFormatter.formatHm(totalSec),
            ridesCount = summaries.size,
            topSpeedDisplay = UnitFormatter.formatSpeed(topSpeedKmh, units),
            speedUnitLabel = UnitFormatter.speedUnitLabel(units),
            monthBars = buildMonthBars(summaries, units),
            yearLabel = yearLabel,
            style = RidingStyleUi(
                avgLeanDisplay = "${avgLeanDeg.roundToInt()}°",
                avgLeanFraction = (avgLeanDeg / 60.0).toFloat().coerceIn(0f, 1f),
                avgSpeedDisplay = avgSpeedDisplay,
                avgSpeedFraction = avgSpeedFractionKmh.toFloat().coerceIn(0f, 1f),
                totalClimbDisplay = UnitFormatter.formatAltitude(totalClimbM, units),
                totalClimbFraction = (totalClimbM / 8000.0).toFloat().coerceIn(0f, 1f),
            ),
            records = buildRecords(personalRecords, units),
            badges = personalRecords.earnedBadges.map { badge ->
                BadgeUi(badge = badge, nameRes = badge.nameRes())
            },
            totalFuelDisplay = totalFuelDisplay,
            totalCostDisplay = totalCostDisplay,
            leanHistogram = leanHistogram,
            hasLeanHistogram = hasLeanHistogram,
        )
    }

    private fun buildLeanHistogram(counts: IntArray): List<LeanBucketUi> {
        val labelRes = listOf(
            R.string.stat_lean_bucket_0_10,
            R.string.stat_lean_bucket_10_20,
            R.string.stat_lean_bucket_20_30,
            R.string.stat_lean_bucket_30_40,
            R.string.stat_lean_bucket_40_plus,
        )
        val maxCount = counts.maxOrNull()?.takeIf { it > 0 } ?: return labelRes.mapIndexed { i, res ->
            LeanBucketUi(axisLabelRes = res, heightFraction = 0f, count = 0)
        }
        return labelRes.mapIndexed { i, res ->
            LeanBucketUi(
                axisLabelRes = res,
                heightFraction = (counts[i].toFloat() / maxCount).coerceIn(0f, 1f),
                count = counts[i],
            )
        }
    }

    private fun buildRecords(
        records: com.mototracker.domain.stats.PersonalRecords,
        units: Units,
    ): List<RecordItemUi> = if (records.longestRideRouteId == null) {
        emptyList()
    } else {
        listOf(
            RecordItemUi(
                labelRes = R.string.rec_longest_ride,
                valueDisplay = UnitFormatter.formatDistance(records.longestRideKm, units),
            ),
            RecordItemUi(
                labelRes = R.string.rec_fastest_avg,
                valueDisplay = UnitFormatter.formatSpeed(records.fastestAvgSpeedKmh, units),
            ),
            RecordItemUi(
                labelRes = R.string.rec_top_speed,
                valueDisplay = UnitFormatter.formatSpeed(records.topSpeedKmh, units),
            ),
            RecordItemUi(
                labelRes = R.string.rec_highest_ascent,
                valueDisplay = UnitFormatter.formatAltitude(records.highestAscentM, units),
            ),
            RecordItemUi(
                labelRes = R.string.rec_best_month,
                valueDisplay = UnitFormatter.formatDistance(records.bestMonthKm, units),
            ),
            RecordItemUi(
                labelRes = R.string.rec_day_streak,
                valueDisplay = "${records.longestDayStreak}",
                unitRes = R.string.unit_days,
            ),
        )
    }

    /** Maps a [Badge] to its display-name string resource ID. */
    private fun Badge.nameRes(): Int = when (this) {
        Badge.FIRST_RIDE -> R.string.badge_first_ride
        Badge.CENTURY -> R.string.badge_century
        Badge.THOUSAND_CLUB -> R.string.badge_thousand_club
        Badge.SPEED_DEMON -> R.string.badge_speed_demon
        Badge.MOUNTAIN_GOAT -> R.string.badge_mountain_goat
        Badge.MARATHON_MONTH -> R.string.badge_marathon_month
        Badge.STREAK_3 -> R.string.badge_streak_3
    }

    /**
     * Builds 6 consecutive calendar months ending at the month of the newest route.
     * Returns an empty list when [summaries] is empty.
     *
     * Each bar's [MonthBarUi.heightFraction] is computed as `0.18 + (km/maxKm)*0.82`,
     * matching the prototype formula (prototype line 958). When all bars have 0 km the
     * fraction is clamped to `0.18f`.
     */
    private fun buildMonthBars(summaries: List<RouteSummaryModel>, units: Units): List<MonthBarUi> {
        if (summaries.isEmpty()) return emptyList()

        val newestMs = summaries.maxOf { it.dateEpochMs }

        val slots = (5 downTo 0).map { offset ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = newestMs
                add(Calendar.MONTH, -offset)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        }

        val kmByMonth = mutableMapOf<Pair<Int, Int>, Double>()
        for (s in summaries) {
            val cal = Calendar.getInstance().apply { timeInMillis = s.dateEpochMs }
            val key = Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            kmByMonth[key] = (kmByMonth[key] ?: 0.0) + s.km
        }

        val maxKm = slots.maxOfOrNull { kmByMonth[it] ?: 0.0 } ?: 0.0

        return slots.map { (year, month) ->
            val km = kmByMonth[Pair(year, month)] ?: 0.0
            val fraction = if (maxKm > 0.0) {
                (0.18 + (km / maxKm) * 0.82).toFloat().coerceIn(0f, 1f)
            } else {
                0.18f
            }
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val monthLabel = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
                ?: (month + 1).toString()
            MonthBarUi(
                monthLabel = monthLabel,
                kmDisplay = UnitFormatter.formatDistance(km, units),
                heightFraction = fraction,
            )
        }
    }
}
