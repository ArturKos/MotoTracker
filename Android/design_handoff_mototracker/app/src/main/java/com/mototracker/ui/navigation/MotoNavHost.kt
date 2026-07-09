package com.mototracker.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mototracker.ui.components.MotoBottomBar
import com.mototracker.ui.components.MotoTopAppBar
import com.mototracker.ui.screens.LoginScreen
import com.mototracker.ui.screens.RecordScreen
import com.mototracker.ui.screens.RidersScreen
import com.mototracker.ui.screens.RouteDetailScreen
import com.mototracker.ui.screens.RoutesScreen
import com.mototracker.ui.screens.SettingsScreen
import com.mototracker.ui.screens.StatsScreen
import com.mototracker.ui.theme.MotoTracker

/**
 * Root composable for the MotoTracker app shell.
 *
 * Owns a [rememberNavController], wraps a [Scaffold] whose top bar and bottom bar
 * are conditionally shown based on the current back-stack destination (via the pure
 * [showTopBar] / [showBottomBar] / [showBackArrow] functions), and hosts the
 * [NavHost] with placeholder composables for every destination.
 *
 * The [startDestination] is driven by [authed]: authenticated users start at [MotoDestination.RECORD];
 * unauthenticated / guest users start at [MotoDestination.LOGIN].
 *
 * Sync state is hardcoded to [SyncState.Offline] until the sync feature (A7) is implemented.
 *
 * Must be called inside [com.mototracker.ui.theme.MotoTrackerTheme].
 *
 * @param authed          Whether the current session is authenticated. Controls start destination.
 * @param onContinueAsGuest Callback invoked when the user taps "Continue as Guest" on the login screen.
 */
@Composable
fun MotoApp(
    authed: Boolean = false,
    onContinueAsGuest: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = MotoDestination.fromRoute(navBackStackEntry?.destination?.route)

    val startDestination = if (authed) {
        MotoDestination.RECORD.route
    } else {
        MotoDestination.LOGIN.route
    }

    Scaffold(
        containerColor = MotoTracker.colors.bg,
        topBar = {
            if (showTopBar(currentDest)) {
                MotoTopAppBar(
                    title = stringResource(currentDest.titleRes),
                    showBack = showBackArrow(currentDest),
                    onBack = { navController.popBackStack() },
                    syncState = SyncState.Offline,
                )
            }
        },
        bottomBar = {
            if (showBottomBar(currentDest)) {
                MotoBottomBar(
                    current = currentDest,
                    onSelect = { dest ->
                        navController.navigate(dest.route) {
                            popUpTo(MotoDestination.RECORD.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .background(MotoTracker.colors.bg)
                .padding(innerPadding),
        ) {
            composable(MotoDestination.LOGIN.route) {
                LoginScreen(onContinueAsGuest = onContinueAsGuest)
            }
            composable(MotoDestination.RECORD.route) { RecordScreen() }
            composable(MotoDestination.ROUTES.route) { RoutesScreen() }
            composable(MotoDestination.RIDERS.route) { RidersScreen() }
            composable(MotoDestination.STATS.route) { StatsScreen() }
            composable(MotoDestination.SETTINGS.route) { SettingsScreen() }
            composable(MotoDestination.ROUTE_DETAIL.route) {
                RouteDetailScreen()
            }
        }
    }
}
