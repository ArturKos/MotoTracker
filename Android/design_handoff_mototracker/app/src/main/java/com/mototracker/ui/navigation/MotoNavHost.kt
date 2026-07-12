package com.mototracker.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mototracker.R
import com.mototracker.ui.components.MotoBottomBar
import com.mototracker.ui.components.MotoTopAppBar
import com.mototracker.ui.screens.riders.RidersScreen
import com.mototracker.ui.screens.settings.SettingsScreen
import com.mototracker.ui.screens.stats.StatsScreen
import com.mototracker.ui.screens.detail.RouteDetailScreen
import com.mototracker.ui.screens.login.LoginScreen
import com.mototracker.ui.screens.record.RecordingScreen
import com.mototracker.ui.screens.routes.RoutesScreen
import com.mototracker.ui.theme.MotoTracker
import kotlinx.coroutines.launch

/**
 * Root composable for the MotoTracker app shell.
 *
 * Owns a [rememberNavController], wraps a [Scaffold] whose top bar and bottom bar
 * are conditionally shown based on the current back-stack destination (via the pure
 * [showTopBar] / [showBottomBar] / [showBackArrow] functions), and hosts the
 * [NavHost] with composables for every destination.
 *
 * An app-level [SnackbarHostState] is shared across all screens that use the
 * [onToast] callback pattern (e.g. [RidersScreen], [RouteDetailScreen]).
 *
 * The [startDestination] is driven by [startAtMain]: users who previously signed in or
 * chose guest mode start at [MotoDestination.RECORD]; unauthenticated users start at
 * [MotoDestination.LOGIN]. A guest ([startAtMain]=true, not authenticated) also lands at
 * [MotoDestination.RECORD] so guest-mode data is immediately accessible (B22).
 *
 * Both sign-in callbacks ([onSignIn] and [onContinueAsGuest]) invoke the app-level state update
 * and then navigate to [MotoDestination.RECORD], popping the login destination from the back stack
 * so the user cannot navigate back to the login screen via the system Back button.
 *
 * Must be called inside [com.mototracker.ui.theme.MotoTrackerTheme].
 *
 * @param startAtMain         Whether to start at the main shell ([MotoDestination.RECORD]).
 *                            `true` for AUTHED (with session) and GUEST users; `false` for NONE
 *                            or AUTHED-without-session.
 * @param sessionExpired      When `true`, the Login screen shows a session-expired notice (B22).
 * @param onSignIn            Callback invoked when the user completes sign-in (authed = true).
 * @param onContinueAsGuest   Callback invoked when the user taps "Continue as Guest" (authed = false).
 * @param recordingActive     Whether a recording session is currently active. When `true`, bottom-nav
 *                            tabs other than Record are disabled and the system back gesture is
 *                            consumed (showing a toast) so the user cannot leave mid-ride.
 */
@Composable
fun MotoApp(
    startAtMain: Boolean = false,
    sessionExpired: Boolean = false,
    onSignIn: () -> Unit = {},
    onContinueAsGuest: () -> Unit = {},
    recordingActive: Boolean = false,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = MotoDestination.fromRoute(navBackStackEntry?.destination?.route)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showToast: (String) -> Unit = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }

    // Consume the back gesture while on the Record screen during an active recording.
    val navLockedMessage = stringResource(R.string.record_nav_locked)
    BackHandler(enabled = recordingActive && currentDest == MotoDestination.RECORD) {
        showToast(navLockedMessage)
    }

    val startDestination = if (startAtMain) {
        MotoDestination.RECORD.route
    } else {
        MotoDestination.LOGIN.route
    }

    Scaffold(
        containerColor = MotoTracker.colors.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    isItemEnabled = { dest -> bottomNavItemEnabled(dest, recordingActive) },
                    onSelect = { dest ->
                        if (!bottomNavItemEnabled(dest, recordingActive)) return@MotoBottomBar
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
                LoginScreen(
                    sessionExpired = sessionExpired,
                    onSignedIn = {
                        onSignIn()
                        navController.navigate(MotoDestination.RECORD.route) {
                            popUpTo(MotoDestination.LOGIN.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onGuest = {
                        onContinueAsGuest()
                        navController.navigate(MotoDestination.RECORD.route) {
                            popUpTo(MotoDestination.LOGIN.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(MotoDestination.RECORD.route) { RecordingScreen() }
            composable(MotoDestination.ROUTES.route) {
                RoutesScreen(onOpenRoute = { routeId ->
                    navController.navigate("route_detail/$routeId")
                })
            }
            composable(MotoDestination.RIDERS.route) {
                RidersScreen(onToast = showToast)
            }
            composable(MotoDestination.STATS.route) { StatsScreen() }
            composable(MotoDestination.SETTINGS.route) {
                SettingsScreen(
                    onSignOut = {
                        navController.navigate(MotoDestination.LOGIN.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(
                route = MotoDestination.ROUTE_DETAIL.route,
                arguments = listOf(navArgument("routeId") { type = NavType.StringType }),
            ) {
                RouteDetailScreen(onToast = showToast)
            }
        }
    }
}
