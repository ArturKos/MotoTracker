package com.mototracker.ui.screens.settings

import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mototracker.R
import com.mototracker.data.local.entity.BikeStatus
import com.mototracker.ui.state.AppStateViewModel
import com.mototracker.ui.state.Language
import com.mototracker.ui.state.Units
import com.mototracker.ui.theme.AccentColor
import com.mototracker.ui.theme.MotoTheme
import com.mototracker.ui.theme.MotoTracker

/**
 * Settings screen (B7) — nine sections rendered in a [LazyColumn].
 *
 * This screen reads from two ViewModels:
 * - [SettingsViewModel] for all persisted settings state.
 * - [AppStateViewModel] for the authenticated session state and live theme/language/units/accent.
 *
 * Appearance changes (theme, accent, language, units) are routed through
 * [AppStateViewModel] intents for live UI update AND persisted via [SettingsViewModel].
 *
 * [AppStateViewModel] is resolved against the enclosing [ComponentActivity]'s
 * [androidx.lifecycle.ViewModelStoreOwner] — the same scope used by [com.mototracker.MainActivity]
 * — so that both sides observe the **same instance** and theme/accent changes propagate immediately.
 *
 * @param modifier       Standard Compose modifier.
 * @param viewModel      Hilt-injected [SettingsViewModel].
 * @param appStateVm     [AppStateViewModel] resolved from the Activity scope; defaults to
 *                       `hiltViewModel(activity)` so Settings and MainActivity share one instance.
 * @param onSignOut      Called after [AppStateViewModel.signOut]; the host navigates to Login.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    appStateVm: AppStateViewModel = hiltViewModel(checkNotNull(LocalActivity.current) as ComponentActivity),
    onSignOut: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appState by appStateVm.uiState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val addBikeMsg = stringResource(R.string.toast_add_bike)

    SettingsContent(
        state = state,
        authed = appState.authed,
        onSignOut = {
            appStateVm.signOut()
            onSignOut()
        },
        onSelectBike = viewModel::selectBike,
        onAddBike = { name, year, plate, status ->
            viewModel.addBike(name, year, plate, status)
            Toast.makeText(ctx, addBikeMsg, Toast.LENGTH_SHORT).show()
        },
        onUpdateBike = viewModel::updateBike,
        onTheme = { key ->
            viewModel.setTheme(key)
            appStateVm.setTheme(MotoTheme.entries.firstOrNull { it.name.lowercase() == key } ?: MotoTheme.COCKPIT)
        },
        onAccent = { hex ->
            viewModel.setAccent(hex)
            val mapped = AccentColor.entries.firstOrNull { it.hex == hex } ?: AccentColor.TEAL
            appStateVm.setAccent(mapped)
        },
        onLanguage = { tag ->
            viewModel.setLanguage(tag)
            appStateVm.setLanguage(Language.entries.firstOrNull { it.tag == tag } ?: Language.PL)
        },
        onUnits = { key ->
            viewModel.setUnits(key)
            appStateVm.setUnits(if (key == "imperial") Units.IMPERIAL else Units.METRIC)
        },
        onServerAddress = viewModel::setServerAddress,
        onOffline = viewModel::setOffline,
        onAutoSync = viewModel::setAutoSync,
        onSyncNow = viewModel::syncNow,
        onSaveBroadcastProfile = viewModel::saveBroadcastProfile,
        onOfflineOnly = viewModel::setOfflineOnly,
        onGpsCorrect = viewModel::setGpsCorrect,
        onAndroidAutoEnabled = viewModel::setAndroidAutoEnabled,
        onDebugLogging = viewModel::setDebugLogging,
        onShareLog = {
            val file = viewModel.getShareTargetFile()
            if (file != null) {
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(Intent.createChooser(intent, file.name))
            }
        },
        onClearLogs = viewModel::clearRideLogs,
        onAutoPause = viewModel::setAutoPause,
        onKeepScreenOn = viewModel::setKeepScreenOn,
        modifier = modifier,
    )
}

/**
 * Pure renderer for the Settings screen: nine sections in a scrollable list.
 * Extracted for Paparazzi screenshot testing — no ViewModels or Activity references.
 *
 * @param state                Pre-computed settings UI state.
 * @param authed               Whether the user is currently signed in (drives Account section).
 * @param onSignOut            Called when the user taps Sign out or Login (navigates away).
 * @param onSelectBike         Called with the bike UUID when the user selects it as active.
 * @param onAddBike            Called when a new bike is confirmed via the Add dialog.
 * @param onUpdateBike         Called when an existing bike is updated via the Edit dialog.
 * @param onTheme              Called with the theme key ("cockpit"|"grid"|"light").
 * @param onAccent             Called with the accent hex string.
 * @param onLanguage           Called with the BCP-47 language tag.
 * @param onUnits              Called with the units key ("metric"|"imperial").
 * @param onServerAddress      Called when the server-address field changes.
 * @param onOffline            Called when the Offline mode switch changes.
 * @param onAutoSync           Called when the Auto-sync switch changes.
 * @param onSyncNow            Called when the user taps the Sync-all button.
 * @param onSaveBroadcastProfile Called when the user saves their BT broadcast profile.
 * @param onOfflineOnly        Called when the Offline-only switch changes.
 * @param onGpsCorrect         Called when the GPS-correction switch changes.
 * @param onAndroidAutoEnabled Called when the Android Auto switch changes.
 * @param onDebugLogging       Called when the Debug-logging switch changes.
 * @param onShareLog           Called when the user taps Share log file.
 * @param onClearLogs          Called when the user taps Clear ride logs.
 * @param onAutoPause          Called when the Auto-pause switch changes.
 * @param onKeepScreenOn       Called when the Keep-screen-on switch changes.
 * @param modifier             Standard Compose modifier.
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    authed: Boolean = false,
    onSignOut: () -> Unit = {},
    onSelectBike: (String) -> Unit = {},
    onAddBike: (String, Int, String, BikeStatus) -> Unit = { _, _, _, _ -> },
    onUpdateBike: (String, String, Int, String, BikeStatus) -> Unit = { _, _, _, _, _ -> },
    onTheme: (String) -> Unit = {},
    onAccent: (String) -> Unit = {},
    onLanguage: (String) -> Unit = {},
    onUnits: (String) -> Unit = {},
    onServerAddress: (String) -> Unit = {},
    onOffline: (Boolean) -> Unit = {},
    onAutoSync: (Boolean) -> Unit = {},
    onSyncNow: () -> Unit = {},
    onSaveBroadcastProfile: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onOfflineOnly: (Boolean) -> Unit = {},
    onGpsCorrect: (Boolean) -> Unit = {},
    onAndroidAutoEnabled: (Boolean) -> Unit = {},
    onDebugLogging: (Boolean) -> Unit = {},
    onShareLog: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onAutoPause: (Boolean) -> Unit = {},
    onKeepScreenOn: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current

    var showBikeDialog by rememberSaveable { mutableStateOf(false) }
    var editBikeId by rememberSaveable { mutableStateOf<String?>(null) }

    if (showBikeDialog) {
        val editBike = editBikeId?.let { id -> state.bikes.find { it.id == id } }
        AddEditBikeDialog(
            initial = editBike,
            onConfirm = { name, year, plate, status ->
                if (editBikeId != null) {
                    onUpdateBike(editBikeId!!, name, year, plate, status)
                } else {
                    onAddBike(name, year, plate, status)
                }
            },
            onDismiss = {
                showBikeDialog = false
                editBikeId = null
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MotoTracker.colors.bg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ── §1 Account ────────────────────────────────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.section_account))
            AccountSection(
                authed = authed,
                onLogout = onSignOut,
                onLogin = onSignOut,
            )
            SectionDivider()
        }

        // ── §2 My motorcycles ─────────────────────────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.section_bikes))
        }
        items(state.bikes) { bike ->
            BikeRow(
                bike = bike,
                onSelect = { onSelectBike(bike.id) },
                onEdit = {
                    editBikeId = bike.id
                    showBikeDialog = true
                },
            )
        }
        item {
            TextButton(
                onClick = {
                    editBikeId = null
                    showBikeDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.btn_add_bike),
                    color = MotoTracker.colors.accent,
                    style = MotoTracker.typography.label,
                )
            }
            SectionDivider()
        }

        // ── §3 Appearance & language ──────────────────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.section_appearance))
            AppearanceSection(
                theme = state.theme,
                accent = state.accent,
                language = state.language,
                units = state.units,
                onTheme = onTheme,
                onAccent = onAccent,
                onLanguage = onLanguage,
                onUnits = onUnits,
            )
            SectionDivider()
        }

        // ── §4 Server & sync ──────────────────────────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.section_server))
            ServerSection(
                serverAddress = state.serverAddress,
                offline = state.offline,
                autoSync = state.autoSync,
                onServerAddress = onServerAddress,
                onOffline = onOffline,
                onAutoSync = onAutoSync,
            )
            SectionDivider()
        }

        // ── §5 Sync queue ─────────────────────────────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.section_sync_queue))
            if (state.pendingRoutes.isEmpty()) {
                Text(
                    text = stringResource(R.string.label_all_synced),
                    color = MotoTracker.colors.dim,
                    style = MotoTracker.typography.label,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Button(
                    onClick = onSyncNow,
                    colors = ButtonDefaults.buttonColors(containerColor = MotoTracker.colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.btn_send_all),
                        color = MotoTracker.colors.onAccent,
                        style = MotoTracker.typography.label,
                    )
                }
            }
        }
        items(state.pendingRoutes) { item ->
            SyncQueueRow(
                item = item,
                onSend = onSyncNow,
            )
        }
        item { SectionDivider() }

        // ── §6 Bluetooth broadcast ────────────────────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.section_broadcast))
            BroadcastSection(
                state = state,
                onSave = onSaveBroadcastProfile,
            )
            SectionDivider()
        }

        // ── §7 System & privacy ───────────────────────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.section_system))
            LabeledSwitch(
                label = stringResource(R.string.label_offline_only),
                desc = stringResource(R.string.desc_offline_only),
                checked = state.offlineOnly,
                onChecked = onOfflineOnly,
            )
            LabeledSwitch(
                label = stringResource(R.string.label_gps_correct),
                desc = stringResource(R.string.desc_gps_correct),
                checked = state.gpsCorrect,
                onChecked = onGpsCorrect,
            )
            LabeledSwitch(
                label = stringResource(R.string.label_android_auto),
                desc = stringResource(R.string.desc_android_auto),
                checked = state.androidAutoEnabled,
                onChecked = onAndroidAutoEnabled,
            )

            // Diagnostics sub-group
            SectionHeader(title = stringResource(R.string.section_diagnostics))
            LabeledSwitch(
                label = stringResource(R.string.label_debug_logging),
                desc = stringResource(R.string.desc_debug_logging),
                checked = state.debugLoggingEnabled,
                onChecked = onDebugLogging,
            )
            DiagnosticsActionRow(
                label = stringResource(R.string.action_share_log),
                enabled = state.debugLoggingEnabled && state.rideLogUsedBytes > 0,
                onClick = onShareLog,
            )
            DiagnosticsActionRow(
                label = stringResource(
                    R.string.diag_used_space,
                    Formatter.formatFileSize(ctx, state.rideLogUsedBytes),
                ),
                sublabel = stringResource(R.string.action_clear_logs),
                enabled = state.debugLoggingEnabled,
                onClick = onClearLogs,
            )
            SectionDivider()
        }

        // ── §8 Preferences ────────────────────────────────────────────────────
        item {
            SectionHeader(title = stringResource(R.string.section_preferences))
            UnitsSelector(
                units = state.units,
                onUnits = onUnits,
            )
            LabeledSwitch(
                label = stringResource(R.string.label_auto_pause),
                desc = null,
                checked = state.autoPause,
                onChecked = onAutoPause,
            )
            LabeledSwitch(
                label = stringResource(R.string.label_screen_on),
                desc = null,
                checked = state.keepScreenOn,
                onChecked = onKeepScreenOn,
            )
            SectionDivider()
        }

        // ── §9 Version footer ─────────────────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.label_version_info),
                color = MotoTracker.colors.dim,
                style = MotoTracker.typography.label,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

/** Section title header row. */
@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        color = MotoTracker.colors.accent,
        style = MotoTracker.typography.label.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/** Thin horizontal rule between sections. */
@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = MotoTracker.colors.line,
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** Labelled toggle row with optional description subtitle. */
@Composable
private fun LabeledSwitch(
    label: String,
    desc: String?,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = MotoTracker.colors.text, style = MotoTracker.typography.body)
            if (desc != null) {
                Text(text = desc, color = MotoTracker.colors.dim, style = MotoTracker.typography.label)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedTrackColor = MotoTracker.colors.accent),
        )
    }
}

/** Account section content: avatar, name/mode, and login/logout button. */
@Composable
private fun AccountSection(
    authed: Boolean,
    onLogout: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MotoTracker.colors.accent.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (authed) "JK" else "?",
                color = MotoTracker.colors.accent,
                style = MotoTracker.typography.label.copy(fontWeight = FontWeight.Bold),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (authed) {
                    stringResource(R.string.label_account_authed_name)
                } else {
                    stringResource(R.string.label_guest_mode)
                },
                color = MotoTracker.colors.text,
                style = MotoTracker.typography.body.copy(fontWeight = FontWeight.SemiBold),
            )
            if (!authed) {
                Text(
                    text = stringResource(R.string.label_local_data),
                    color = MotoTracker.colors.dim,
                    style = MotoTracker.typography.label,
                )
            }
        }
        TextButton(onClick = if (authed) onLogout else onLogin) {
            Text(
                text = if (authed) {
                    stringResource(R.string.btn_logout)
                } else {
                    stringResource(R.string.btn_login)
                },
                color = MotoTracker.colors.accent,
                style = MotoTracker.typography.label,
            )
        }
    }
}

/**
 * Single bike row: name, year·plate, status badge, CURRENT tag.
 *
 * Tapping the row marks it as the currently active bike ([onSelect]).
 * The pencil icon opens the edit dialog ([onEdit]).
 */
@Composable
private fun BikeRow(
    bike: BikeUi,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = bike.name, color = MotoTracker.colors.text, style = MotoTracker.typography.body)
            Text(text = bike.yearPlate, color = MotoTracker.colors.dim, style = MotoTracker.typography.label)
        }
        Spacer(modifier = Modifier.width(4.dp))
        if (bike.isCurrent) {
            StatusChip(text = stringResource(R.string.tag_current), accent = true)
            Spacer(modifier = Modifier.width(6.dp))
        }
        val statusText = if (bike.status == BikeStatus.ACTIVE) {
            stringResource(R.string.status_active)
        } else {
            stringResource(R.string.status_sold)
        }
        StatusChip(text = statusText, accent = bike.status == BikeStatus.ACTIVE)
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.dialog_title_edit_bike),
                tint = MotoTracker.colors.dim,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Small status badge chip. */
@Composable
private fun StatusChip(text: String, accent: Boolean) {
    val bg = if (accent) MotoTracker.colors.accent.copy(alpha = 0.15f) else MotoTracker.colors.panel2
    val textColor = if (accent) MotoTracker.colors.accent else MotoTracker.colors.dim
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = text, color = textColor, style = MotoTracker.typography.label.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
    }
}

/** Appearance & language section: theme selector, accent, language, units. */
@Composable
private fun AppearanceSection(
    theme: String,
    accent: String,
    language: String,
    units: String,
    onTheme: (String) -> Unit,
    onAccent: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onUnits: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingLabel(stringResource(R.string.label_theme))
        ChipRow(
            options = listOf(
                "cockpit" to stringResource(R.string.theme_cockpit),
                "grid" to stringResource(R.string.theme_grid),
                "light" to stringResource(R.string.theme_light),
            ),
            selected = theme,
            onSelect = onTheme,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingLabel(stringResource(R.string.label_accent_color))
        AccentColorRow(selectedHex = accent, onSelect = onAccent)
        Spacer(modifier = Modifier.height(8.dp))
        SettingLabel(stringResource(R.string.label_language))
        ChipRow(
            options = listOf("pl" to "PL", "en" to "EN", "de" to "DE", "fr" to "FR", "cs" to "CS", "ru" to "RU"),
            selected = language,
            onSelect = onLanguage,
        )
        Spacer(modifier = Modifier.height(8.dp))
        UnitsSelector(units = units, onUnits = onUnits)
    }
}

/** Units row, shared between §3 and §8. */
@Composable
private fun UnitsSelector(
    units: String,
    onUnits: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingLabel(stringResource(R.string.label_units))
        ChipRow(
            options = listOf(
                "metric" to stringResource(R.string.unit_metric),
                "imperial" to stringResource(R.string.unit_imperial),
            ),
            selected = units,
            onSelect = onUnits,
        )
    }
}

/** Small grey label above a chip row. */
@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        color = MotoTracker.colors.dim,
        style = MotoTracker.typography.label,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** Row of selectable text chips; the selected chip is highlighted with accent. */
@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label) ->
            val isSelected = key == selected
            val bg = if (isSelected) MotoTracker.colors.accent else MotoTracker.colors.panel2
            val textColor = if (isSelected) MotoTracker.colors.onAccent else MotoTracker.colors.text
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(text = label, color = textColor, style = MotoTracker.typography.label)
            }
        }
    }
}

/** Four accent colour swatches. */
@Composable
private fun AccentColorRow(
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccentColor.entries.forEach { ac ->
            val isSelected = ac.hex == selectedHex
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ac.value)
                    .then(
                        if (isSelected) Modifier.border(2.dp, MotoTracker.colors.text, CircleShape)
                        else Modifier
                    )
                    .clickable { onSelect(ac.hex) },
            )
        }
    }
}

/** Server address + offline mode + auto-sync section. */
@Composable
private fun ServerSection(
    serverAddress: String,
    offline: Boolean,
    autoSync: Boolean,
    onServerAddress: (String) -> Unit,
    onOffline: (Boolean) -> Unit,
    onAutoSync: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addressDraft by rememberSaveable { mutableStateOf(serverAddress) }
    Column(modifier = modifier) {
        OutlinedTextField(
            value = addressDraft,
            onValueChange = { addressDraft = it },
            label = {
                Text(stringResource(R.string.label_server_address), color = MotoTracker.colors.dim, style = MotoTracker.typography.label)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onServerAddress(addressDraft) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MotoTracker.colors.text,
                unfocusedTextColor = MotoTracker.colors.text,
                focusedBorderColor = MotoTracker.colors.accent,
                unfocusedBorderColor = MotoTracker.colors.line,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(6.dp))
        LabeledSwitch(
            label = stringResource(R.string.label_offline_mode),
            desc = stringResource(R.string.desc_offline_mode),
            checked = offline,
            onChecked = onOffline,
        )
        LabeledSwitch(
            label = stringResource(R.string.label_auto_sync),
            desc = stringResource(R.string.desc_auto_sync),
            checked = autoSync,
            onChecked = onAutoSync,
        )
    }
}

/** Single row in the sync queue list. */
@Composable
private fun SyncQueueRow(
    item: SyncQueueItemUi,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, color = MotoTracker.colors.text, style = MotoTracker.typography.body)
            Text(
                text = "${item.dateDisplay}  ${item.kmDisplay}",
                color = MotoTracker.colors.dim,
                style = MotoTracker.typography.label,
            )
        }
        TextButton(onClick = onSend) {
            Text(text = stringResource(R.string.btn_send), color = MotoTracker.colors.accent, style = MotoTracker.typography.label)
        }
    }
}

/** Bluetooth broadcast profile section: 4 editable + 3 read-only fields + Save. */
@Composable
private fun BroadcastSection(
    state: SettingsUiState,
    onSave: (name: String, phone: String, origin: String, social: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nameField by rememberSaveable { mutableStateOf(state.bcName) }
    var phoneField by rememberSaveable { mutableStateOf(state.bcPhone) }
    var originField by rememberSaveable { mutableStateOf(state.bcOrigin) }
    var socialField by rememberSaveable { mutableStateOf(state.bcSocial) }
    val context = LocalContext.current
    val savedMsg = stringResource(R.string.toast_bc_saved)

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.label_broadcast_intro),
            color = MotoTracker.colors.dim,
            style = MotoTracker.typography.label,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        BcTextField(label = stringResource(R.string.label_bc_name), value = nameField, onValue = { nameField = it })
        BcTextField(label = stringResource(R.string.label_bc_phone), value = phoneField, onValue = { phoneField = it })
        BcTextField(label = stringResource(R.string.label_bc_from), value = originField, onValue = { originField = it })
        BcTextField(label = stringResource(R.string.label_bc_social), value = socialField, onValue = { socialField = it })
        // Read-only auto fields
        BcReadOnly(label = stringResource(R.string.label_bc_bike), value = state.bcBikeDisplay)
        BcReadOnly(label = stringResource(R.string.label_bc_today), value = state.bcTodayDisplay)
        BcReadOnly(label = stringResource(R.string.label_bc_total), value = state.bcTotalDisplay)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onSave(nameField, phoneField, originField, socialField)
                Toast.makeText(context, savedMsg, Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MotoTracker.colors.accent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.btn_save),
                color = MotoTracker.colors.onAccent,
                style = MotoTracker.typography.label,
            )
        }
    }
}

/** Editable text field used in the broadcast profile and add/edit bike dialog. */
@Composable
private fun BcTextField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, color = MotoTracker.colors.dim, style = MotoTracker.typography.label) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MotoTracker.colors.text,
            unfocusedTextColor = MotoTracker.colors.text,
            focusedBorderColor = MotoTracker.colors.accent,
            unfocusedBorderColor = MotoTracker.colors.line,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    )
}

/**
 * Dialog for adding a new motorcycle (when [initial] is null) or editing an existing one.
 *
 * Form state is held locally with [rememberSaveable] / [remember]. Validation is
 * performed via [BikeFormValidation.validate] on confirm; the dialog stays open and
 * highlights the offending field on invalid input.
 *
 * @param initial    Pre-fills the form for edit mode; null means add mode.
 * @param onConfirm  Called with validated values when the user taps Save.
 * @param onDismiss  Called when the user taps Cancel or dismisses the dialog.
 */
@Composable
private fun AddEditBikeDialog(
    initial: BikeUi?,
    onConfirm: (name: String, year: Int, plate: String, status: BikeStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    var nameText by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var yearText by rememberSaveable {
        mutableStateOf(if (initial != null && initial.year > 0) initial.year.toString() else "")
    }
    var plateText by rememberSaveable { mutableStateOf(initial?.plate ?: "") }
    var status by remember { mutableStateOf(initial?.status ?: BikeStatus.ACTIVE) }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var yearError by rememberSaveable { mutableStateOf(false) }

    val title = if (initial == null) stringResource(R.string.dialog_title_add_bike)
    else stringResource(R.string.dialog_title_edit_bike)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MotoTracker.colors.bg,
        titleContentColor = MotoTracker.colors.text,
        textContentColor = MotoTracker.colors.text,
        title = {
            Text(text = title, style = MotoTracker.typography.body.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column {
                BcTextField(
                    label = stringResource(R.string.label_bike_name),
                    value = nameText,
                    onValue = { nameText = it; nameError = false },
                )
                if (nameError) {
                    Text(
                        text = stringResource(R.string.error_name_blank),
                        color = Color.Red,
                        style = MotoTracker.typography.label,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    )
                }
                BcTextField(
                    label = stringResource(R.string.label_bike_year),
                    value = yearText,
                    onValue = { yearText = it; yearError = false },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (yearError) {
                    Text(
                        text = stringResource(R.string.error_year_invalid),
                        color = Color.Red,
                        style = MotoTracker.typography.label,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    )
                }
                BcTextField(
                    label = stringResource(R.string.label_bike_plate),
                    value = plateText,
                    onValue = { plateText = it },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.label_bike_status),
                    color = MotoTracker.colors.dim,
                    style = MotoTracker.typography.label,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        BikeStatus.ACTIVE to stringResource(R.string.status_active),
                        BikeStatus.SOLD to stringResource(R.string.status_sold),
                    ).forEach { (s, label) ->
                        val isSelected = status == s
                        val bg = if (isSelected) MotoTracker.colors.accent else MotoTracker.colors.panel2
                        val textColor = if (isSelected) MotoTracker.colors.onAccent else MotoTracker.colors.text
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                                .clickable { status = s }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(text = label, color = textColor, style = MotoTracker.typography.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (val result = BikeFormValidation.validate(nameText, yearText, plateText)) {
                    is BikeFormResult.NameBlank -> nameError = true
                    is BikeFormResult.YearInvalid -> yearError = true
                    is BikeFormResult.Valid -> {
                        onConfirm(result.name, result.year, result.plate, status)
                        onDismiss()
                    }
                }
            }) {
                Text(
                    text = stringResource(R.string.btn_save),
                    color = MotoTracker.colors.accent,
                    style = MotoTracker.typography.label,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    color = MotoTracker.colors.dim,
                    style = MotoTracker.typography.label,
                )
            }
        },
    )
}

/** Read-only broadcast field row (auto-computed value). */
@Composable
private fun BcReadOnly(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MotoTracker.colors.dim, style = MotoTracker.typography.label)
        Text(text = value, color = MotoTracker.colors.text, style = MotoTracker.typography.label)
    }
}

/**
 * Clickable action row used in the Diagnostics sub-group.
 *
 * Shows [label] as the primary text and an optional [sublabel] below it.  When
 * [enabled] is false the row is rendered dimmed and non-interactive.
 *
 * @param label    Primary label (e.g. "Share log" or the used-space counter).
 * @param sublabel Optional secondary label shown below [label].
 * @param enabled  Whether the row responds to clicks.
 * @param onClick  Called when the user taps the row.
 */
@Composable
private fun DiagnosticsActionRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    sublabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val textColor = if (enabled) MotoTracker.colors.accent else MotoTracker.colors.dim
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = textColor, style = MotoTracker.typography.body)
            if (sublabel != null) {
                Text(text = sublabel, color = MotoTracker.colors.dim, style = MotoTracker.typography.label)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AccentColor hex extension (maps AccentColor enum to hex string used in DataStore)
// ─────────────────────────────────────────────────────────────────────────────

private val AccentColor.hex: String
    get() = when (this) {
        AccentColor.TEAL -> "#00D1B2"
        AccentColor.ORANGE -> "#FF5C38"
        AccentColor.LIME -> "#C6FF00"
        AccentColor.BLUE -> "#3B82F6"
    }
