package com.mototracker.ui.screens.routes

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.core.format.RouteThumbnail
import com.mototracker.ui.theme.MotoTracker
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Routes list screen — thin ViewModel wrapper that wires the file picker and delegates
 * to [RoutesContent].
 *
 * @param onOpenRoute  Called with the route UUID when the user taps a card.
 * @param modifier     Standard Compose modifier.
 * @param viewModel    Hilt-injected [RoutesViewModel].
 */
@Composable
fun RoutesScreen(
    onOpenRoute: (routeId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RoutesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val importMsg = stringResource(R.string.toast_import)
    val gpxLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { /* GPX parsing deferred to a later task */ }

    RoutesContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onImportGpx = {
            scope.launch { snackbarHostState.showSnackbar(importMsg) }
            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(android.content.Intent.EXTRA_MIME_TYPES, arrayOf("application/gpx+xml", "application/octet-stream"))
            }
            gpxLauncher.launch(intent)
        },
        onOpenRoute = onOpenRoute,
        onSetQuery = viewModel::setQuery,
        onSetBikeFilter = viewModel::setBikeFilter,
        onSetDateRange = viewModel::setDateRange,
        onSetSort = viewModel::setSort,
        onClearFilters = viewModel::clearFilters,
        modifier = modifier,
    )
}

/**
 * Pure renderer for the Routes list screen: summary tiles, search/filter bar, and route cards.
 * Extracted for Paparazzi screenshot testing — no file picker launcher or ViewModel inside.
 *
 * @param state              Pre-computed list UI state.
 * @param snackbarHostState  Snackbar host; defaults to a fresh instance for standalone use.
 * @param onImportGpx        Called when the user taps the GPX import button.
 * @param onOpenRoute        Called with the route UUID when the user taps a card.
 * @param onSetQuery         Called when the search query changes.
 * @param onSetBikeFilter    Called when the bike filter selection changes (`null` = all bikes).
 * @param onSetDateRange     Called with (fromEpochMs, toEpochMs) when the date range changes.
 * @param onSetSort          Called when sort key or direction changes.
 * @param onClearFilters     Called when the user clears all active filters.
 * @param modifier           Standard Compose modifier.
 */
@Composable
fun RoutesContent(
    state: RoutesUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onImportGpx: () -> Unit = {},
    onOpenRoute: (String) -> Unit = {},
    onSetQuery: (String) -> Unit = {},
    onSetBikeFilter: (String?) -> Unit = {},
    onSetDateRange: (Long?, Long?) -> Unit = { _, _ -> },
    onSetSort: (RouteSortKey, SortDirection) -> Unit = { _, _ -> },
    onClearFilters: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        containerColor = MotoTracker.colors.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            SummaryRow(
                routeCount = state.routeCount,
                totalRouteCount = state.totalRouteCount,
                totalKmDisplay = state.totalKmDisplay,
                onImportGpx = onImportGpx,
            )
            Spacer(Modifier.height(12.dp))
            SearchAndFilterBar(
                filter = state.filter,
                availableBikes = state.availableBikes,
                onSetQuery = onSetQuery,
                onSetBikeFilter = onSetBikeFilter,
                onSetDateRange = onSetDateRange,
                onSetSort = onSetSort,
                onClearFilters = onClearFilters,
            )
            Spacer(Modifier.height(8.dp))
            if (state.cards.isEmpty() && state.totalRouteCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.routes_no_matches),
                        style = MotoTracker.typography.bodySmall,
                        color = MotoTracker.colors.dim,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.cards, key = { it.id }) { card ->
                        RouteCard(card = card, onClick = { onOpenRoute(card.id) })
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

// ── Summary row ──────────────────────────────────────────────────────────────

@Composable
private fun SummaryRow(
    routeCount: Int,
    totalRouteCount: Int,
    totalKmDisplay: String,
    onImportGpx: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val countValue = if (totalRouteCount > 0 && routeCount < totalRouteCount) {
            "$routeCount / $totalRouteCount"
        } else {
            routeCount.toString()
        }
        SummaryTile(
            label = stringResource(R.string.label_all_routes),
            value = countValue,
            modifier = Modifier.weight(1f),
        )
        SummaryTile(
            label = stringResource(R.string.label_total_km),
            value = totalKmDisplay,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onImportGpx,
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FileOpen,
                contentDescription = stringResource(R.string.toast_import),
                tint = MotoTracker.colors.accent,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
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
            style = MotoTracker.typography.bigCardNumber,
            color = MotoTracker.colors.text,
            maxLines = 1,
        )
    }
}

// ── Search & filter bar ───────────────────────────────────────────────────────

/**
 * Search field, bike dropdown, sort chips, date-range pickers, and clear-all button.
 *
 * All controls are 🔬 (on-device rendering only); the transformation logic they
 * drive is fully unit-tested in [RoutesViewModelTest].
 */
@Composable
private fun SearchAndFilterBar(
    filter: RoutesFilter,
    availableBikes: List<BikeFilterOption>,
    onSetQuery: (String) -> Unit,
    onSetBikeFilter: (String?) -> Unit,
    onSetDateRange: (Long?, Long?) -> Unit,
    onSetSort: (RouteSortKey, SortDirection) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = onSetQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    stringResource(R.string.routes_search_hint),
                    style = MotoTracker.typography.bodySmall,
                    color = MotoTracker.colors.dim,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MotoTracker.colors.dim,
                )
            },
            trailingIcon = if (filter.query.isNotEmpty()) {
                {
                    IconButton(onClick = { onSetQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            } else null,
            singleLine = true,
        )

        Spacer(Modifier.height(6.dp))

        // Bike dropdown + sort key chips (horizontally scrollable)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BikeDropdown(
                availableBikes = availableBikes,
                selectedBikeId = filter.bikeId,
                onSelect = onSetBikeFilter,
                allBikesLabel = stringResource(R.string.routes_filter_all_bikes),
            )
            val sortKeys = RouteSortKey.values()
            sortKeys.forEach { key ->
                val keyLabel = when (key) {
                    RouteSortKey.DATE -> stringResource(R.string.routes_sort_date)
                    RouteSortKey.DISTANCE -> stringResource(R.string.routes_sort_distance)
                    RouteSortKey.DURATION -> stringResource(R.string.routes_sort_duration)
                    RouteSortKey.MAX_SPEED -> stringResource(R.string.routes_sort_max_speed)
                }
                FilterChip(
                    selected = filter.sortKey == key,
                    onClick = {
                        val newDir = if (filter.sortKey == key) {
                            if (filter.sortDir == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC
                        } else {
                            if (key == RouteSortKey.DATE) SortDirection.DESC else SortDirection.ASC
                        }
                        onSetSort(key, newDir)
                    },
                    label = {
                        Text(keyLabel, style = MotoTracker.typography.label, maxLines = 1)
                    },
                    trailingIcon = if (filter.sortKey == key) {
                        {
                            Icon(
                                imageVector = if (filter.sortDir == SortDirection.DESC) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    } else null,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Date range row + clear-all button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DatePickerChip(
                labelRes = R.string.routes_filter_date_from,
                epochMs = filter.fromEpochMs,
                onDateSelected = { ms -> onSetDateRange(ms, filter.toEpochMs) },
            )
            DatePickerChip(
                labelRes = R.string.routes_filter_date_to,
                epochMs = filter.toEpochMs,
                onDateSelected = { ms -> onSetDateRange(filter.fromEpochMs, ms) },
            )
            Spacer(Modifier.weight(1f))
            val isFiltered = filter != RoutesFilter()
            if (isFiltered) {
                TextButton(onClick = onClearFilters) {
                    Text(
                        text = stringResource(R.string.routes_clear_filters),
                        style = MotoTracker.typography.label,
                        color = MotoTracker.colors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun BikeDropdown(
    availableBikes: List<BikeFilterOption>,
    selectedBikeId: String?,
    onSelect: (String?) -> Unit,
    allBikesLabel: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = availableBikes.find { it.id == selectedBikeId }?.name ?: allBikesLabel
    Box {
        SuggestionChip(
            onClick = { expanded = true },
            icon = {
                Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            label = {
                Text(selectedName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MotoTracker.typography.label)
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allBikesLabel) },
                onClick = { onSelect(null); expanded = false },
            )
            availableBikes.forEach { bike ->
                DropdownMenuItem(
                    text = { Text(bike.name) },
                    onClick = { onSelect(bike.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun DatePickerChip(
    labelRes: Int,
    epochMs: Long?,
    onDateSelected: (Long?) -> Unit,
) {
    val context = LocalContext.current
    val label = stringResource(labelRes)
    val displayLabel = if (epochMs != null) {
        "$label: ${DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(epochMs))}"
    } else {
        label
    }
    SuggestionChip(
        onClick = {
            val cal = Calendar.getInstance()
            if (epochMs != null) cal.timeInMillis = epochMs
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val c = Calendar.getInstance()
                    c.set(year, month, day, 0, 0, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    onDateSelected(c.timeInMillis)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            ).show()
        },
        label = {
            Text(displayLabel, maxLines = 1, style = MotoTracker.typography.label)
        },
    )
}

// ── Route card ───────────────────────────────────────────────────────────────

@Composable
private fun RouteCard(
    card: RouteCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MotoTracker.colors.panel)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RouteThumbnailImage(pathD = card.thumbnailPathD)

        Column(modifier = Modifier.weight(1f)) {
            // Name + date row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = card.name,
                    style = MotoTracker.typography.routeTitle,
                    color = MotoTracker.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = card.dateDisplay,
                    style = MotoTracker.typography.bodySmall,
                    color = MotoTracker.colors.dim,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Bike name + optional SOLD tag
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.bikeName,
                    style = MotoTracker.typography.bodySmall,
                    color = MotoTracker.colors.dim,
                )
                if (card.bikeSold) {
                    Spacer(Modifier.width(6.dp))
                    TagChip(text = stringResource(R.string.tag_sold), accent = false)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Metrics row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricItem(value = card.distanceDisplay, label = null)
                MetricItem(value = card.durationDisplay, label = null)
                MetricItem(
                    value = card.maxSpeedDisplay,
                    label = stringResource(R.string.tag_max_short),
                )
            }

            Spacer(Modifier.height(6.dp))

            // Sync tag
            TagChip(
                text = if (card.synced) "✓" else stringResource(R.string.tag_queue),
                accent = card.synced,
            )
        }
    }
}

/**
 * Route mini-map thumbnail composable.
 *
 * Renders a 100×64 dp box with the route polyline drawn from [pathD] scaled into the box.
 * When [pathD] is blank or parses to fewer than 2 points, shows an empty placeholder box.
 *
 * The 320×200 SVG viewBox emitted by [RouteThumbnail.buildPathDFromPoints] is mapped into
 * the box dimensions with letterboxing (uniform scale, centred).
 *
 * @param pathD  SVG path `d` string from [RouteCardUi.thumbnailPathD]; empty = placeholder.
 * @param modifier Standard Compose modifier.
 */
@Composable
internal fun RouteThumbnailImage(
    pathD: String,
    modifier: Modifier = Modifier,
) {
    val points = remember(pathD) { RouteThumbnail.parsePathD(pathD) }
    val strokeColor = MotoTracker.colors.accent

    Box(
        modifier = modifier
            .size(width = 100.dp, height = 64.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MotoTracker.colors.panel2)
            .border(
                width = 1.dp,
                color = MotoTracker.colors.line,
                shape = RoundedCornerShape(6.dp),
            ),
    ) {
        if (points.size >= 2) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val viewW = 320f
                val viewH = 200f
                val scale = minOf(size.width / viewW, size.height / viewH)
                val offsetX = (size.width - viewW * scale) / 2f
                val offsetY = (size.height - viewH * scale) / 2f

                val path = Path()
                points.forEachIndexed { idx, (x, y) ->
                    val sx = offsetX + x * scale
                    val sy = offsetY + y * scale
                    if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MetricItem(value: String, label: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = MotoTracker.typography.bodySmall.copy(
                fontFamily = com.mototracker.ui.theme.JetBrainsMonoFamily,
                fontSize = 13.sp,
            ),
            color = MotoTracker.colors.text,
        )
        if (label != null) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = label,
                style = MotoTracker.typography.label,
                color = MotoTracker.colors.dim,
            )
        }
    }
}

@Composable
private fun TagChip(text: String, accent: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (accent) MotoTracker.colors.accent.copy(alpha = 0.18f)
                else MotoTracker.colors.panel2,
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MotoTracker.typography.label,
            color = if (accent) MotoTracker.colors.accent else MotoTracker.colors.dim,
        )
    }
}
