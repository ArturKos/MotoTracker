package com.mototracker.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.ui.theme.JetBrainsMonoFamily
import com.mototracker.ui.theme.MotoTracker
import androidx.compose.ui.text.font.FontWeight

/**
 * Statistics screen — shows 4 summary tiles, a distance-per-month bar chart,
 * and a riding-style summary card with 3 labelled progress bars.
 *
 * This is a pure renderer: all aggregation happens in [StatsViewModel].
 * Because it renders on a real device, rendering correctness is marked 🔬.
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        StatTilesGrid(state)
        MonthBarCard(state)
        RidingStyleCard(state)
        Spacer(Modifier.height(8.dp))
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
