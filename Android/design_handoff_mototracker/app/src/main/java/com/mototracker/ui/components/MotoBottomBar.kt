package com.mototracker.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mototracker.ui.navigation.MotoDestination
import com.mototracker.ui.navigation.bottomNavDestinations
import com.mototracker.ui.theme.MotoTracker

/**
 * The MotoTracker bottom navigation bar showing the five main tab destinations:
 * Record · Routes · Riders · Stats · Settings.
 *
 * Active tab icon and label are tinted with [MotoTracker.colors.accent]; inactive
 * items use [MotoTracker.colors.dim]. The bar surface uses [MotoTracker.colors.panel].
 *
 * Disabled items (when [isItemEnabled] returns `false`) are greyed-out and non-tappable,
 * which is used to lock navigation to the Record tab while a recording is active.
 *
 * @param current       The currently active [MotoDestination] — used to highlight the
 *                      selected tab.
 * @param onSelect      Called with the selected [MotoDestination] when the user taps a
 *                      tab item.
 * @param isItemEnabled Returns `true` when the given [MotoDestination] tab should be
 *                      interactive. Defaults to `{ true }` so existing callers are unaffected.
 * @param modifier      Optional [Modifier].
 */
@Composable
fun MotoBottomBar(
    current: MotoDestination,
    onSelect: (MotoDestination) -> Unit,
    isItemEnabled: (MotoDestination) -> Boolean = { true },
    modifier: Modifier = Modifier,
) {
    val colors = MotoTracker.colors

    NavigationBar(
        containerColor = colors.panel,
        contentColor = colors.text,
        modifier = modifier,
    ) {
        bottomNavDestinations.forEach { dest ->
            val selected = current == dest

            NavigationBarItem(
                selected = selected,
                enabled = isItemEnabled(dest),
                onClick = { onSelect(dest) },
                icon = {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = stringResource(dest.labelRes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(dest.labelRes).uppercase(),
                        style = MotoTracker.typography.label,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent,
                    selectedTextColor = colors.accent,
                    indicatorColor = colors.panel2,
                    unselectedIconColor = colors.dim,
                    unselectedTextColor = colors.dim,
                ),
            )
        }
    }
}
