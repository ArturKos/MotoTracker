package com.mototracker.ui.screens.record

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.data.model.Rider
import com.mototracker.domain.fuel.FuelAdjustmentMode
import com.mototracker.core.format.CoordFormat
import com.mototracker.core.format.CoordinateClipboard
import com.mototracker.core.format.CoordinateFormatter
import com.mototracker.data.battery.BatteryOptimizationIntents
import com.mototracker.domain.fuel.FuelCostCalculator
import com.mototracker.domain.fuel.FuelRangeColor
import com.mototracker.domain.fuel.FuelRangeIndicator
import com.mototracker.domain.location.GnssSignalLevel
import com.mototracker.domain.recording.RecordingControl
import com.mototracker.domain.recording.RecordingControls
import com.mototracker.domain.recording.RecordingMetrics
import com.mototracker.service.RecordingService
import com.mototracker.ui.permissions.AppFeaturePermission
import com.mototracker.ui.permissions.PermissionDeniedBanner
import com.mototracker.ui.permissions.rememberFeaturePermission
import com.mototracker.ui.theme.FuelDangerRed
import com.mototracker.ui.theme.MotoTracker
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Recording screen — ViewModel + permission wrapper that delegates to [RecordingContent].
 *
 * Manages the foreground service lifecycle, orientation lock, and location permission;
 * all rendering is in [RecordingContent]. Map tiles, GPS, and sensors are on-device only (🔬).
 *
 * @param modifier   Standard Compose modifier.
 * @param viewModel  Hilt-injected recording view model.
 */
@Composable
fun RecordingScreen(
    modifier: Modifier = Modifier,
    viewModel: RecordingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val locationPerm = rememberFeaturePermission(
        feature = AppFeaturePermission.LOCATION,
        companion = listOf(AppFeaturePermission.NOTIFICATIONS),
    )

    fun dispatchEvent(event: RecordingEvent) {
        if (event is RecordingEvent.Start) {
            locationPerm.requestThen { viewModel.onEvent(event) }
        } else {
            viewModel.onEvent(event)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecordingEffect.Saved -> {
                    val msg = if (effect.offline) context.getString(R.string.toast_saved_local)
                              else context.getString(R.string.toast_ride_saved)
                    snackbarHostState.showSnackbar(msg)
                }
                is RecordingEffect.NavigateToDetail -> { /* wired in B4 */ }
            }
        }
    }

    LaunchedEffect(state.phase) {
        when (state.phase) {
            RecordingPhase.Recording -> startRecordingService(context, state.activeRouteId)
            RecordingPhase.Idle -> stopRecordingService(context)
            RecordingPhase.Paused -> Unit
        }
    }

    // Lock orientation to portrait while recording/paused so the accelerometer axes stay
    // consistent with lean-angle calibration.  Released (Unspecified) on Idle or disposal.
    // (🔬 physical rotation lock + lean correctness verified on-device)
    DisposableEffect(state.phase) {
        val activity = context.findActivity()
        val orientation = requestedOrientationFor(state.phase)
        if (activity != null) {
            activity.requestedOrientation = when (orientation) {
                RecordOrientation.LockedPortrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                RecordOrientation.Unspecified -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Apply FLAG_KEEP_SCREEN_ON while the user's setting is on AND a ride is active.
    // Cleared immediately on Idle and unconditionally on disposal so leaving the screen
    // never leaves the flag dangling.  (🔬 actual screen-stays-on verified on-device)
    DisposableEffect(state.keepScreenOn, state.phase) {
        val activity = context.findActivity()
        if (shouldKeepScreenOn(state.keepScreenOn, state.phase)) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    RecordingContent(
        state = state,
        onEvent = ::dispatchEvent,
        locationPermDenied = locationPerm.denied,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Pure renderer for the Recording screen: compact chip row, metric tiles, and control strip.
 * Extracted for Roborazzi screenshot testing — no ViewModels, services, or permissions.
 *
 * @param state               Pre-computed recording UI state.
 * @param onEvent             Dispatches [RecordingEvent]s upward.
 * @param locationPermDenied  When `true` the permission-denied banner is shown instead of the Start button.
 * @param snackbarHostState   Snackbar host; defaults to a fresh instance for standalone use.
 * @param modifier            Standard Compose modifier.
 */
@Composable
fun RecordingContent(
    state: RecordingUiState,
    onEvent: (RecordingEvent) -> Unit = {},
    locationPermDenied: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
) {
    // G3: Resolve display heading — always prefer magnetometer; GPS bearing is fallback only.
    val displayHeadingDeg = CompassMath.selectDisplayHeading(
        state.phase, state.liveHeadingDeg, state.metrics.headingDeg
    )
    // F2: Live lean — always from sensor; falls back to last-recorded value.
    val displayLeanDeg = state.liveLeanDeg ?: state.metrics.currentLeanDeg

    // B20: Resume-or-discard prompt when an interrupted session is detected on startup.
    if (state.resumableSession != null) {
        ResumeSessionDialog(
            onResume = { onEvent(RecordingEvent.ResumeSession) },
            onDiscard = { onEvent(RecordingEvent.DiscardSession) },
        )
    }

    // G5: Refuel input dialog shown when the fuel-pump icon is tapped.
    if (state.showRefuelDialog) {
        RefuelDialog(
            initialLitres = state.refuelDialogLitres,
            initialPricePerL = state.refuelDialogPricePerL,
            onConfirm = { litres, pricePerL -> onEvent(RecordingEvent.ConfirmRefuel(litres, pricePerL)) },
            onDismiss = { onEvent(RecordingEvent.DismissRefuelDialog) },
        )
    }

    // J4: Stop-confirmation dialog shown when the user taps the Stop button.
    if (state.showStopConfirmDialog) {
        StopConfirmDialog(
            onConfirm = { onEvent(RecordingEvent.ConfirmStop) },
            onDismiss = { onEvent(RecordingEvent.DismissStopDialog) },
        )
    }

    // O1: Battery-optimization exemption dialog shown before the first screen-off recording.
    if (state.showBatteryOptPrompt) {
        val context = LocalContext.current
        BatteryOptPromptDialog(
            onConfirm = {
                onEvent(RecordingEvent.BatteryOptConfirm)
                try {
                    context.startActivity(
                        BatteryOptimizationIntents.requestIgnoreIntent(context.packageName)
                    )
                } catch (_: Exception) { /* intent not resolvable — fail silently */ }
            },
            onDismiss = { onEvent(RecordingEvent.BatteryOptDismiss) },
        )
    }

    // R1: Fuel-level correction dialog shown when the rider taps the correction affordance.
    if (state.showFuelCorrectionDialog) {
        FuelCorrectionDialog(
            currentRemainingL = state.fuelCorrectionCurrentRemaining,
            onConfirm = { mode, value -> onEvent(RecordingEvent.ConfirmFuelCorrection(mode, value)) },
            onDismiss = { onEvent(RecordingEvent.DismissFuelCorrectionDialog) },
        )
    }

    // X2: In-range group roster sheet.
    if (state.showGroupRosterSheet) {
        GroupRosterSheet(
            riders = state.inRangeRiders,
            onToggleGroup = { shortId, inGroup -> onEvent(RecordingEvent.ToggleGroup(shortId, inGroup)) },
            onDismiss = { onEvent(RecordingEvent.DismissGroupRoster) },
        )
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── Tiles — fills remaining space; adaptive sizing primary, scroll fallback ─
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MotoTracker.colors.bg),
            ) {
                val sizing = RecordLayoutSizing.forHeight(maxHeight.value.toInt())
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                ) {
                    // ── Compact chip row: GPS sat-count left, live coordinates right (K7, P1, S1) ─
                    // Range info is in the fuel readout below; weather is on Route detail.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GpsChip(satCount = state.gpsSatCount, onRoad = state.gpsOnRoad)
                        Spacer(Modifier.weight(1f))
                        CoordReadout(
                            lat = state.liveLat,
                            lng = state.liveLng,
                            coordFormat = state.coordFormat,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    SpeedAndTimeRow(state = state, headingDeg = displayHeadingDeg, sizing = sizing)
                    Spacer(Modifier.height(sizing.rowSpacingDp.dp))
                    DistanceAltitudeFuelRow(state = state, sizing = sizing)
                    FuelTankRow(state = state, sizing = sizing)
                    Spacer(Modifier.height(sizing.rowSpacingDp.dp))
                    LeanRow(state = state, currentLeanDeg = displayLeanDeg)
                    Spacer(Modifier.height(sizing.rowSpacingDp.dp))
                    TimersRow(state = state, sizing = sizing)
                    Spacer(Modifier.height(sizing.rowSpacingDp.dp))
                    if (locationPermDenied && state.phase == RecordingPhase.Idle) {
                        PermissionDeniedBanner(
                            text = stringResource(R.string.perm_location_denied),
                            onRetry = { onEvent(RecordingEvent.Start) },
                        )
                    } else {
                        RecordingControlRow(
                            phase = state.phase,
                            metrics = state.metrics,
                            sizing = sizing,
                            onEvent = onEvent,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Overlay chips
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps a [GnssSignalLevel] to the Compose [Color] used as the GPS chip's text/accent colour.
 *
 * NONE → [FuelDangerRed] (no satellites; mirrors fuel-danger pattern);
 * ACQUIRING → accent2 (amber; signal improving); FIXED → accent (full fix, speed colour).
 */
@Composable
private fun gnssSignalTint(level: GnssSignalLevel): Color = when (level) {
    GnssSignalLevel.NONE      -> FuelDangerRed
    GnssSignalLevel.ACQUIRING -> MotoTracker.colors.accent2
    GnssSignalLevel.FIXED     -> MotoTracker.colors.accent
}

/**
 * Centred chip showing GPS satellite count, colour-coded by signal quality, with an optional
 * road-correction indicator (K7).
 *
 * Text colour transitions from red (0 sats) through amber (1–3) to accent green/blue (≥ 4)
 * via [GnssSignalLevel.fromSatelliteCount].
 *
 * @param satCount Number of GPS satellites in use.
 * @param onRoad   Whether GPS-to-road correction is active.
 */
@Composable
private fun GpsChip(satCount: Int, onRoad: Boolean, modifier: Modifier = Modifier) {
    val level = GnssSignalLevel.fromSatelliteCount(satCount)
    val tint = gnssSignalTint(level)
    val satAbbr = stringResource(R.string.label_sat_abbr)
    val text = buildString {
        append("GPS · ")
        append(satCount)
        append(" ")
        append(satAbbr)
        if (onRoad) append(" · ").append(stringResource(R.string.label_on_road))
    }
    InfoChip(text = text, contentColor = tint, modifier = modifier)
}

@Composable
private fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MotoTracker.colors.text,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MotoTracker.colors.panel.copy(alpha = 0.85f),
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            style = MotoTracker.typography.label,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Live GPS coordinate readout shown to the right of the GPS sat-count chip in the
 * compact chip row (P1, S1).
 *
 * Displays coordinates formatted per [coordFormat] once a fix is available; shows a
 * placeholder resource until then. Uses JetBrains Mono at 11 sp with soft-wrapping
 * enabled (no ellipsis) so the widest UTM string (≈18 chars) always fits on ~360dp
 * screens without clipping.
 *
 * **Tap** copies all three coordinate formats (DD/DMS/UTM) to the clipboard.
 * **Double-tap** copies a Google Maps URL to the clipboard.
 * Both gestures no-op and show a toast when there is no GPS fix (lat/lng are null).
 *
 * @param lat         Live WGS-84 latitude in decimal degrees, or null before first fix.
 * @param lng         Live WGS-84 longitude in decimal degrees, or null before first fix.
 * @param coordFormat Desired display format (DD / DMS / UTM).
 * @param textAlign   Horizontal text alignment within the composable; defaults to [TextAlign.Center].
 * @param modifier    Standard Compose modifier.
 */
@Composable
private fun CoordReadout(
    lat: Double?,
    lng: Double?,
    coordFormat: CoordFormat,
    textAlign: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.coord_copied_toast)
    val mapsMsg = stringResource(R.string.coord_maps_link_copied_toast)
    val noPositionMsg = stringResource(R.string.coord_no_position_toast)

    val text = if (lat != null && lng != null) {
        CoordinateFormatter.format(lat, lng, coordFormat)
    } else {
        stringResource(R.string.gps_no_fix)
    }
    Text(
        text = text,
        style = MotoTracker.typography.bigCardNumber.copy(fontSize = 11.sp),
        color = MotoTracker.colors.dim,
        softWrap = true,
        textAlign = textAlign,
        modifier = modifier.pointerInput(lat, lng) {
            detectTapGestures(
                onTap = {
                    if (lat != null && lng != null) {
                        context.copyToClipboard(CoordinateClipboard.clipboardText(lat, lng))
                        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, noPositionMsg, Toast.LENGTH_SHORT).show()
                    }
                },
                onDoubleTap = {
                    if (lat != null && lng != null) {
                        context.copyToClipboard(CoordinateClipboard.mapsUrl(lat, lng))
                        Toast.makeText(context, mapsMsg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, noPositionMsg, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        },
    )
}

/** Copies [text] to the system clipboard with a blank label. */
private fun Context.copyToClipboard(text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("", text))
}

// ─────────────────────────────────────────────────────────────────────────────
// Metric tiles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpeedAndTimeRow(state: RecordingUiState, headingDeg: Float, sizing: RecordSizing) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SpeedTile(
            speedKmh = state.liveSpeedKmh ?: state.metrics.currentSpeedKmh,
            speedFontSp = sizing.speedFontSp,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        CompassDial(
            headingDeg = headingDeg,
            compassDiameterDp = sizing.compassDiameterDp,
            bigNumberFontSp = sizing.bigNumberFontSp,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun SpeedTile(speedKmh: Double, speedFontSp: Int, modifier: Modifier = Modifier) {
    MetricTile(modifier = modifier) {
        Text(
            text = stringResource(R.string.tile_speed),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        Text(
            text = String.format(Locale.US, "%.0f", speedKmh),
            style = MotoTracker.typography.recordSpeed.copy(fontSize = speedFontSp.sp),
            color = MotoTracker.colors.accent,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.unit_speed_kmh),
            style = MotoTracker.typography.bodySmall,
            color = MotoTracker.colors.dim,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (speedKmh / 160.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = MotoTracker.colors.accent,
            trackColor = MotoTracker.colors.panel2,
        )
    }
}

/** Formats [sec] as HH:MM:SS. */
private fun formatHms(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

@Composable
private fun TimeTile(durationSec: Long, modifier: Modifier = Modifier) {
    MetricTile(modifier = modifier) {
        Text(
            text = stringResource(R.string.tile_ride_time),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        Text(
            text = formatHms(durationSec),
            style = MotoTracker.typography.timer,
            color = MotoTracker.colors.text,
        )
    }
}

/**
 * Compact row showing ride time and moving time side by side.
 *
 * Uses [MotoTypography.bigCardNumber] (30 sp mono) instead of [MotoTypography.timer] (52 sp)
 * so both timers fit without wrapping on a P20-class screen (w411dp).
 *
 * @param state Current recording UI state; reads [RecordingMetrics.durationSec] and [RecordingMetrics.movingSec].
 */
@Composable
private fun TimersRow(state: RecordingUiState, sizing: RecordSizing) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MetricTile(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tile_ride_time),
                style = MotoTracker.typography.label,
                color = MotoTracker.colors.dim,
            )
            Text(
                text = formatHms(state.metrics.durationSec),
                style = MotoTracker.typography.bigCardNumber.copy(fontSize = sizing.bigNumberFontSp.sp),
                color = MotoTracker.colors.text,
            )
        }
        MetricTile(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.tile_moving_time),
                style = MotoTracker.typography.label,
                color = MotoTracker.colors.dim,
            )
            Text(
                text = formatHms(state.metrics.movingSec),
                style = MotoTracker.typography.bigCardNumber.copy(fontSize = sizing.bigNumberFontSp.sp),
                color = MotoTracker.colors.text,
            )
        }
    }
}

/**
 * Fuel-tank readout row shown when the current bike has a tank capacity configured.
 *
 * Displays remaining fuel, remaining range, and running fuel cost (when [RecordingUiState.fuelPricePerL]
 * is set). Low-fuel state is conveyed by the tinted fuel icon in [FuelRangeIndicator]; no separate
 * warning row is rendered here. The fill-to-full action has moved to the control strip (G2); this
 * composable is a pure readout with no interactive elements.
 *
 * Hidden entirely when [RecordingUiState.metrics.tankCapacityL] is null.
 *
 * @param state Current recording UI state.
 */
@Composable
private fun FuelTankRow(state: RecordingUiState, sizing: RecordSizing) {
    val metrics = state.metrics
    if (metrics.tankCapacityL == null) return

    Spacer(Modifier.height(4.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Remaining fuel tile
        metrics.remainingFuelL?.let { remaining ->
            SmallMetricTile(
                label = stringResource(R.string.label_fuel_remaining),
                value = String.format(Locale.US, "%.1f", remaining),
                unit = stringResource(R.string.unit_fuel_short),
                valueFontSp = sizing.bigNumberFontSp,
                modifier = Modifier.weight(1f),
            )
        }
        // Remaining range tile
        metrics.remainingRangeKm?.let { range ->
            SmallMetricTile(
                label = stringResource(R.string.label_fuel_range),
                value = String.format(Locale.US, "%.0f", range),
                unit = stringResource(R.string.unit_km),
                valueFontSp = sizing.bigNumberFontSp,
                modifier = Modifier.weight(1f),
            )
        }
        // Running fuel cost tile — shown only when a price per litre is configured
        state.fuelPricePerL?.let { pricePerL ->
            val cost = FuelCostCalculator.cost(metrics.fuelL, pricePerL)
            SmallMetricTile(
                label = stringResource(R.string.label_fuel_cost),
                value = "%.2f".format(cost),
                unit = state.currency,
                valueFontSp = sizing.bigNumberFontSp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DistanceAltitudeFuelRow(state: RecordingUiState, sizing: RecordSizing) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SmallMetricTile(
            label = stringResource(R.string.tile_distance),
            value = String.format(Locale.US, "%.1f", state.metrics.distanceKm),
            unit = stringResource(R.string.unit_km),
            valueFontSp = sizing.bigNumberFontSp,
            modifier = Modifier.weight(1f),
        )
        SmallMetricTile(
            label = stringResource(R.string.tile_altitude),
            value = String.format(Locale.US, "%.0f", state.liveAltitudeM ?: state.metrics.altitudeM),
            unit = stringResource(R.string.unit_meters_asl),
            valueFontSp = sizing.bigNumberFontSp,
            modifier = Modifier.weight(1f),
        )
        SmallMetricTile(
            label = stringResource(R.string.tile_fuel),
            value = String.format(Locale.US, "%.1f", state.metrics.fuelL),
            unit = stringResource(R.string.unit_fuel_short),
            valueFontSp = sizing.bigNumberFontSp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LeanRow(state: RecordingUiState, currentLeanDeg: Double) {
    LeanTiltBar(
        currentLeanDeg = currentLeanDeg,
        maxLeanLeftDeg = state.metrics.maxLeanLeftDeg,
        maxLeanRightDeg = state.metrics.maxLeanRightDeg,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Analog compass dial rendered adjacent to the speedometer.
 *
 * Draws a compass rose inside a [MetricTile]: an outer ring, tick marks for all
 * eight cardinal directions (N is longer and red), and a two-colour needle.  The
 * rose (ring, tick marks, and N/E/S/W labels) is **fixed** — N is always at the
 * top of the dial.  Only the red needle rotates to point at the current heading.
 * Below the rose the numeric heading and the localised [CompassMath.Cardinal]
 * abbreviation are shown.
 *
 * Heading comes from GPS bearing ([RecordingMetrics.headingDeg]) and only updates
 * while moving — correct rendering on a live ride is verified on-device (🔬).
 *
 * @param headingDeg Raw GPS bearing in degrees; may be any float (normalised internally).
 * @param modifier   Standard Compose modifier applied to the enclosing [MetricTile].
 */
@Composable
private fun CompassDial(
    headingDeg: Float,
    compassDiameterDp: Int,
    bigNumberFontSp: Int,
    modifier: Modifier = Modifier,
) {
    val normalised = CompassMath.normalizeHeading(headingDeg)
    val cardinal = CompassMath.cardinal(headingDeg)

    val cardinalRes = when (cardinal) {
        CompassMath.Cardinal.N  -> R.string.compass_n
        CompassMath.Cardinal.NE -> R.string.compass_ne
        CompassMath.Cardinal.E  -> R.string.compass_e
        CompassMath.Cardinal.SE -> R.string.compass_se
        CompassMath.Cardinal.S  -> R.string.compass_s
        CompassMath.Cardinal.SW -> R.string.compass_sw
        CompassMath.Cardinal.W  -> R.string.compass_w
        CompassMath.Cardinal.NW -> R.string.compass_nw
    }
    val cardinalLabel = stringResource(cardinalRes)

    val compassNLabel = stringResource(R.string.compass_n)
    val compassELabel = stringResource(R.string.compass_e)
    val compassSLabel = stringResource(R.string.compass_s)
    val compassWLabel = stringResource(R.string.compass_w)

    val accent = MotoTracker.colors.accent
    val ringColor = Color.White.copy(alpha = 0.25f)
    val tickDim = Color.White.copy(alpha = 0.55f)

    MetricTile(modifier = modifier) {
        Text(
            text = stringResource(R.string.label_compass),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        Box(
            Modifier.size(compassDiameterDp.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Inner Box holds the fixed rose + N/E/S/W labels — no rotation applied.
            Box(Modifier.size((compassDiameterDp - 4).dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val r = size.minDimension / 2 - 2.dp.toPx()

                    // Outer ring
                    drawCircle(
                        color = ringColor,
                        radius = r,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )

                    // 8 cardinal tick marks — N is longer/red, others are shorter/dim
                    val tickAngles = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
                    for (i in tickAngles.indices) {
                        val angleRad = Math.toRadians(tickAngles[i].toDouble())
                        val isNorth = i == 0
                        val outerR = r
                        val innerR = if (isNorth) r * 0.58f else r * 0.76f
                        val tickColor = if (isNorth) Color.Red else tickDim
                        val tickWidth = if (isNorth) 3.dp.toPx() else 1.5.dp.toPx()
                        // Canvas: up = -y, so x = sin(θ), y = -cos(θ) for bearing θ from N
                        val sx = (sin(angleRad) * innerR).toFloat()
                        val sy = (-cos(angleRad) * innerR).toFloat()
                        val ex = (sin(angleRad) * outerR).toFloat()
                        val ey = (-cos(angleRad) * outerR).toFloat()
                        drawLine(
                            color = tickColor,
                            start = Offset(cx + sx, cy + sy),
                            end   = Offset(cx + ex, cy + ey),
                            strokeWidth = tickWidth,
                            cap = StrokeCap.Round,
                        )
                    }

                    // Needle — red tip points at heading; grey tail points opposite.
                    val (tipDx, tipDy) = CompassMath.needleEndpoint(normalised, r * 0.54f)
                    val (tailDx, tailDy) = CompassMath.needleEndpoint(normalised, -r * 0.38f)
                    drawLine(
                        color = Color.Red,
                        start = Offset(cx, cy),
                        end   = Offset(cx + tipDx, cy + tipDy),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(cx, cy),
                        end   = Offset(cx + tailDx, cy + tailDy),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    // Centre dot
                    drawCircle(color = accent, radius = 3.dp.toPx())
                }
                // N/E/S/W cardinal text labels at the 4 compass points — fixed, always upright.
                Text(
                    text = compassNLabel,
                    style = MotoTracker.typography.label,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                Text(
                    text = compassELabel,
                    style = MotoTracker.typography.label,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
                Text(
                    text = compassSLabel,
                    style = MotoTracker.typography.label,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                Text(
                    text = compassWLabel,
                    style = MotoTracker.typography.label,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
        Text(
            text = String.format(Locale.US, "%.0f°", normalised),
            style = MotoTracker.typography.bigCardNumber.copy(fontSize = bigNumberFontSp.sp),
            color = MotoTracker.colors.text,
        )
        Text(
            text = cardinalLabel,
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tile containers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetricTile(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MotoTracker.colors.panel,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SmallMetricTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueFontSp: Int = 30,
) {
    MetricTile(modifier = modifier) {
        Text(
            text = label,
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        Text(
            text = value,
            style = MotoTracker.typography.bigCardNumber.copy(fontSize = valueFontSp.sp),
            color = MotoTracker.colors.text,
        )
        Text(
            text = unit,
            style = MotoTracker.typography.bodySmall,
            color = MotoTracker.colors.dim,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps a [FuelRangeColor] band to the corresponding Compose [Color] for the fuel icon tint.
 *
 * GREEN → accent (theme primary), YELLOW → accent2 (theme warning hue),
 * RED → [FuelDangerRed] (fixed across themes), UNKNOWN → dim (neutral grey).
 */
@Composable
private fun fuelRangeTint(color: FuelRangeColor): androidx.compose.ui.graphics.Color = when (color) {
    FuelRangeColor.GREEN   -> MotoTracker.colors.accent
    FuelRangeColor.YELLOW  -> MotoTracker.colors.accent2
    FuelRangeColor.RED     -> FuelDangerRed
    FuelRangeColor.UNKNOWN -> MotoTracker.colors.dim
}

/**
 * Compact icon-button control strip driven by [RecordingControls.forPhase].
 *
 * Idle → [START]. Recording → [PAUSE, STOP, FILL_TO_FULL]. Paused → [RESUME, STOP, FILL_TO_FULL].
 * FILL_TO_FULL is always shown in Recording and Paused (unconditional — H2). Each icon carries a
 * contentDescription string resource for a11y and Compose-UI test discovery.
 *
 * The fuel icon (FILL_TO_FULL) is tinted by remaining fuel via [FuelRangeIndicator] (H3).
 *
 * Button size is driven by [sizing.controlButtonDp] so the control strip scales with the
 * available screen height (K3).
 *
 * @param phase   Current recording phase from [RecordingUiState].
 * @param metrics Live recording metrics used to derive the fuel-icon tint colour.
 * @param sizing  Responsive layout tokens for the current screen height breakpoint.
 * @param onEvent Callback for dispatching [RecordingEvent]s to the ViewModel.
 */
@Composable
private fun RecordingControlRow(
    phase: RecordingPhase,
    metrics: RecordingMetrics,
    sizing: RecordSizing,
    onEvent: (RecordingEvent) -> Unit,
) {
    val controls = RecordingControls.forPhase(phase)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // X2: Group roster icon always visible in the control row.
        OutlinedIconButton(
            onClick = { onEvent(RecordingEvent.ShowGroupRoster) },
            modifier = Modifier.size(sizing.controlButtonDp.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.People,
                contentDescription = stringResource(R.string.label_group_roster),
            )
        }
        Spacer(Modifier.width(16.dp))

        controls.forEachIndexed { index, control ->
            if (index > 0) Spacer(Modifier.width(16.dp))
            when (control) {
                RecordingControl.START -> FilledIconButton(
                    onClick = { onEvent(RecordingEvent.Start) },
                    modifier = Modifier.size(sizing.controlButtonDp.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MotoTracker.colors.accent,
                        contentColor = MotoTracker.colors.onAccent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.btn_start_ride),
                        modifier = Modifier.size((sizing.controlButtonDp * 0.6f).roundToInt().dp),
                    )
                }
                RecordingControl.PAUSE -> OutlinedIconButton(
                    onClick = { onEvent(RecordingEvent.Pause) },
                    modifier = Modifier.size(sizing.controlButtonDp.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.btn_pause),
                    )
                }
                RecordingControl.RESUME -> FilledIconButton(
                    onClick = { onEvent(RecordingEvent.Resume) },
                    modifier = Modifier.size(sizing.controlButtonDp.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MotoTracker.colors.accent,
                        contentColor = MotoTracker.colors.onAccent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.btn_resume),
                    )
                }
                RecordingControl.STOP -> FilledIconButton(
                    onClick = { onEvent(RecordingEvent.RequestStop) },
                    modifier = Modifier.size(sizing.controlButtonDp.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MotoTracker.colors.accent2,
                        contentColor = MotoTracker.colors.onAccent2,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.btn_finish),
                    )
                }
                RecordingControl.FILL_TO_FULL -> {
                    val fraction = metrics.tankCapacityL
                        ?.takeIf { it > 0.0 }
                        ?.let { cap -> metrics.remainingFuelL?.let { it / cap } }
                    val fuelIconTint = fuelRangeTint(
                        FuelRangeIndicator.colorFor(fraction, metrics.remainingRangeKm)
                    )
                    OutlinedIconButton(
                        onClick = { onEvent(RecordingEvent.ShowRefuelDialog) },
                        modifier = Modifier.size(sizing.controlButtonDp.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalGasStation,
                            contentDescription = stringResource(R.string.action_fill_to_full),
                            tint = fuelIconTint,
                        )
                    }
                    // R1: Fuel-correction button shown alongside the refuel action.
                    Spacer(Modifier.width(8.dp))
                    OutlinedIconButton(
                        onClick = { onEvent(RecordingEvent.ShowFuelCorrectionDialog) },
                        modifier = Modifier.size(sizing.controlButtonDp.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.action_fuel_correction),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Resume-session dialog (B20)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Modal dialog shown when an interrupted recording session is detected on startup.
 *
 * Offers two actions: resume the session (restoring all accumulated metrics and the
 * GPS track) or discard it (clear the stored snapshot and start fresh).
 *
 * @param onResume  Called when the user taps the Resume button.
 * @param onDiscard Called when the user taps the Discard button.
 */
@Composable
private fun ResumeSessionDialog(
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* non-dismissable — user must choose */ },
        title = { Text(stringResource(R.string.record_resume_session_title)) },
        text = { Text(stringResource(R.string.record_resume_session_body)) },
        confirmButton = {
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MotoTracker.colors.accent,
                    contentColor = MotoTracker.colors.onAccent,
                ),
            ) {
                Text(stringResource(R.string.btn_session_resume))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.btn_session_discard))
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Refuel input dialog (G5)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * AlertDialog for logging a refuel event during an active ride.
 *
 * Pre-fills [initialLitres] (from the bike's tank capacity) and [initialPricePerL] (from the bike
 * default). Both fields are editable; the dialog validates that litres is a positive number before
 * calling [onConfirm]. On-device rendering is 🔬.
 *
 * @param initialLitres     Pre-filled litres field value (bike tank capacity or 0).
 * @param initialPricePerL  Pre-filled price/L field value; null shows an empty field.
 * @param onConfirm         Called with the confirmed litres and price/L values.
 * @param onDismiss         Called when the user cancels or dismisses without confirming.
 */
@Composable
private fun RefuelDialog(
    initialLitres: Double,
    initialPricePerL: Double?,
    onConfirm: (litres: Double, pricePerL: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var litresText by remember { mutableStateOf(if (initialLitres > 0.0) "%.1f".format(initialLitres) else "") }
    var priceText by remember { mutableStateOf(initialPricePerL?.let { "%.2f".format(it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_refuel_title),
                style = MotoTracker.typography.body,
                color = MotoTracker.colors.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = litresText,
                    onValueChange = { litresText = it },
                    label = { Text(stringResource(R.string.label_refuel_litres)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.label_refuel_price_per_l)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val litres = litresText.toDoubleOrNull() ?: return@TextButton
                    val price = priceText.toDoubleOrNull() ?: 0.0
                    if (litres > 0.0) onConfirm(litres, price)
                },
            ) {
                Text(
                    text = stringResource(R.string.dialog_refuel_confirm),
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

// ─────────────────────────────────────────────────────────────────────────────
// Stop-confirmation dialog (J4)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Modal dialog shown when the rider taps Stop during an active recording session.
 *
 * Prevents accidental ride termination by requiring explicit confirmation before
 * [RecordingEvent.ConfirmStop] is dispatched. Dismissing via button or back gesture
 * dispatches [RecordingEvent.DismissStopDialog] so recording continues unchanged.
 * On-device rendering is 🔬.
 *
 * @param onConfirm Called when the user taps the confirm button; triggers `ConfirmStop`.
 * @param onDismiss Called when the user cancels or dismisses; triggers `DismissStopDialog`.
 */
@Composable
private fun StopConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_stop_ride_title)) },
        text = null,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MotoTracker.colors.accent2,
                    contentColor = MotoTracker.colors.onAccent2,
                ),
            ) {
                Text(stringResource(R.string.dialog_stop_ride_confirm))
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

/**
 * Battery-optimization exemption prompt dialog (O1).
 *
 * Shown before the first screen-off recording when the app is not yet exempt.
 * "Allow" fires the system exemption intent (🔬); "Not now" persists the dismissed flag.
 */
@Composable
private fun BatteryOptPromptDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_opt_dialog_title)) },
        text = { Text(stringResource(R.string.battery_opt_dialog_body)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MotoTracker.colors.accent2,
                    contentColor = MotoTracker.colors.onAccent2,
                ),
            ) {
                Text(stringResource(R.string.battery_opt_action_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.battery_opt_action_not_now),
                    color = MotoTracker.colors.dim,
                )
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Fuel-level correction dialog (R1)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dialog for manually re-anchoring the remaining-fuel estimate (R1).
 *
 * Offers two modes:
 * - **Set** ([FuelAdjustmentMode.SET_ABSOLUTE]) — enter the exact remaining litres.
 * - **Delta** ([FuelAdjustmentMode.DELTA]) — enter a signed correction (positive = add,
 *   negative = remove). Useful when a known amount was lost off-ride.
 *
 * The [currentRemainingL] is shown as context so the rider knows the engine's current
 * estimate before typing a correction. On-device rendering is 🔬.
 *
 * @param currentRemainingL  Engine's current remaining-fuel estimate in litres.
 * @param onConfirm          Called with the chosen [FuelAdjustmentMode] and value in litres.
 * @param onDismiss          Called when the user cancels or dismisses without confirming.
 */
@Composable
private fun FuelCorrectionDialog(
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
                // Mode toggle — SET_ABSOLUTE | DELTA
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
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
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
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
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

// ─────────────────────────────────────────────────────────────────────────────
// Service helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Walks the [ContextWrapper] chain to find the host [Activity], or null if not found. */
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun startRecordingService(context: Context, routeId: String?) {
    val intent = Intent(context, RecordingService::class.java).apply {
        if (routeId != null) putExtra(RecordingService.EXTRA_ROUTE_ID, routeId)
    }
    context.startForegroundService(intent)
}

private fun stopRecordingService(context: Context) {
    context.stopService(Intent(context, RecordingService::class.java))
}

// ─────────────────────────────────────────────────────────────────────────────
// Group roster sheet (X2)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Modal bottom sheet listing riders currently in BLE range (last seen within ~30 s).
 *
 * Each row shows the rider's nick and bike name alongside an add/remove-from-group Switch.
 * Usable while Idle, Recording, or Paused — the sheet does not interrupt recording.
 *
 * @param riders         In-range riders, newest-first.
 * @param onToggleGroup  Called when the user changes a rider's group membership.
 * @param onDismiss      Called when the sheet should be dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupRosterSheet(
    riders: List<Rider>,
    onToggleGroup: (shortId: String, inGroup: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MotoTracker.colors.panel,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MotoTracker.colors.dim.copy(alpha = 0.3f))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = stringResource(R.string.label_riders_in_range).uppercase(),
                style = MotoTracker.typography.label,
                color = MotoTracker.colors.dim,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(12.dp))
            if (riders.isEmpty()) {
                Text(
                    text = stringResource(R.string.label_no_riders_in_range),
                    style = MotoTracker.typography.body,
                    color = MotoTracker.colors.dim,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                riders.forEachIndexed { index, rider ->
                    if (index > 0) HorizontalDivider(color = MotoTracker.colors.line.copy(alpha = 0.4f))
                    RiderRosterRow(
                        rider = rider,
                        onToggle = { inGroup -> onToggleGroup(rider.shortId, inGroup) },
                    )
                }
            }
        }
    }
}

/**
 * Single row in the group-roster sheet: rider nick + bike alongside an inGroup Switch.
 *
 * @param rider    Rider to display.
 * @param onToggle Called with the new [Rider.inGroup] value when the switch is toggled.
 */
@Composable
private fun RiderRosterRow(rider: Rider, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rider.nick.ifEmpty { rider.shortId },
                style = MotoTracker.typography.body,
                fontWeight = FontWeight.SemiBold,
                color = MotoTracker.colors.text,
            )
            if (rider.bike.isNotEmpty()) {
                Text(
                    text = rider.bike,
                    style = MotoTracker.typography.bodySmall,
                    color = MotoTracker.colors.dim,
                )
            }
        }
        Switch(
            checked = rider.inGroup,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MotoTracker.colors.onAccent,
                checkedTrackColor = MotoTracker.colors.accent,
            ),
        )
    }
}
