package com.mototracker.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mototracker.ui.screens.detail.RouteDetailContent
import com.mototracker.ui.screens.login.LoginContent
import com.mototracker.ui.screens.record.RecordingContent
import com.mototracker.ui.screens.riders.RidersContent
import com.mototracker.ui.screens.routes.RoutesContent
import com.mototracker.ui.screens.settings.SettingsContent
import com.mototracker.ui.screens.stats.StatsContent
import com.mototracker.ui.theme.AccentColor
import com.mototracker.ui.theme.MotoTheme
import com.mototracker.ui.theme.MotoTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshot tests for all 7 key screens rendered via Roborazzi + Robolectric.
 *
 * Snapshot matrix:
 * - All 7 screens in [MotoTheme.COCKPIT] (populated state)
 * - [RecordingContent], [RoutesContent], [SettingsContent] in [MotoTheme.GRID] and [MotoTheme.LIGHT]
 * - Empty-state snapshots for [RoutesContent] and [StatsContent]
 *
 * Baselines live in `app/src/test/snapshots/`. Record with `./gradlew recordRoborazziDebug`;
 * verify with `./gradlew verifyRoborazziDebug`.
 *
 * Map slots ([RecordingContent.mapSlot] and [RouteDetailContent.mapSlot]) are left as empty
 * lambdas — map tile rendering is on-device only and out of headless scope.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun snapshot(name: String) {
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    fun login_cockpit_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                LoginContent(uiState = ScreenshotFixtures.loginPopulated)
            }
        }
        snapshot("login_cockpit_populated")
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    @Test
    fun record_cockpit_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                RecordingContent(state = ScreenshotFixtures.recordingPopulated)
            }
        }
        snapshot("record_cockpit_populated")
    }

    @Test
    fun record_grid_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.GRID, accent = AccentColor.TEAL) {
                RecordingContent(state = ScreenshotFixtures.recordingPopulated)
            }
        }
        snapshot("record_grid_populated")
    }

    @Test
    fun record_light_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.LIGHT, accent = AccentColor.TEAL) {
                RecordingContent(state = ScreenshotFixtures.recordingPopulated)
            }
        }
        snapshot("record_light_populated")
    }

    // ── Routes ────────────────────────────────────────────────────────────────

    @Test
    fun routes_cockpit_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                RoutesContent(state = ScreenshotFixtures.routesPopulated)
            }
        }
        snapshot("routes_cockpit_populated")
    }

    @Test
    fun routes_cockpit_empty() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                RoutesContent(state = ScreenshotFixtures.routesEmpty)
            }
        }
        snapshot("routes_cockpit_empty")
    }

    @Test
    fun routes_grid_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.GRID, accent = AccentColor.TEAL) {
                RoutesContent(state = ScreenshotFixtures.routesPopulated)
            }
        }
        snapshot("routes_grid_populated")
    }

    @Test
    fun routes_light_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.LIGHT, accent = AccentColor.TEAL) {
                RoutesContent(state = ScreenshotFixtures.routesPopulated)
            }
        }
        snapshot("routes_light_populated")
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Test
    fun stats_cockpit_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                StatsContent(state = ScreenshotFixtures.statsPopulated)
            }
        }
        snapshot("stats_cockpit_populated")
    }

    @Test
    fun stats_cockpit_empty() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                StatsContent(state = ScreenshotFixtures.statsEmpty)
            }
        }
        snapshot("stats_cockpit_empty")
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h3000dp-xxhdpi")
    fun settings_cockpit_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                SettingsContent(
                    state = ScreenshotFixtures.settingsPopulated,
                    authed = true,
                )
            }
        }
        snapshot("settings_cockpit_populated")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h3000dp-xxhdpi")
    fun settings_grid_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.GRID, accent = AccentColor.TEAL) {
                SettingsContent(
                    state = ScreenshotFixtures.settingsPopulated,
                    authed = true,
                )
            }
        }
        snapshot("settings_grid_populated")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h3000dp-xxhdpi")
    fun settings_light_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.LIGHT, accent = AccentColor.TEAL) {
                SettingsContent(
                    state = ScreenshotFixtures.settingsPopulated,
                    authed = true,
                )
            }
        }
        snapshot("settings_light_populated")
    }

    // ── Riders ────────────────────────────────────────────────────────────────

    @Test
    fun riders_cockpit_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                RidersContent(
                    state = ScreenshotFixtures.ridersPopulated,
                    wavesEnabled = true,
                )
            }
        }
        snapshot("riders_cockpit_populated")
    }

    // ── Route Detail ──────────────────────────────────────────────────────────

    @Test
    fun detail_cockpit_populated() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                RouteDetailContent(state = ScreenshotFixtures.routeDetailPopulated)
            }
        }
        snapshot("detail_cockpit_populated")
    }
}
