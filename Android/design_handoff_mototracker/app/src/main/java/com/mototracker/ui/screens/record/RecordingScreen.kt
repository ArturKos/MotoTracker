package com.mototracker.ui.screens.record

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.mototracker.service.RecordingService
import com.mototracker.ui.permissions.AppFeaturePermission
import com.mototracker.ui.permissions.PermissionDeniedBanner
import com.mototracker.ui.permissions.rememberFeaturePermission
import com.mototracker.ui.theme.MotoTracker
import java.util.Locale
import kotlin.math.abs

/**
 * Recording screen — the app's primary screen while a ride is active.
 *
 * Composable is a pure state renderer; all business logic lives in [RecordingViewModel].
 * The map tile, GPS updates, and sensor readings are on-device only (🔬).
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

    // Shared permission layer: gate Start on LOCATION; request NOTIFICATIONS alongside (best-effort).
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

    // Collect one-shot effects (save toast, navigate to detail)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecordingEffect.Saved -> {
                    val msg = if (effect.offline) context.getString(R.string.toast_saved_local)
                              else context.getString(R.string.toast_ride_saved)
                    snackbarHostState.showSnackbar(msg)
                }
                is RecordingEffect.NavigateToDetail -> {
                    // B4 will wire navigation to the route detail screen
                }
            }
        }
    }

    // Start / stop the foreground location service as the recording phase changes
    LaunchedEffect(state.phase) {
        when (state.phase) {
            RecordingPhase.Recording -> startRecordingService(context)
            RecordingPhase.Idle -> stopRecordingService(context)
            RecordingPhase.Paused -> Unit
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── Map area ─────────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MotoTracker.colors.panel),
            ) {
                RecordingMapPlaceholder(state, Modifier.fillMaxSize())

                GpsChip(
                    satCount = state.gpsSatCount,
                    onRoad = state.gpsOnRoad,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )

                WeatherChip(
                    weather = state.weather,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )

                WindRose(
                    headingDeg = state.metrics.headingDeg,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                )
            }

            // ── Tiles ─────────────────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MotoTracker.colors.bg)
                    .padding(horizontal = 8.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                SpeedAndTimeRow(state)
                Spacer(Modifier.height(6.dp))
                DistanceAltitudeFuelRow(state)
                Spacer(Modifier.height(6.dp))
                LeanCompassRow(state)
                Spacer(Modifier.height(12.dp))
                if (locationPerm.denied && state.phase == RecordingPhase.Idle) {
                    PermissionDeniedBanner(
                        text = stringResource(R.string.perm_location_denied),
                        onRetry = {
                            locationPerm.requestThen { viewModel.onEvent(RecordingEvent.Start) }
                        },
                    )
                } else {
                    RecordingControlRow(phase = state.phase, onEvent = ::dispatchEvent)
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
// Map placeholder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Schematic map stand-in with a growing track line and a pulsing position dot.
 * Replaced by an actual map tile in on-device testing (🔬).
 */
@Composable
private fun RecordingMapPlaceholder(state: RecordingUiState, modifier: Modifier = Modifier) {
    val accentColor = MotoTracker.colors.accent
    val dimColor = MotoTracker.colors.dim
    val panel2 = MotoTracker.colors.panel2

    Canvas(modifier.fillMaxSize()) {
        // Grid lines
        val step = size.width / 6
        for (i in 1..5) {
            drawLine(
                color = dimColor.copy(alpha = 0.2f),
                start = Offset(i * step, 0f),
                end = Offset(i * step, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }
        val hStep = size.height / 4
        for (i in 1..3) {
            drawLine(
                color = dimColor.copy(alpha = 0.2f),
                start = Offset(0f, i * hStep),
                end = Offset(size.width, i * hStep),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Simulated track path (simplified sine-wave for placeholder)
        if (state.phase != RecordingPhase.Idle) {
            val path = Path()
            val w = size.width
            val cy = size.height * 0.55f
            path.moveTo(w * 0.1f, cy)
            path.cubicTo(
                w * 0.3f, cy - size.height * 0.2f,
                w * 0.5f, cy + size.height * 0.15f,
                w * 0.7f, cy - size.height * 0.1f,
            )
            drawPath(
                path = path,
                color = accentColor.copy(alpha = 0.7f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            // Position pulse dot
            drawCircle(
                color = accentColor,
                radius = 8.dp.toPx(),
                center = Offset(w * 0.7f, cy - size.height * 0.1f),
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.3f),
                radius = 16.dp.toPx(),
                center = Offset(w * 0.7f, cy - size.height * 0.1f),
            )
        }

        // Center cross (compass ref)
        drawLine(
            color = panel2,
            start = Offset(size.width / 2 - 10.dp.toPx(), size.height / 2),
            end = Offset(size.width / 2 + 10.dp.toPx(), size.height / 2),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = panel2,
            start = Offset(size.width / 2, size.height / 2 - 10.dp.toPx()),
            end = Offset(size.width / 2, size.height / 2 + 10.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
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
private fun SpeedAndTimeRow(state: RecordingUiState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SpeedTile(speedKmh = state.metrics.currentSpeedKmh, modifier = Modifier.weight(1.5f))
        TimeTile(durationSec = state.metrics.durationSec, modifier = Modifier.weight(1f))
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

@Composable
private fun TimeTile(durationSec: Long, modifier: Modifier = Modifier) {
    val h = durationSec / 3600
    val m = (durationSec % 3600) / 60
    val s = durationSec % 60
    val timeText = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)

    MetricTile(modifier = modifier) {
        Text(
            text = stringResource(R.string.tile_ride_time),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        Text(
            text = timeText,
            style = MotoTracker.typography.timer,
            color = MotoTracker.colors.text,
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
private fun LeanCompassRow(state: RecordingUiState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LeanTile(leanDeg = state.metrics.currentLeanDeg, modifier = Modifier.weight(1f))
        CompassTile(headingDeg = state.metrics.headingDeg, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LeanTile(leanDeg: Double, modifier: Modifier = Modifier) {
    MetricTile(modifier = modifier) {
        Text(
            text = stringResource(R.string.tile_lean),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        Box(
            Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Bike silhouette rotated by lean angle
            Canvas(
                Modifier
                    .size(40.dp)
                    .rotate(leanDeg.toFloat()),
            ) {
                val cx = size.width / 2
                val cy = size.height / 2
                drawLine(
                    color = Color.White,
                    start = Offset(cx, cy - size.height * 0.4f),
                    end = Offset(cx, cy + size.height * 0.4f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Text(
            text = String.format(Locale.US, "%.1f°", abs(leanDeg)),
            style = MotoTracker.typography.bigCardNumber,
            color = MotoTracker.colors.text,
        )
    }
}

@Composable
private fun CompassTile(headingDeg: Float, modifier: Modifier = Modifier) {
    MetricTile(modifier = modifier) {
        Text(
            text = stringResource(R.string.label_compass),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
        Box(
            Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier
                    .size(40.dp)
                    .rotate(headingDeg),
            ) {
                val cx = size.width / 2
                val cy = size.height / 2
                val r = size.minDimension / 2 - 2.dp.toPx()
                drawCircle(
                    color = Color.White.copy(alpha = 0.2f),
                    radius = r,
                    style = Stroke(width = 1.dp.toPx()),
                )
                // North pointer
                drawLine(
                    color = Color.Red,
                    start = Offset(cx, cy),
                    end = Offset(cx, cy - r),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Text(
            text = String.format(Locale.US, "%.0f°", headingDeg),
            style = MotoTracker.typography.bigCardNumber,
            color = MotoTracker.colors.text,
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
 * Start / Pause / Resume / Finish control strip; appearance adapts to [phase].
 *
 * @param phase   Current recording phase from [RecordingUiState].
 * @param onEvent Callback for dispatching [RecordingEvent]s to the ViewModel.
 */
@Composable
private fun RecordingControlRow(phase: RecordingPhase, onEvent: (RecordingEvent) -> Unit) {
    when (phase) {
        RecordingPhase.Idle -> {
            Button(
                onClick = { onEvent(RecordingEvent.Start) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MotoTracker.colors.accent,
                    contentColor = MotoTracker.colors.onAccent,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.btn_start_ride),
                    style = MotoTracker.typography.routeTitle,
                )
            }
        }

        RecordingPhase.Recording -> {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onEvent(RecordingEvent.Pause) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MotoTracker.colors.text,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.btn_pause),
                        style = MotoTracker.typography.routeTitle,
                    )
                }
                Button(
                    onClick = { onEvent(RecordingEvent.Finish) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MotoTracker.colors.accent2,
                        contentColor = MotoTracker.colors.onAccent2,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.btn_finish),
                        style = MotoTracker.typography.routeTitle,
                    )
                }
            }
        }

        RecordingPhase.Paused -> {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onEvent(RecordingEvent.Resume) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MotoTracker.colors.accent,
                        contentColor = MotoTracker.colors.onAccent,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.btn_resume),
                        style = MotoTracker.typography.routeTitle,
                    )
                }
                Button(
                    onClick = { onEvent(RecordingEvent.Finish) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MotoTracker.colors.accent2,
                        contentColor = MotoTracker.colors.onAccent2,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.btn_finish),
                        style = MotoTracker.typography.routeTitle,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Service helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun startRecordingService(context: Context) {
    context.startForegroundService(Intent(context, RecordingService::class.java))
}

private fun stopRecordingService(context: Context) {
    context.stopService(Intent(context, RecordingService::class.java))
}
