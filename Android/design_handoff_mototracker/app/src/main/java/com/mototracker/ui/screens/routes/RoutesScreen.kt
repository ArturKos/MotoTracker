package com.mototracker.ui.screens.routes

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker
import kotlinx.coroutines.launch

/**
 * Routes list screen — shows summary tiles and a scrollable list of route cards.
 *
 * The GPX import button launches an [Intent.ACTION_OPEN_DOCUMENT] file picker for
 * `.gpx` files and shows a snackbar; actual GPX parsing is out of scope for B3.
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
    val context = LocalContext.current

    val importMsg = stringResource(R.string.toast_import)
    val gpxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* GPX parsing deferred to a later task */ }

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
                totalKmDisplay = state.totalKmDisplay,
                onImportGpx = {
                    scope.launch { snackbarHostState.showSnackbar(importMsg) }
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/gpx+xml", "application/octet-stream"))
                    }
                    gpxLauncher.launch(intent)
                },
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.cards, key = { it.id }) { card ->
                    RouteCard(card = card, onClick = { onOpenRoute(card.id) })
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ── Summary row ──────────────────────────────────────────────────────────────

@Composable
private fun SummaryRow(
    routeCount: Int,
    totalKmDisplay: String,
    onImportGpx: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryTile(
            label = stringResource(R.string.label_all_routes),
            value = routeCount.toString(),
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
        // Thumbnail placeholder (actual SVG rendering deferred — on-device 🔬)
        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MotoTracker.colors.panel2)
                .border(
                    width = 1.dp,
                    color = MotoTracker.colors.line,
                    shape = RoundedCornerShape(6.dp),
                ),
        )

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
