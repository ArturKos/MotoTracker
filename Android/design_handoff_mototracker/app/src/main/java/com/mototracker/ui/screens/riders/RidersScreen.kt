package com.mototracker.ui.screens.riders

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.data.model.FeedType
import com.mototracker.ui.permissions.AppFeaturePermission
import com.mototracker.ui.permissions.PermissionDeniedBanner
import com.mototracker.ui.permissions.PermissionRequirements
import com.mototracker.ui.permissions.rememberFeaturePermission
import com.mototracker.ui.theme.JetBrainsMonoFamily
import com.mototracker.ui.theme.MotoTracker
import kotlinx.coroutines.flow.collectLatest

/**
 * Riders screen — permission + ViewModel wrapper that delegates to [RidersContent].
 *
 * Live GPS, real BT scanning, and server feed are on-device-only (🔬).
 *
 * @param modifier   Standard Compose modifier.
 * @param viewModel  Hilt-injected [RidersViewModel].
 * @param onToast    Called with a localised message string for the Composable to show.
 */
@Composable
fun RidersScreen(
    modifier: Modifier = Modifier,
    viewModel: RidersViewModel = hiltViewModel(),
    onToast: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val inviteSentMsg = stringResource(R.string.toast_add_member)
    LaunchedEffect(viewModel.events) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is RidersEvent.InviteSent -> onToast(inviteSentMsg)
            }
        }
    }

    val context = LocalContext.current
    val sdkInt = Build.VERSION.SDK_INT
    val requiredBtPerms = remember(sdkInt) {
        PermissionRequirements.permissionsFor(AppFeaturePermission.BLUETOOTH_WAVES, sdkInt)
    }
    var wavesEnabled by remember {
        mutableStateOf(
            requiredBtPerms.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val wavesPerm = rememberFeaturePermission(AppFeaturePermission.BLUETOOTH_WAVES)

    RidersContent(
        state = state,
        wavesEnabled = wavesEnabled && !wavesPerm.denied,
        onAddByPhone = { viewModel.onAddByPhone(it) },
        onRequestWaves = { wavesPerm.requestThen { wavesEnabled = true } },
        modifier = modifier,
    )
}

/**
 * Pure renderer for the Riders screen: group members, live feed, and Bluetooth waves.
 * Extracted for Paparazzi screenshot testing — no ViewModels, permissions, or launchers.
 *
 * @param state           Pre-computed riders UI state.
 * @param wavesEnabled    Whether Bluetooth waves permission has been granted; drives the waves section.
 * @param onAddByPhone    Called with the phone number when the user confirms the add-member dialog.
 * @param onRequestWaves  Called when the user taps the BT permission request banner.
 * @param modifier        Standard Compose modifier.
 */
@Composable
fun RidersContent(
    state: RidersUiState,
    wavesEnabled: Boolean = false,
    onAddByPhone: (String) -> Unit = {},
    onRequestWaves: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        // ── GROUP section ─────────────────────────────────────────────────────
        item {
            SectionHeader(
                title = stringResource(R.string.label_group_title).uppercase(),
                badge = "${state.memberCount} ${stringResource(R.string.label_members)}",
            )
        }

        items(state.members) { member ->
            MemberRow(member = member)
        }

        item { Spacer(Modifier.height(8.dp)) }

        item {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MotoTracker.colors.accent,
                    contentColor = MotoTracker.colors.onAccent,
                ),
                shape = MotoTracker.shapes.card,
            ) {
                Text(
                    text = stringResource(R.string.btn_add_by_phone),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }

        // ── LIVE FEED section ──────────────────────────────────────────────────
        item {
            SectionHeader(
                title = stringResource(R.string.label_feed_title).uppercase(),
                badge = if (!state.feedAvailable) stringResource(R.string.label_no_internet) else null,
                badgeAccent = MotoTracker.colors.accent2,
            )
        }

        if (state.feedAvailable) {
            items(state.feed) { event ->
                FeedEventRow(event = event)
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.label_no_internet),
                        style = MotoTracker.typography.body,
                        color = MotoTracker.colors.dim,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }

        // ── WAVES section ──────────────────────────────────────────────────────
        // Hidden entirely when the waves setting is disabled in Settings.
        if (state.wavesEnabled) {
            item {
                SectionHeader(
                    title = stringResource(R.string.riders_waves_title).uppercase(),
                    badge = if (wavesEnabled) stringResource(R.string.label_works_offline) else null,
                    badgeAccent = MotoTracker.colors.accent,
                )
            }

            when {
                !wavesEnabled -> {
                    item {
                        PermissionDeniedBanner(
                            text = stringResource(R.string.perm_bt_rationale),
                            onRetry = onRequestWaves,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                state.waves.isEmpty() -> {
                    item { Spacer(Modifier.height(8.dp)) }
                }
                else -> {
                    items(state.waves) { wave ->
                        WaveRow(wave = wave)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAddDialog) {
        AddByPhoneDialog(
            onConfirm = { phone ->
                onAddByPhone(phone)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

/**
 * Uppercase dim section header with an optional inline badge chip.
 *
 * @param title       Section title, caller must pass uppercased.
 * @param badge       Optional badge text shown after the title.
 * @param badgeAccent Colour for the badge pill; defaults to [MotoTracker.colors.dim].
 */
@Composable
private fun SectionHeader(
    title: String,
    badge: String? = null,
    badgeAccent: androidx.compose.ui.graphics.Color = MotoTracker.colors.dim,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MotoTracker.typography.label,
            color = MotoTracker.colors.dim,
            letterSpacing = 0.6.sp,
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .background(
                        color = badgeAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badge,
                    style = MotoTracker.typography.label,
                    color = badgeAccent,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

// ── Member row ─────────────────────────────────────────────────────────────────

/**
 * A single riding-group member card row: avatar circle with initial, name bold, phone · bike dim mono.
 *
 * @param member The [GroupMemberUi] to render.
 */
@Composable
private fun MemberRow(member: GroupMemberUi) {
    val colors = MotoTracker.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 38dp circular avatar
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = member.initial,
                style = MotoTracker.typography.routeTitle,
                color = colors.accent,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name,
                style = MotoTracker.typography.body,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val secondary = buildString {
                append(member.phone)
                if (member.bikeName.isNotEmpty()) append(" · ${member.bikeName}")
            }
            Text(
                text = secondary,
                style = MotoTracker.typography.bodySmall,
                fontFamily = JetBrainsMonoFamily,
                color = colors.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Feed event row ─────────────────────────────────────────────────────────────

/**
 * A single live-feed row: coloured dot, rider name + action, optional speed value accent2, bike · time dim.
 *
 * @param event The [FeedEventUi] to render.
 */
@Composable
private fun FeedEventRow(event: FeedEventUi) {
    val colors = MotoTracker.colors
    val dotColor = when (event.dotColor) {
        FeedDotColor.ACCENT  -> colors.accent
        FeedDotColor.ACCENT2 -> colors.accent2
        FeedDotColor.DIM     -> colors.dim
    }
    val actionLabel = when (event.type) {
        FeedType.START  -> stringResource(R.string.label_feed_start)
        FeedType.FINISH -> stringResource(R.string.label_feed_finish)
        FeedType.MAX    -> stringResource(R.string.label_feed_max_speed)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 8dp coloured dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${event.who} $actionLabel",
                    style = MotoTracker.typography.body,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (event.isMax && event.value != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = event.value,
                        style = MotoTracker.typography.bodySmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = colors.accent2,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(
                text = "${event.bikeName} · ${event.timeLabel}",
                style = MotoTracker.typography.bodySmall,
                fontFamily = JetBrainsMonoFamily,
                color = colors.dim,
            )
        }
    }
}

// ── Wave row ───────────────────────────────────────────────────────────────────

/**
 * A single Bluetooth wave row: accent-tinted panel tile, nick · bike bold, place · time dim mono.
 *
 * @param wave The [WaveUi] to render.
 */
@Composable
private fun WaveRow(wave: WaveUi) {
    val colors = MotoTracker.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(MotoTracker.shapes.card)
            .background(colors.panel2)
            .border(
                width = 1.dp,
                color = colors.accent.copy(alpha = 0.25f),
                shape = MotoTracker.shapes.card,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 34dp rounded wave icon tile
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "👋",
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${wave.nick} · ${wave.bikeName}",
                style = MotoTracker.typography.body,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${stringResource(R.string.label_wave_at)} ${wave.place} · ${wave.timeLabel}",
                style = MotoTracker.typography.bodySmall,
                fontFamily = JetBrainsMonoFamily,
                color = colors.dim,
            )
        }
    }
}

// ── Add-by-phone dialog ────────────────────────────────────────────────────────

/**
 * Modal dialog prompting the user to enter a phone number for the group invite.
 *
 * @param onConfirm Called with the trimmed phone string when the user confirms.
 * @param onDismiss Called when the user dismisses or cancels.
 */
@Composable
private fun AddByPhoneDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var phoneInput by remember { mutableStateOf("") }
    val colors = MotoTracker.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        titleContentColor = colors.text,
        textContentColor = colors.dim,
        title = {
            Text(
                text = stringResource(R.string.btn_add_by_phone),
                style = MotoTracker.typography.routeTitle,
            )
        },
        text = {
            OutlinedTextField(
                value = phoneInput,
                onValueChange = { phoneInput = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (phoneInput.isNotBlank()) onConfirm(phoneInput.trim()) },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.line,
                    cursorColor = colors.accent,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text,
                ),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (phoneInput.isNotBlank()) onConfirm(phoneInput.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    color = colors.dim,
                )
            }
        },
    )
}
