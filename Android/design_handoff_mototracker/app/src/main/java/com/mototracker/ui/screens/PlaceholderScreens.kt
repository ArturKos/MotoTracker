package com.mototracker.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker

/** Placeholder for [com.mototracker.ui.navigation.MotoDestination.RECORD] — replaced in B2. */
@Composable
fun RecordScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(title = stringResource(R.string.screen_record), modifier = modifier)
}

/** Placeholder for [com.mototracker.ui.navigation.MotoDestination.ROUTES] — replaced in B3. */
@Composable
fun RoutesScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(title = stringResource(R.string.screen_routes), modifier = modifier)
}

/** Placeholder for [com.mototracker.ui.navigation.MotoDestination.ROUTE_DETAIL] — replaced in B4. */
@Composable
fun RouteDetailScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(title = stringResource(R.string.screen_route_detail), modifier = modifier)
}

/** Placeholder for [com.mototracker.ui.navigation.MotoDestination.RIDERS] — replaced in B5. */
@Composable
fun RidersScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(title = stringResource(R.string.screen_riders), modifier = modifier)
}

/** Placeholder for [com.mototracker.ui.navigation.MotoDestination.STATS] — replaced in B6. */
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(title = stringResource(R.string.screen_stats), modifier = modifier)
}

/** Placeholder for [com.mototracker.ui.navigation.MotoDestination.SETTINGS] — replaced in B7. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(title = stringResource(R.string.screen_settings), modifier = modifier)
}

@Composable
private fun PlaceholderContent(title: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Text(
            text = title.uppercase(),
            style = MotoTracker.typography.screenTitle,
            color = MotoTracker.colors.text,
        )
    }
}
