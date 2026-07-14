package com.mototracker.ui.screens.bikedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker

/**
 * Bike Detail screen — stateful wrapper resolving [BikeDetailViewModel] via Hilt.
 *
 * Delegates all rendering to [BikeDetailContent] to keep the Composable tree
 * testable and preview-friendly.
 *
 * @param onRouteClick  Called with the route UUID when the user taps a route row,
 *                      triggering navigation to `route_detail/{routeId}`.
 * @param modifier      Standard Compose modifier.
 * @param viewModel     Hilt-injected [BikeDetailViewModel].
 */
@Composable
fun BikeDetailScreen(
    onRouteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BikeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BikeDetailContent(state = state, onRouteClick = onRouteClick, modifier = modifier)
}

/**
 * Pure stateless renderer for the Bike Detail screen.
 *
 * Shows a header with the bike name and an optional SOLD badge, a stat grid
 * (ride count, total distance, total time, total fuel, optional cost, longest ride,
 * top speed), and a tappable list of the bike's routes.
 *
 * @param state         The current UI state produced by [BikeDetailViewModel].
 * @param onRouteClick  Called with the route UUID when the user taps a route row.
 * @param modifier      Standard Compose modifier.
 */
@Composable
fun BikeDetailContent(
    state: BikeDetailUiState,
    onRouteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MotoTracker.colors.bg)
            .padding(horizontal = 16.dp),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.bikeName,
                    color = MotoTracker.colors.text,
                    style = MotoTracker.typography.body.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (state.isSold) {
                    Spacer(modifier = Modifier.width(8.dp))
                    BikeDetailBadge(text = stringResource(R.string.tag_sold).uppercase())
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Stat tiles ───────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BikeStatTile(
                        label = stringResource(R.string.bike_detail_stat_rides),
                        value = state.rideCountDisplay,
                        modifier = Modifier.weight(1f),
                    )
                    BikeStatTile(
                        label = stringResource(R.string.bike_detail_stat_distance),
                        value = state.totalDistanceDisplay,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BikeStatTile(
                        label = stringResource(R.string.bike_detail_stat_time),
                        value = state.totalTimeDisplay,
                        modifier = Modifier.weight(1f),
                    )
                    BikeStatTile(
                        label = stringResource(R.string.bike_detail_stat_fuel),
                        value = state.totalFuelDisplay,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.totalCostDisplay != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BikeStatTile(
                            label = stringResource(R.string.bike_detail_stat_cost),
                            value = state.totalCostDisplay,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BikeStatTile(
                        label = stringResource(R.string.bike_detail_stat_longest),
                        value = state.longestRideDisplay,
                        modifier = Modifier.weight(1f),
                    )
                    BikeStatTile(
                        label = stringResource(R.string.bike_detail_stat_top_speed),
                        value = state.topSpeedDisplay,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ── Routes header ────────────────────────────────────────────────────
        item {
            Text(
                text = stringResource(R.string.bike_detail_routes_header).uppercase(),
                color = MotoTracker.colors.accent,
                style = MotoTracker.typography.label.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // ── Route list (empty or populated) ─────────────────────────────────
        if (state.routes.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.bike_detail_no_routes),
                    color = MotoTracker.colors.dim,
                    style = MotoTracker.typography.label,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            items(state.routes) { route ->
                BikeRouteRow(route = route, onClick = { onRouteClick(route.id) })
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun BikeStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MotoTracker.colors.panel2)
            .padding(12.dp),
    ) {
        Column {
            Text(
                text = label,
                color = MotoTracker.colors.dim,
                style = MotoTracker.typography.label,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = MotoTracker.colors.text,
                style = MotoTracker.typography.body.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun BikeDetailBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MotoTracker.colors.panel2)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = MotoTracker.colors.dim,
            style = MotoTracker.typography.label.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun BikeRouteRow(
    route: BikeRouteRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.name,
                color = MotoTracker.colors.text,
                style = MotoTracker.typography.body,
            )
            Text(
                text = route.dateDisplay,
                color = MotoTracker.colors.dim,
                style = MotoTracker.typography.label,
            )
        }
        Text(
            text = route.distanceDisplay,
            color = MotoTracker.colors.dim,
            style = MotoTracker.typography.label,
        )
    }
    HorizontalDivider(color = MotoTracker.colors.line, thickness = 0.5.dp)
}
