package com.mototracker.ui.screenshot

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mototracker.R
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
 * The [RouteDetailContent.mapSlot] is left as an empty lambda — map tile rendering is
 * on-device only and out of headless scope.
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

    /** Captures the dialog root (second root node) for fullscreen-overlay tests. */
    private fun snapshotDialog(name: String) {
        composeRule.onAllNodes(isRoot())[1].captureRoboImage("src/test/snapshots/$name.png")
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

    /**
     * Anti-clipping regression guard (G2 — revised from F1).
     *
     * Renders [RecordingContent] in Idle state on a P20-class short screen
     * (w411dp × h781dp / 1080×2280, xxhdpi).
     *
     * G2 rule: fill-to-full is only visible during Recording/Paused when a tank is configured —
     * it must NOT appear in Idle. The start control must still be on-screen.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h781dp-xxhdpi")
    fun record_idle_controls_always_visible() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val startLabel = ctx.getString(R.string.btn_start_ride)
        val fillLabel = ctx.getString(R.string.action_fill_to_full)

        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                RecordingContent(state = ScreenshotFixtures.recordingIdle)
            }
        }

        // Start control must be visible on a 781dp-tall screen.
        composeRule.onNodeWithContentDescription(startLabel).assertIsDisplayed()
        // Fill-to-full must NOT appear in Idle (G2).
        composeRule.onNodeWithContentDescription(fillLabel).assertDoesNotExist()

        snapshot("record_idle_controls_visible")
    }

    /**
     * Recording-phase control-strip guard (G2).
     *
     * Verifies that pause, stop, AND fill-to-full controls are all displayed during an active
     * recording session when the current bike has a tank capacity configured.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h781dp-xxhdpi")
    fun record_recording_fuel_controls_visible() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val pauseLabel = ctx.getString(R.string.btn_pause)
        val finishLabel = ctx.getString(R.string.btn_finish)
        val fillLabel = ctx.getString(R.string.action_fill_to_full)

        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                RecordingContent(state = ScreenshotFixtures.recordingWithFuelTank)
            }
        }

        // All three controls must be visible during Recording with a configured fuel tank.
        composeRule.onNodeWithContentDescription(pauseLabel).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(finishLabel).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(fillLabel).assertIsDisplayed()

        snapshot("record_recording_fuel_controls_visible")
    }

    /**
     * H2 regression guard — Recording phase, no tank configured.
     *
     * Proves FILL_TO_FULL (fuel-pump icon) is always displayed during Recording regardless of
     * whether the bike has a tank capacity set. Would fail if the control were still gated on
     * hasFuelTank. Uses [ScreenshotFixtures.recordingPopulated] which has no tankCapacityL.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
    fun record_recording_fuel_control_always_displayed() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val fillLabel = ctx.getString(R.string.action_fill_to_full)
        val pauseLabel = ctx.getString(R.string.btn_pause)
        val finishLabel = ctx.getString(R.string.btn_finish)

        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                RecordingContent(state = ScreenshotFixtures.recordingPopulated)
            }
        }

        composeRule.onNodeWithContentDescription(pauseLabel).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(finishLabel).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(fillLabel).assertIsDisplayed()

        snapshot("record_recording_fuel_control_always_displayed")
    }

    /**
     * H2 regression guard — Paused phase, no tank configured.
     *
     * Proves FILL_TO_FULL is always displayed in Paused, alongside Resume and Stop, even when
     * [RecordingMetrics.tankCapacityL] is null. Uses [ScreenshotFixtures.recordingPaused].
     */
    @Test
    @Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
    fun record_paused_fuel_control_always_displayed() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val fillLabel = ctx.getString(R.string.action_fill_to_full)
        val resumeLabel = ctx.getString(R.string.btn_resume)
        val finishLabel = ctx.getString(R.string.btn_finish)

        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                RecordingContent(state = ScreenshotFixtures.recordingPaused)
            }
        }

        composeRule.onNodeWithContentDescription(resumeLabel).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(finishLabel).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(fillLabel).assertIsDisplayed()

        snapshot("record_paused_fuel_control_always_displayed")
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

    // ── Route Detail E8: inline map with expand button ────────────────────────

    @Test
    fun detail_cockpit_map_inline() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                RouteDetailContent(
                    state = ScreenshotFixtures.routeDetailPopulated,
                    mapSlot = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color(0xFF1A2030)),
                        )
                    },
                    mapFullscreen = false,
                    onToggleMapFullscreen = {},
                )
            }
        }
        snapshot("detail_cockpit_map_inline")
    }

    @Test
    fun detail_grid_map_inline() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.GRID, accent = AccentColor.TEAL) {
                RouteDetailContent(
                    state = ScreenshotFixtures.routeDetailPopulated,
                    mapSlot = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color(0xFF1A2030)),
                        )
                    },
                    mapFullscreen = false,
                    onToggleMapFullscreen = {},
                )
            }
        }
        snapshot("detail_grid_map_inline")
    }

    @Test
    fun detail_light_map_inline() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.LIGHT, accent = AccentColor.TEAL) {
                RouteDetailContent(
                    state = ScreenshotFixtures.routeDetailPopulated,
                    mapSlot = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color(0xFFCDD8E0)),
                        )
                    },
                    mapFullscreen = false,
                    onToggleMapFullscreen = {},
                )
            }
        }
        snapshot("detail_light_map_inline")
    }

    // ── Route Detail E8: fullscreen map overlay ───────────────────────────────

    @Test
    fun detail_cockpit_map_fullscreen() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.COCKPIT, accent = AccentColor.TEAL) {
                RouteDetailContent(
                    state = ScreenshotFixtures.routeDetailPopulated,
                    mapSlot = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color(0xFF1A2030)),
                        )
                    },
                    fullscreenMapSlot = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1A2030)),
                        )
                    },
                    mapFullscreen = true,
                    onToggleMapFullscreen = {},
                )
            }
        }
        snapshotDialog("detail_cockpit_map_fullscreen")
    }

    @Test
    fun detail_grid_map_fullscreen() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.GRID, accent = AccentColor.TEAL) {
                RouteDetailContent(
                    state = ScreenshotFixtures.routeDetailPopulated,
                    mapSlot = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color(0xFF1A2030)),
                        )
                    },
                    fullscreenMapSlot = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1A2030)),
                        )
                    },
                    mapFullscreen = true,
                    onToggleMapFullscreen = {},
                )
            }
        }
        snapshotDialog("detail_grid_map_fullscreen")
    }

    @Test
    fun detail_light_map_fullscreen() {
        composeRule.setContent {
            MotoTrackerTheme(theme = MotoTheme.LIGHT, accent = AccentColor.TEAL) {
                RouteDetailContent(
                    state = ScreenshotFixtures.routeDetailPopulated,
                    mapSlot = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color(0xFFCDD8E0)),
                        )
                    },
                    fullscreenMapSlot = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFFCDD8E0)),
                        )
                    },
                    mapFullscreen = true,
                    onToggleMapFullscreen = {},
                )
            }
        }
        snapshotDialog("detail_light_map_fullscreen")
    }
}
