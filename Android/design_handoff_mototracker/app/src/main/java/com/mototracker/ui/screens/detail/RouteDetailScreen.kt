package com.mototracker.ui.screens.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.core.format.WeatherUi
import com.mototracker.ui.theme.JetBrainsMonoFamily
import com.mototracker.ui.theme.MotoTracker

/**
 * Route Detail screen — shows track thumbnail, stat tiles, weather, speed/elevation charts,
 * meetups, and export/send actions.
 *
 * The screen reads all display data from [RouteDetailViewModel] (via Hilt) which reads the
 * `routeId` from [androidx.lifecycle.SavedStateHandle] injected by Navigation Compose.
 *
 * Map tiles and chart Canvas rendering are on-device-only concerns (🔬).
 *
 * @param modifier   Standard Compose modifier.
 * @param viewModel  Hilt-injected [RouteDetailViewModel].
 * @param onExport   Called when "Export / share" is tapped (B8 owns the sheet).
 * @param onSend     Called when "Send" is tapped (B8 owns the action).
 */
@Composable
fun RouteDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: RouteDetailViewModel = hiltViewModel(),
    onExport: () -> Unit = {},
    onSend: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.loading -> LoadingPane(modifier)
        state.routeNotFound -> NotFoundPane(modifier)
        else -> DetailContent(state = state, modifier = modifier, onExport = onExport, onSend = onSend)
    }
}

// ── Loading / error panes ─────────────────────────────────────────────────────

@Composable
private fun LoadingPane(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MotoTracker.colors.accent)
    }
}

@Composable
private fun NotFoundPane(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.screen_route_detail).uppercase(),
            style = MotoTracker.typography.screenTitle,
            color = MotoTracker.colors.dim,
        )
    }
}

// ── Main content ──────────────────────────────────────────────────────────────

@Composable
private fun DetailContent(
    state: RouteDetailUiState,
    modifier: Modifier = Modifier,
    onExport: () -> Unit,
    onSend: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            TrackPanel(
                pathD = state.thumbnailPathD,
                accent = MotoTracker.colors.accent,
                accent2 = MotoTracker.colors.accent2,
            )
        }

        item {
            Column {
                Text(
                    text = state.name,
                    style = MotoTracker.typography.routeTitle,
                    color = MotoTracker.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.dateDisplay,
                        style = MotoTracker.typography.bodySmall,
                        color = MotoTracker.colors.dim,
                    )
                    if (state.bikeName != "—") {
                        Text(
                            text = " · ${state.bikeName}",
                            style = MotoTracker.typography.bodySmall,
                            color = MotoTracker.colors.dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.bikeSold) {
                            Spacer(Modifier.width(6.dp))
                            SoldChip()
                        }
                    }
                }
            }
        }

        item { StatGrid(state) }

        item { WeatherCard(weather = state.weather) }

        item {
            ChartCard(
                title = stringResource(R.string.chart_speed_title),
                stroke = state.speedStroke,
                fill = state.speedFill,
                accentColor = MotoTracker.colors.accent,
            )
        }

        item {
            ChartCard(
                title = stringResource(R.string.chart_elevation_title),
                stroke = state.elevStroke,
                fill = state.elevFill,
                accentColor = MotoTracker.colors.accent2,
                subtitle = state.elevGainLabel.ifEmpty { null },
            )
        }

        item {
            MeetupsCard(
                meetings = state.meetings,
                meetingsNone = state.meetingsNone,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MotoTracker.colors.accent,
                    ),
                ) {
                    Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_export_share),
                        style = MotoTracker.typography.label,
                    )
                }
                if (state.queued) {
                    Button(
                        onClick = onSend,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MotoTracker.colors.accent,
                            contentColor = MotoTracker.colors.onAccent,
                        ),
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_send),
                            style = MotoTracker.typography.label,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Track panel ───────────────────────────────────────────────────────────────

@Composable
private fun TrackPanel(pathD: String, accent: Color, accent2: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MotoTracker.colors.panel2)
            .border(1.dp, MotoTracker.colors.line, RoundedCornerShape(10.dp)),
    ) {
        if (pathD.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scaleX = size.width / 320f
                val scaleY = size.height / 200f
                val path = Path()
                var firstPt: Offset? = null
                var lastPt: Offset? = null
                try {
                    val tokens = pathD.trim().split(" ")
                    var i = 0
                    while (i < tokens.size) {
                        when (tokens[i]) {
                            "M" -> {
                                val x = tokens[i + 1].toFloat() * scaleX
                                val y = tokens[i + 2].toFloat() * scaleY
                                path.moveTo(x, y); firstPt = Offset(x, y); lastPt = Offset(x, y); i += 3
                            }
                            "L" -> {
                                val x = tokens[i + 1].toFloat() * scaleX
                                val y = tokens[i + 2].toFloat() * scaleY
                                path.lineTo(x, y); lastPt = Offset(x, y); i += 3
                            }
                            else -> i++
                        }
                    }
                } catch (_: Exception) { return@Canvas }
                drawPath(path, color = accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                firstPt?.let { drawCircle(color = accent, radius = 5.dp.toPx(), center = it) }
                lastPt?.let { drawCircle(color = accent2, radius = 5.dp.toPx(), center = it) }
            }
        }
    }
}

// ── Stat grid ─────────────────────────────────────────────────────────────────

@Composable
private fun StatGrid(state: RouteDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(label = stringResource(R.string.tile_distance), tile = state.distanceTile, modifier = Modifier.weight(1f))
            StatTile(label = stringResource(R.string.label_time), tile = state.durationTile, modifier = Modifier.weight(1f))
            StatTile(label = stringResource(R.string.label_avg_speed), tile = state.avgTile, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(label = stringResource(R.string.tag_max), tile = state.maxTile, modifier = Modifier.weight(1f))
            StatTile(label = stringResource(R.string.label_lean_short), tile = state.leanTile, modifier = Modifier.weight(1f))
            StatTile(label = stringResource(R.string.tile_fuel), tile = state.fuelTile, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, tile: StatTileUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MotoTracker.colors.panel)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text = label.uppercase(), style = MotoTracker.typography.label, color = MotoTracker.colors.dim)
        Spacer(Modifier.height(2.dp))
        Text(
            text = tile.value,
            style = MotoTracker.typography.bigCardNumber.copy(fontSize = 20.sp),
            color = MotoTracker.colors.text,
            maxLines = 1,
        )
        Text(text = tile.unit, style = MotoTracker.typography.label, color = MotoTracker.colors.dim)
    }
}

// ── Weather card ──────────────────────────────────────────────────────────────

@Composable
private fun WeatherCard(weather: WeatherUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MotoTracker.colors.panel)
            .padding(12.dp),
    ) {
        Text(text = stringResource(R.string.section_weather).uppercase(), style = MotoTracker.typography.label, color = MotoTracker.colors.dim)
        Spacer(Modifier.height(8.dp))
        if (weather.offline) {
            Text(text = stringResource(R.string.label_wx_offline), style = MotoTracker.typography.body, color = MotoTracker.colors.dim)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WeatherItem(label = stringResource(R.string.label_wx_temp), value = weather.tempDisplay)
                WeatherItem(label = stringResource(R.string.label_wx_hum), value = weather.humDisplay)
                WeatherItem(
                    label = stringResource(R.string.label_wx_rain),
                    value = when (weather.rain) {
                        true  -> stringResource(R.string.label_wx_rain_yes)
                        false -> stringResource(R.string.label_wx_rain_no)
                        null  -> "—"
                    },
                )
            }
        }
    }
}

@Composable
private fun WeatherItem(label: String, value: String) {
    Column {
        Text(text = label.uppercase(), style = MotoTracker.typography.label, color = MotoTracker.colors.dim)
        Text(text = value, style = MotoTracker.typography.body, color = MotoTracker.colors.text)
    }
}

// ── Chart card ────────────────────────────────────────────────────────────────

@Composable
private fun ChartCard(
    title: String,
    stroke: String,
    fill: String,
    accentColor: Color,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MotoTracker.colors.panel)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title.uppercase(), style = MotoTracker.typography.label, color = MotoTracker.colors.dim)
            if (subtitle != null) {
                Text(text = subtitle, style = MotoTracker.typography.label, color = accentColor)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MotoTracker.colors.panel2),
        ) {
            if (stroke.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scaleX = size.width / 320f
                    val scaleY = size.height / 90f
                    if (fill.isNotEmpty()) {
                        val fillPath = buildPolylinePath(fill, scaleX, scaleY)
                        if (fillPath != null) {
                            fillPath.close()
                            drawPath(fillPath, color = accentColor.copy(alpha = 0.20f))
                        }
                    }
                    val strokePath = buildPolylinePath(stroke, scaleX, scaleY)
                    if (strokePath != null) {
                        drawPath(strokePath, color = accentColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

// ── Meetups card ──────────────────────────────────────────────────────────────

@Composable
private fun MeetupsCard(meetings: List<MeetingUi>, meetingsNone: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MotoTracker.colors.panel)
            .padding(12.dp),
    ) {
        Text(text = stringResource(R.string.label_meetings).uppercase(), style = MotoTracker.typography.label, color = MotoTracker.colors.dim)
        Spacer(Modifier.height(8.dp))
        if (meetingsNone) {
            Text(text = stringResource(R.string.label_meetings_none), style = MotoTracker.typography.body, color = MotoTracker.colors.dim)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                meetings.forEach { MeetupRow(it) }
            }
        }
    }
}

@Composable
private fun MeetupRow(meeting: MeetingUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MotoTracker.colors.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = meeting.initials,
                style = MotoTracker.typography.label.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
                color = MotoTracker.colors.accent,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "${meeting.who} · ${meeting.bikeName}",
                style = MotoTracker.typography.body,
                color = MotoTracker.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${stringResource(R.string.label_wave_at)} ${meeting.place} · ${meeting.timeLabel}",
                style = MotoTracker.typography.bodySmall,
                color = MotoTracker.colors.dim,
            )
        }
    }
}

// ── Sold chip ─────────────────────────────────────────────────────────────────

@Composable
private fun SoldChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MotoTracker.colors.panel2)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.tag_sold).uppercase(),
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
        )
    }
}

// ── Path building helpers ─────────────────────────────────────────────────────

/**
 * Parses a polyline `points` string (`"x,y x,y …"`) and builds a scaled [Path].
 *
 * Returns `null` on empty input or any parse error.
 */
private fun buildPolylinePath(points: String, scaleX: Float, scaleY: Float): Path? {
    if (points.isEmpty()) return null
    return try {
        val path = Path()
        var first = true
        points.trim().split(" ").forEach { pair ->
            val ci = pair.indexOf(',')
            val x = pair.substring(0, ci).toFloat() * scaleX
            val y = pair.substring(ci + 1).toFloat() * scaleY
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
        }
        if (first) null else path
    } catch (_: Exception) { null }
}
