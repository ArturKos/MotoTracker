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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.mototracker.domain.fuel.FuelAdjustmentMode
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

    // R1: Off-ride fuel-correction dialog.
    if (state.showFuelCorrectionDialog) {
        BikeDetailFuelCorrectionDialog(
            currentRemainingL = state.fuelCorrectionCurrentRemaining,
            onConfirm = { mode, value -> viewModel.confirmFuelCorrection(mode, value) },
            onDismiss = { viewModel.dismissFuelCorrectionDialog() },
        )
    }

    BikeDetailContent(
        state = state,
        onRouteClick = onRouteClick,
        onFuelCorrectionClick = { viewModel.openFuelCorrectionDialog() },
        modifier = modifier,
    )
}

/**
 * Pure stateless renderer for the Bike Detail screen.
 *
 * Shows a header with the bike name and an optional SOLD badge, a stat grid
 * (ride count, total distance, total time, total fuel, optional cost, longest ride,
 * top speed), an off-ride fuel-correction button (R1), and a tappable list of the
 * bike's routes.
 *
 * @param state                  The current UI state produced by [BikeDetailViewModel].
 * @param onRouteClick           Called with the route UUID when the user taps a route row.
 * @param onFuelCorrectionClick  Called when the rider taps the off-ride fuel-correction button (R1).
 * @param modifier               Standard Compose modifier.
 */
@Composable
fun BikeDetailContent(
    state: BikeDetailUiState,
    onRouteClick: (String) -> Unit,
    onFuelCorrectionClick: () -> Unit = {},
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

        // ── R1: Off-ride fuel-correction action ──────────────────────────────
        item {
            OutlinedButton(
                onClick = onFuelCorrectionClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MotoTracker.colors.accent,
                ),
            ) {
                Text(stringResource(R.string.action_fuel_correction))
            }
            Spacer(modifier = Modifier.height(12.dp))
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

// ─────────────────────────────────────────────────────────────────────────────
// Off-ride fuel-correction dialog (R1)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dialog for correcting the remaining-fuel estimate while no ride is active (R1).
 *
 * Mirrors [com.mototracker.ui.screens.record.RecordingScreen]'s `FuelCorrectionDialog`
 * but is wired to [BikeDetailViewModel] so the event is persisted with `routeId = null`.
 *
 * @param currentRemainingL Pre-filled estimate; typically the bike's tank capacity (no engine running).
 * @param onConfirm         Called with the chosen [FuelAdjustmentMode] and value in litres.
 * @param onDismiss         Called when the user cancels or dismisses without confirming.
 */
@Composable
private fun BikeDetailFuelCorrectionDialog(
    currentRemainingL: Double,
    onConfirm: (mode: FuelAdjustmentMode, value: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(FuelAdjustmentMode.SET_ABSOLUTE) }
    var valueText by remember { mutableStateOf("%.1f".format(currentRemainingL)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_fuel_correction_title),
                style = MotoTracker.typography.body,
                color = MotoTracker.colors.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            if (mode != FuelAdjustmentMode.SET_ABSOLUTE) {
                                mode = FuelAdjustmentMode.SET_ABSOLUTE
                                valueText = "%.1f".format(currentRemainingL)
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (mode == FuelAdjustmentMode.SET_ABSOLUTE)
                                MotoTracker.colors.accent else MotoTracker.colors.dim,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.label_fuel_correction_set_absolute))
                    }
                    TextButton(
                        onClick = {
                            if (mode != FuelAdjustmentMode.DELTA) {
                                mode = FuelAdjustmentMode.DELTA
                                valueText = "0.0"
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (mode == FuelAdjustmentMode.DELTA)
                                MotoTracker.colors.accent else MotoTracker.colors.dim,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.label_fuel_correction_delta))
                    }
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text(stringResource(R.string.label_fuel_correction_litres)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val v = valueText.toDoubleOrNull() ?: return@TextButton
                    onConfirm(mode, v)
                },
            ) {
                Text(
                    text = stringResource(R.string.dialog_fuel_correction_confirm),
                    color = MotoTracker.colors.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    color = MotoTracker.colors.dim,
                )
            }
        },
    )
}
