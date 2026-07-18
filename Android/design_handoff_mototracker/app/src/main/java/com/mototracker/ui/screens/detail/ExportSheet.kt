package com.mototracker.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker

/**
 * Export & share bottom sheet for the Route Detail screen.
 *
 * Presents five action rows:
 * - Export GPX file
 * - Export TCX file (Q2)
 * - Share route link
 * - Share as image (K4)
 * - Send to GPStrack server
 *
 * Each row invokes the matching ViewModel action and dismisses the sheet.
 * Sheet visibility is controlled by the caller via [onDismiss]; visibility state
 * is kept as Compose-local state in the parent screen.
 *
 * @param routeName     The route display name shown as the sheet subtitle.
 * @param onExportGpx   Called when the user taps "Export GPX file".
 * @param onExportTcx   Called when the user taps "Export TCX file" (Q2).
 * @param onShareRoute  Called when the user taps "Share route".
 * @param onShareImage  Called when the user taps "Share as image" (K4).
 * @param onSendServer  Called when the user taps "Send to GPStrack server".
 * @param onDismiss     Called when the sheet should be dismissed (drag away or option tapped).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    routeName: String,
    onExportGpx: () -> Unit,
    onExportTcx: () -> Unit,
    onShareRoute: () -> Unit,
    onShareImage: () -> Unit,
    onSendServer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MotoTracker.colors.panel,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MotoTracker.colors.dim.copy(alpha = 0.3f),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            Text(
                text = stringResource(R.string.title_export).uppercase(),
                style = MotoTracker.typography.label,
                color = MotoTracker.colors.dim,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = routeName,
                style = MotoTracker.typography.routeTitle,
                color = MotoTracker.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            ExportOptionRow(
                title = stringResource(R.string.btn_export_gpx),
                desc = stringResource(R.string.desc_export_gpx),
                onClick = { onExportGpx(); onDismiss() },
            )
            HorizontalDivider(color = MotoTracker.colors.line.copy(alpha = 0.4f))
            ExportOptionRow(
                title = stringResource(R.string.btn_export_tcx),
                desc = stringResource(R.string.desc_export_tcx),
                onClick = { onExportTcx(); onDismiss() },
            )
            HorizontalDivider(color = MotoTracker.colors.line.copy(alpha = 0.4f))
            ExportOptionRow(
                title = stringResource(R.string.btn_share_route),
                desc = stringResource(R.string.desc_share_route),
                onClick = { onShareRoute(); onDismiss() },
            )
            HorizontalDivider(color = MotoTracker.colors.line.copy(alpha = 0.4f))
            ExportOptionRow(
                title = stringResource(R.string.btn_share_image),
                desc = stringResource(R.string.desc_share_image),
                onClick = { onShareImage(); onDismiss() },
            )
            HorizontalDivider(color = MotoTracker.colors.line.copy(alpha = 0.4f))
            ExportOptionRow(
                title = stringResource(R.string.btn_send_server),
                desc = stringResource(R.string.desc_send_server),
                onClick = { onSendServer(); onDismiss() },
            )
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/**
 * A single tappable export option row: bold title, dim description, and a trailing chevron.
 *
 * @param title   Primary action label.
 * @param desc    Secondary description / hint text.
 * @param onClick Invoked when the row is tapped.
 */
@Composable
private fun ExportOptionRow(
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MotoTracker.typography.body,
                fontWeight = FontWeight.SemiBold,
                color = MotoTracker.colors.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = desc,
                style = MotoTracker.typography.bodySmall,
                color = MotoTracker.colors.dim,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MotoTracker.colors.dim,
            modifier = Modifier.size(20.dp),
        )
    }
}
