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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.domain.fuel.FuelCostCalculator
import com.mototracker.domain.recording.RecordingControl
import com.mototracker.domain.recording.RecordingControls
import com.mototracker.service.RecordingService
import com.mototracker.ui.permissions.AppFeaturePermission
import com.mototracker.ui.permissions.PermissionDeniedBanner
import com.mototracker.ui.permissions.rememberFeaturePermission
import com.mototracker.ui.theme.MotoTracker
import java.util.Locale
import kotlin.math.cos
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
    // F2: Resolve display heading — GPS bearing during Recording, magnetometer otherwise.
    val displayHeadingDeg = if (state.phase == RecordingPhase.Recording) {
        state.metrics.headingDeg
    } else {
        state.liveHeadingDeg ?: state.metrics.headingDeg
    }
    // F2: Live lean — always from sensor; falls back to last-recorded value.
    val displayLeanDeg = state.liveLeanDeg ?: state.metrics.currentLeanDeg

    // B20: Resume-or-discard prompt when an interrupted session is detected on startup.
    if (state.resumableSession != null) {
        ResumeSessionDialog(
            onResume = { onEvent(RecordingEvent.ResumeSession) },
            onDiscard = { onEvent(RecordingEvent.DiscardSession) },
        )
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── Tiles — fills remaining space; scrollable fallback so nothing clips ─
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .background(MotoTracker.colors.bg)
                    .padding(horizontal = 8.dp),
            ) {
                // ── Compact chip row: GPS sat-count · weather · wind rose ──────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GpsChip(satCount = state.gpsSatCount, onRoad = state.gpsOnRoad)
                    Spacer(Modifier.weight(1f))
                    WeatherChip(weather = state.weather)
                    Spacer(Modifier.width(8.dp))
                    WindRose(headingDeg = displayHeadingDeg)
                }
                Spacer(Modifier.height(8.dp))
                SpeedAndTimeRow(state = state, headingDeg = displayHeadingDeg)
                Spacer(Modifier.height(6.dp))
                DistanceAltitudeFuelRow(state)
                FuelTankRow(state = state)
                Spacer(Modifier.height(6.dp))
                LeanRow(state = state, currentLeanDeg = displayLeanDeg)
                Spacer(Modifier.height(6.dp))
                TimersRow(state)
                Spacer(Modifier.height(12.dp))
                if (locationPermDenied && state.phase == RecordingPhase.Idle) {
                    PermissionDeniedBanner(
                        text = stringResource(R.string.perm_location_denied),
                        onRetry = { onEvent(RecordingEvent.Start) },
                    )
                } else {
                    RecordingControlRow(
                        phase = state.phase,
                        hasFuelTank = state.metrics.tankCapacityL != null,
                        onEvent = onEvent,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.hint_pause_handlebar),
                    style = MotoTracker.typography.bodySmall,
                    color = MotoTracker.colors.dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
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
 * Top-left chip showing GPS satellite count and optional road-correction indicator.
 *
 * @param satCount Number of GPS satellites in use.
 * @param onRoad   Whether GPS-to-road correction is active.
 */
@Composable
private fun GpsChip(satCount: Int, onRoad: Boolean, modifier: Modifier = Modifier) {
    val satAbbr = stringResource(R.string.label_sat_abbr)
    val text = buildString {
        append("GPS · ")
        append(satCount)
        append(" ")
        append(satAbbr)
        if (onRoad) append(" · ").append(stringResource(R.string.label_on_road))
    }
    InfoChip(text = text, modifier = modifier)
}

/**
 * Top-right chip showing current weather conditions, or "offline" when unavailable.
 *
 * @param weather Current weather snapshot, or null when offline.
 */
@Composable
private fun WeatherChip(weather: WeatherInfo?, modifier: Modifier = Modifier) {
    val text = if (weather == null) {
        stringResource(R.string.label_wx_offline)
    } else {
        val rain = if (weather.rain) stringResource(R.string.label_wx_rain_yes)
                   else stringResource(R.string.label_wx_rain_no)
        "${weather.tempC}°C · ${weather.humPct}% · $rain"
    }
    InfoChip(text = text, modifier = modifier)
}

@Composable
private fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MotoTracker.colors.panel.copy(alpha = 0.85f),
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Wind rose
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Small wind-rose / heading indicator rotated to reflect the current GPS bearing.
 *
 * @param headingDeg GPS bearing in degrees (0–360, 0 = North).
 */
@Composable
private fun WindRose(headingDeg: Float, modifier: Modifier = Modifier) {
    val accent = MotoTracker.colors.accent
    val dim = MotoTracker.colors.dim

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MotoTracker.colors.panel.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(36.dp)
                .rotate(headingDeg),
        ) {
            val cx = size.width / 2
            val cy = size.height / 2
            val r = size.minDimension / 2

            // North arrow (accent colour)
            drawLine(
                color = accent,
                start = Offset(cx, cy),
                end = Offset(cx, cy - r),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            // South arrow (dim colour)
            drawLine(
                color = dim,
                start = Offset(cx, cy),
                end = Offset(cx, cy + r * 0.7f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Metric tiles
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpeedAndTimeRow(state: RecordingUiState, headingDeg: Float) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SpeedTile(speedKmh = state.metrics.currentSpeedKmh, modifier = Modifier.weight(1f).fillMaxHeight())
        CompassDial(headingDeg = headingDeg, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun SpeedTile(speedKmh: Double, modifier: Modifier = Modifier) {
    MetricTile(modifier = modifier) {
        Text(
            text = stringResource(R.string.tile_speed),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        Text(
            text = String.format(Locale.US, "%.0f", speedKmh),
            style = MotoTracker.typography.recordSpeed,
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
private fun TimersRow(state: RecordingUiState) {
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
                style = MotoTracker.typography.bigCardNumber,
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
                style = MotoTracker.typography.bigCardNumber,
                color = MotoTracker.colors.text,
            )
        }
    }
}

/**
 * Fuel-tank readout row shown when the current bike has a tank capacity configured.
 *
 * Displays remaining fuel, remaining range, running fuel cost (when [RecordingUiState.fuelPricePerL]
 * is set), and an optional low-fuel warning. The fill-to-full action has moved to the control strip
 * (G2); this composable is a pure readout with no interactive elements.
 *
 * Hidden entirely when [RecordingUiState.metrics.tankCapacityL] is null.
 *
 * @param state Current recording UI state.
 */
@Composable
private fun FuelTankRow(state: RecordingUiState) {
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
                modifier = Modifier.weight(1f),
            )
        }
        // Remaining range tile
        metrics.remainingRangeKm?.let { range ->
            SmallMetricTile(
                label = stringResource(R.string.label_fuel_range),
                value = String.format(Locale.US, "%.0f", range),
                unit = stringResource(R.string.unit_km),
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
                modifier = Modifier.weight(1f),
            )
        }
    }
    // Low-fuel warning
    if (metrics.lowFuel) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.warn_low_fuel),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.accent2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DistanceAltitudeFuelRow(state: RecordingUiState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SmallMetricTile(
            label = stringResource(R.string.tile_distance),
            value = String.format(Locale.US, "%.1f", state.metrics.distanceKm),
            unit = stringResource(R.string.unit_km),
            modifier = Modifier.weight(1f),
        )
        SmallMetricTile(
            label = stringResource(R.string.tile_altitude),
            value = String.format(Locale.US, "%.0f", state.metrics.altitudeM),
            unit = stringResource(R.string.unit_meters_asl),
            modifier = Modifier.weight(1f),
        )
        SmallMetricTile(
            label = stringResource(R.string.tile_fuel),
            value = String.format(Locale.US, "%.1f", state.metrics.fuelL),
            unit = stringResource(R.string.unit_fuel_short),
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
 * whole rose is rotated by the normalised heading so the needle points in the
 * direction of travel.  Below the rose the numeric heading and the localised
 * [CompassMath.Cardinal] abbreviation are shown.
 *
 * Heading comes from GPS bearing ([RecordingMetrics.headingDeg]) and only updates
 * while moving — correct rotation on a live ride is verified on-device (🔬).
 *
 * @param headingDeg Raw GPS bearing in degrees; may be any float (normalised internally).
 * @param modifier   Standard Compose modifier applied to the enclosing [MetricTile].
 */
@Composable
private fun CompassDial(headingDeg: Float, modifier: Modifier = Modifier) {
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
            Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Inner Box rotates with the heading so the rose + N/E/S/W labels turn together.
            Box(Modifier.size(68.dp).rotate(normalised)) {
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

                    // Needle — red tip (north / direction of travel) + grey tail
                    drawLine(
                        color = Color.Red,
                        start = Offset(cx, cy),
                        end   = Offset(cx, cy - r * 0.54f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(cx, cy),
                        end   = Offset(cx, cy + r * 0.38f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    // Centre dot
                    drawCircle(color = accent, radius = 3.dp.toPx())
                }
                // N/E/S/W cardinal text labels at the 4 compass points — rotate with the rose.
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
            style = MotoTracker.typography.bigCardNumber,
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
) {
    MetricTile(modifier = modifier) {
        Text(
            text = label,
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        Text(
            text = value,
            style = MotoTracker.typography.bigCardNumber,
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
 * Compact icon-button control strip driven by [RecordingControls.forPhase].
 *
 * The visible controls depend on both [phase] and whether the current bike has a fuel tank
 * ([hasFuelTank]): the fill-to-full button only appears during Recording and Paused when a tank
 * is configured; it is never shown in Idle. Each icon carries a contentDescription string
 * resource for a11y and Compose-UI test discovery.
 *
 * @param phase       Current recording phase from [RecordingUiState].
 * @param hasFuelTank True when the current bike has a tank capacity configured.
 * @param onEvent     Callback for dispatching [RecordingEvent]s to the ViewModel.
 */
@Composable
private fun RecordingControlRow(
    phase: RecordingPhase,
    hasFuelTank: Boolean,
    onEvent: (RecordingEvent) -> Unit,
) {
    val controls = RecordingControls.forPhase(phase, hasFuelTank)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        controls.forEachIndexed { index, control ->
            if (index > 0) Spacer(Modifier.width(16.dp))
            when (control) {
                RecordingControl.START -> FilledIconButton(
                    onClick = { onEvent(RecordingEvent.Start) },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MotoTracker.colors.accent,
                        contentColor = MotoTracker.colors.onAccent,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.btn_start_ride),
                        modifier = Modifier.size(32.dp),
                    )
                }
                RecordingControl.PAUSE -> OutlinedIconButton(
                    onClick = { onEvent(RecordingEvent.Pause) },
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.btn_pause),
                    )
                }
                RecordingControl.RESUME -> FilledIconButton(
                    onClick = { onEvent(RecordingEvent.Resume) },
                    modifier = Modifier.size(52.dp),
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
                    onClick = { onEvent(RecordingEvent.Finish) },
                    modifier = Modifier.size(52.dp),
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
                RecordingControl.FILL_TO_FULL -> OutlinedIconButton(
                    onClick = { onEvent(RecordingEvent.FillToFull) },
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalGasStation,
                        contentDescription = stringResource(R.string.action_fill_to_full),
                        tint = MotoTracker.colors.accent,
                    )
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
