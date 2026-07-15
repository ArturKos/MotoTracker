package com.mototracker.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector
import com.mototracker.R
import com.mototracker.ui.screens.record.RecordingPhase

/**
 * Sealed class modelling the seven top-level navigation destinations in the
 * MotoTracker app.
 *
 * Each destination carries:
 * - [route]    — stable string identifier used as the NavHost route key.
 * - [labelRes] — string resource for the bottom-nav tab label (5 main tabs only).
 * - [titleRes] — string resource for the top-bar screen title.
 * - [icon]     — Material vector icon shown in the bottom navigation bar.
 */
sealed class MotoDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    /** Login / server setup screen. Hidden from bottom nav and top bar per spec. */
    object LOGIN : MotoDestination("login", R.string.nav_login, R.string.screen_login, Icons.Filled.Lock)

    /** Live recording screen — the app's default start destination. */
    object RECORD : MotoDestination("record", R.string.nav_record, R.string.screen_record, Icons.Filled.FiberManualRecord)

    /** Route list screen showing saved rides. */
    object ROUTES : MotoDestination("routes", R.string.nav_routes, R.string.screen_routes, Icons.Filled.Route)

    /** Riders / group screen for friends and Bluetooth waves. */
    object RIDERS : MotoDestination("riders", R.string.nav_riders, R.string.screen_riders, Icons.Filled.Person)

    /** Statistics summary screen. */
    object STATS : MotoDestination("stats", R.string.nav_stats, R.string.screen_stats, Icons.AutoMirrored.Filled.ShowChart)

    /** Settings screen. */
    object SETTINGS : MotoDestination("settings", R.string.nav_settings, R.string.screen_settings, Icons.Filled.Settings)

    /**
     * Route detail screen. Not in the bottom nav; reached by tapping a route
     * card. Shows a back arrow in the top bar instead of the bottom nav.
     *
     * The route pattern carries a `{routeId}` nav argument; navigate via
     * `"route_detail/$routeId"` and declare the argument with [NavType.StringType].
     */
    object ROUTE_DETAIL : MotoDestination("route_detail/{routeId}", R.string.nav_route_detail, R.string.screen_route_detail, Icons.Filled.Map)

    /**
     * Bike detail screen (E2). Not in the bottom nav; reached from Settings → Motorcycles
     * by tapping the detail icon on a bike row. Shows a back arrow in the top bar.
     *
     * The route pattern carries a `{bikeId}` nav argument; navigate via
     * `"bike_detail/$bikeId"` and declare the argument with [NavType.StringType].
     */
    object BIKE_DETAIL : MotoDestination("bike_detail/{bikeId}", R.string.nav_bike_detail, R.string.screen_bike_detail, Icons.Filled.TwoWheeler)

    /**
     * Help screen (J1). Not in the bottom nav; reached from Settings → Preferences → Help.
     * Shows a back arrow in the top bar.
     */
    object HELP : MotoDestination("help", R.string.nav_help, R.string.screen_help, Icons.Filled.Info)

    companion object {
        /**
         * Resolves a [MotoDestination] from a NavBackStackEntry route string.
         * Falls back to [RECORD] when the route is null or unrecognised.
         *
         * Uses string literal constants (not object property references) to avoid
         * Kotlin companion-object / nested-object initialization ordering issues on
         * the JVM, where the companion `<clinit>` can run before nested objects are
         * fully initialized.
         */
        fun fromRoute(route: String?): MotoDestination = when {
            route == "login"                          -> LOGIN
            route == "record"                         -> RECORD
            route == "routes"                         -> ROUTES
            route == "riders"                         -> RIDERS
            route == "stats"                          -> STATS
            route == "settings"                       -> SETTINGS
            route?.startsWith("route_detail") == true -> ROUTE_DETAIL
            route?.startsWith("bike_detail") == true  -> BIKE_DETAIL
            route == "help"                           -> HELP
            else                                      -> RECORD
        }
    }
}

/**
 * The five destinations shown in the bottom navigation bar, in display order:
 * Record · Routes · Riders · Stats · Settings.
 */
val bottomNavDestinations: List<MotoDestination> = listOf(
    MotoDestination.RECORD,
    MotoDestination.ROUTES,
    MotoDestination.RIDERS,
    MotoDestination.STATS,
    MotoDestination.SETTINGS,
)

/**
 * Returns `true` when the bottom navigation bar should be visible for [dest].
 *
 * Per spec (README §Stała rama): the bottom nav is hidden on the login screen
 * and on the route-detail screen.
 */
fun showBottomBar(dest: MotoDestination): Boolean =
    dest != MotoDestination.LOGIN &&
        dest != MotoDestination.ROUTE_DETAIL &&
        dest != MotoDestination.BIKE_DETAIL &&
        dest != MotoDestination.HELP

/**
 * Returns `true` when the top app bar should be visible for [dest].
 *
 * Per spec (README §Stała rama): the top bar is hidden on the login screen only.
 */
fun showTopBar(dest: MotoDestination): Boolean =
    dest != MotoDestination.LOGIN

/**
 * Returns `true` when the top bar should show a back arrow instead of nothing
 * in the navigation-icon slot.
 *
 * Per spec: only [MotoDestination.ROUTE_DETAIL] uses a back arrow.
 */
fun showBackArrow(dest: MotoDestination): Boolean =
    dest == MotoDestination.ROUTE_DETAIL || dest == MotoDestination.BIKE_DETAIL || dest == MotoDestination.HELP

/**
 * Returns `true` when navigation should be locked to the Record screen.
 *
 * Navigation is locked whenever [phase] is not [RecordingPhase.Idle] (i.e. the
 * ride is actively recording or paused) to prevent leaving the nav-scoped
 * [com.mototracker.ui.screens.record.RecordingViewModel] mid-session.
 *
 * @param phase The current recording phase reported by [com.mototracker.car.CarRecordingBridge].
 */
fun isRecordingLocked(phase: RecordingPhase): Boolean = phase != RecordingPhase.Idle

/**
 * Returns `true` when [dest] should be interactive in the bottom navigation bar.
 *
 * While a recording is active only [MotoDestination.RECORD] remains enabled;
 * all other tabs are greyed-out and non-tappable. When no recording is active
 * every destination is enabled.
 *
 * @param dest            The candidate navigation destination.
 * @param recordingActive Whether a recording session is currently active (non-Idle).
 */
fun bottomNavItemEnabled(dest: MotoDestination, recordingActive: Boolean): Boolean =
    !recordingActive || dest == MotoDestination.RECORD
