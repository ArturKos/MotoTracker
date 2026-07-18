package com.mototracker.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.ui.theme.JetBrainsMonoFamily
import com.mototracker.ui.theme.MotoTracker

/**
 * Statistics screen — thin ViewModel wrapper that delegates to [StatsContent].
 *
 * @param modifier  Standard Compose modifier.
 * @param viewModel Hilt-injected [StatsViewModel].
 */
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsContent(state = state, modifier = modifier)
}

/**
 * Pure renderer for the Statistics screen: 4 summary tiles, a distance-per-month bar chart,
 * and a riding-style summary card. Extracted for Paparazzi screenshot testing.
 *
 * @param state    Pre-computed UI state; no ViewModels or side-effects inside.
 * @param modifier Standard Compose modifier.
 */
@Composable
fun StatsContent(
    state: StatsUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        StatTilesGrid(state)
        FuelTile(state)
        MonthBarCard(state)
        RidingStyleCard(state)
        LeanHistogramCard(state)
        if (state.records.isNotEmpty()) RecordsCard(state)
        if (state.badges.isNotEmpty()) AchievementsCard(state)
        Spacer(Modifier.height(8.dp))
    }
}

// ── Total fuel + cost tile (Q1) ───────────────────────────────────────────────

@Composable
private fun FuelTile(state: StatsUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MotoTracker.colors.panel)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.stat_fuel_total).uppercase(),
                style = MotoTracker.typography.label,
                color = MotoTracker.colors.dim,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.totalFuelDisplay,
                style = MotoTracker.typography.bigCardNumber.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                ),
                color = MotoTracker.colors.text,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.totalCostDisplay,
                style = MotoTracker.typography.label.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                ),
                color = MotoTracker.colors.accent,
                maxLines = 1,
            )
        }
    }
}

// ── Lean-angle histogram card (Q1) ────────────────────────────────────────────

@Composable
private fun LeanHistogramCard(state: StatsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MotoTracker.colors.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.stat_lean_histogram_title).uppercase(),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        Spacer(Modifier.height(12.dp))

        if (!state.hasLeanHistogram) {
            Text(
                text = stringResource(R.string.stat_lean_histogram_no_data),
                style = MotoTracker.typography.bigCardNumber,
                color = MotoTracker.colors.dim,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (bucket in state.leanHistogram) {
                    LeanBucketBar(bucket = bucket, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LeanBucketBar(bucket: LeanBucketUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxOf(bucket.heightFraction * 70f, 2f).dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(MotoTracker.colors.accent2),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(bucket.axisLabelRes),
            style = MotoTracker.typography.label.copy(fontSize = 9.sp),
            color = MotoTracker.colors.dim,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

// ── 2×2 summary tiles ─────────────────────────────────────────────────────────

@Composable
private fun StatTilesGrid(state: StatsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(
                label = stringResource(R.string.stat_total_distance),
                value = state.totalDistanceDisplay,
                accent = false,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stat_time_saddle),
                value = state.timeInSaddleDisplay,
                accent = false,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(
                label = stringResource(R.string.stat_rides),
                value = state.ridesCount.toString(),
                accent = false,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.stat_speed_record),
                value = state.topSpeedDisplay,
                accent = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MotoTracker.colors.panel)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MotoTracker.typography.bigCardNumber.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
            ),
            color = if (accent) MotoTracker.colors.accent else MotoTracker.colors.text,
            maxLines = 1,
        )
    }
}

// ── Distance / month bar chart ────────────────────────────────────────────────

@Composable
private fun MonthBarCard(state: StatsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MotoTracker.colors.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.stat_distance_per_month).uppercase(),
                style = MotoTracker.typography.label,
                color = MotoTracker.colors.dim,
            )
            if (state.yearLabel.isNotEmpty()) {
                Text(
                    text = state.yearLabel,
                    style = MotoTracker.typography.label.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MotoTracker.colors.dim,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state.monthBars.isEmpty()) {
            Text(
                text = "—",
                style = MotoTracker.typography.bigCardNumber,
                color = MotoTracker.colors.dim,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (bar in state.monthBars) {
                    MonthBar(bar = bar, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MonthBar(bar: MonthBarUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = bar.kmDisplay,
            style = MotoTracker.typography.label.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
            ),
            color = MotoTracker.colors.accent,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((bar.heightFraction * 80).dp)
                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                .background(MotoTracker.colors.accent),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = bar.monthLabel,
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

// ── Riding style summary ──────────────────────────────────────────────────────

@Composable
private fun RidingStyleCard(state: StatsUiState) {
    val style = state.style
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MotoTracker.colors.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.stat_style_summary).uppercase(),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        StyleBar(
            label = stringResource(R.string.stat_avg_lean),
            valueDisplay = style.avgLeanDisplay,
            fraction = style.avgLeanFraction,
            barColor = MotoTracker.colors.accent2,
        )
        StyleBar(
            label = stringResource(R.string.stat_avg_speed_label),
            valueDisplay = style.avgSpeedDisplay,
            fraction = style.avgSpeedFraction,
            barColor = MotoTracker.colors.accent,
        )
        StyleBar(
            label = stringResource(R.string.stat_total_climb),
            valueDisplay = style.totalClimbDisplay,
            fraction = style.totalClimbFraction,
            barColor = MotoTracker.colors.accent,
        )
    }
}

@Composable
private fun StyleBar(
    label: String,
    valueDisplay: String,
    fraction: Float,
    barColor: Color,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                style = MotoTracker.typography.label,
                color = MotoTracker.colors.dim,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = valueDisplay,
                style = MotoTracker.typography.label.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MotoTracker.colors.text,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = barColor,
            trackColor = MotoTracker.colors.panel2,
        )
    }
}

// ── Personal records card ─────────────────────────────────────────────────────

@Composable
private fun RecordsCard(state: StatsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MotoTracker.colors.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_records_title).uppercase(),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        for (record in state.records) {
            RecordRow(item = record)
        }
    }
}

@Composable
private fun RecordRow(item: RecordItemUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(item.labelRes).uppercase(),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        val valueText = if (item.unitRes != null) {
            "${item.valueDisplay} ${stringResource(item.unitRes)}"
        } else {
            item.valueDisplay
        }
        Text(
            text = valueText,
            style = MotoTracker.typography.label.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MotoTracker.colors.text,
        )
    }
}

// ── Achievements / badges card ────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementsCard(state: StatsUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MotoTracker.colors.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_achievements_title).uppercase(),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (badgeUi in state.badges) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = stringResource(badgeUi.nameRes),
                            style = MotoTracker.typography.label.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                            ),
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MotoTracker.colors.accent.copy(alpha = 0.15f),
                        labelColor = MotoTracker.colors.accent,
                    ),
                    border = null,
                )
            }
        }
    }
}
