package com.mototracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mototracker.R
import com.mototracker.ui.navigation.SyncChip
import com.mototracker.ui.navigation.SyncState
import com.mototracker.ui.theme.MotoTracker

/**
 * The MotoTracker top app bar shown on all screens except [com.mototracker.ui.navigation.MotoDestination.LOGIN].
 *
 * Renders:
 * - An optional back-arrow navigation icon (for route-detail; absent on main tabs).
 * - The screen [title] in [MotoTracker.typography.screenTitle] (22 sp, ALL CAPS).
 * - A trailing [SyncChip] reflecting the current [syncState].
 *
 * Colors are read from [MotoTracker.colors] so the bar inherits the active theme.
 *
 * @param title     Screen title string (already resolved from string resource at call site).
 * @param showBack  Whether to show the back-arrow navigation icon.
 * @param onBack    Called when the back arrow is tapped.
 * @param syncState The current outbound sync state for the chip.
 * @param modifier  Optional [Modifier].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotoTopAppBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    syncState: SyncState,
    modifier: Modifier = Modifier,
) {
    val colors = MotoTracker.colors

    TopAppBar(
        title = {
            Text(
                text = title.uppercase(),
                style = MotoTracker.typography.screenTitle,
                color = colors.text,
            )
        },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = colors.text,
                    )
                }
            }
        },
        actions = {
            SyncChip(state = syncState)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.panel,
            titleContentColor = colors.text,
            navigationIconContentColor = colors.text,
            actionIconContentColor = colors.text,
        ),
        modifier = modifier,
    )
}
