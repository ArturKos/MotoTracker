package com.mototracker.ui.state

import app.cash.turbine.test
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.i18n.LocaleController
import com.mototracker.ui.screens.record.RecordingPhase
import com.mototracker.ui.theme.AccentColor
import com.mototracker.ui.theme.MotoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Records every [applyLanguage] call for assertion in tests. */
private class FakeLocaleController : LocaleController {
    val appliedTags = mutableListOf<String>()
    override fun applyLanguage(tag: String) {
        appliedTags += tag
    }
}

class AppStateViewModelTest {

    private lateinit var fakeLocale: FakeLocaleController
    private lateinit var bridge: CarRecordingBridge
    private lateinit var viewModel: AppStateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeLocale = FakeLocaleController()
        bridge = CarRecordingBridge()
        viewModel = AppStateViewModel(fakeLocale, bridge)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state matches AppUiState defaults`() {
        val state = viewModel.uiState.value
        assertEquals(AppUiState(), state)
        assertEquals(AppScreen.LOGIN, state.screen)
        assertFalse(state.authed)
        assertEquals(Language.PL, state.language)
        assertEquals(MotoTheme.COCKPIT, state.theme)
        assertEquals(AccentColor.TEAL, state.accent)
        assertEquals(Units.METRIC, state.units)
    }

    @Test
    fun `signIn sets authed=true and screen=MAIN`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.signIn()
            val state = awaitItem()
            assertTrue(state.authed)
            assertEquals(AppScreen.MAIN, state.screen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `continueAsGuest sets screen=MAIN and authed=false`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.continueAsGuest()
            val state = awaitItem()
            assertFalse(state.authed)
            assertEquals(AppScreen.MAIN, state.screen)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signOut resets state to defaults`() = runTest {
        viewModel.signIn()
        viewModel.signOut()
        assertEquals(AppUiState(), viewModel.uiState.value)
        assertEquals(AppScreen.LOGIN, viewModel.uiState.value.screen)
        assertFalse(viewModel.uiState.value.authed)
    }

    @Test
    fun `setLanguage updates state and invokes LocaleController with correct tag`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.setLanguage(Language.DE)
            val state = awaitItem()
            assertEquals(Language.DE, state.language)
            assertEquals(listOf("de"), fakeLocale.appliedTags)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setLanguage mutates only language field`() = runTest {
        val before = viewModel.uiState.value
        viewModel.setLanguage(Language.DE)
        val after = viewModel.uiState.value
        assertEquals(Language.DE, after.language)
        assertEquals(before.copy(language = Language.DE), after)
    }

    @Test
    fun `setLanguage calls LocaleController for every Language value`() {
        Language.entries.forEach { lang ->
            fakeLocale.appliedTags.clear()
            viewModel.setLanguage(lang)
            assertEquals(listOf(lang.tag), fakeLocale.appliedTags)
        }
    }

    @Test
    fun `Language tag values match BCP-47`() {
        assertEquals("pl", Language.PL.tag)
        assertEquals("en", Language.EN.tag)
        assertEquals("de", Language.DE.tag)
        assertEquals("fr", Language.FR.tag)
        assertEquals("cs", Language.CS.tag)
        assertEquals("ru", Language.RU.tag)
    }

    @Test
    fun `setTheme mutates only theme field`() = runTest {
        val before = viewModel.uiState.value
        viewModel.setTheme(MotoTheme.GRID)
        val after = viewModel.uiState.value
        assertEquals(MotoTheme.GRID, after.theme)
        assertEquals(before.copy(theme = MotoTheme.GRID), after)
    }

    @Test
    fun `setAccent mutates only accent field`() = runTest {
        val before = viewModel.uiState.value
        viewModel.setAccent(AccentColor.ORANGE)
        val after = viewModel.uiState.value
        assertEquals(AccentColor.ORANGE, after.accent)
        assertEquals(before.copy(accent = AccentColor.ORANGE), after)
    }

    @Test
    fun `setUnits mutates only units field`() = runTest {
        val before = viewModel.uiState.value
        viewModel.setUnits(Units.IMPERIAL)
        val after = viewModel.uiState.value
        assertEquals(Units.IMPERIAL, after.units)
        assertEquals(before.copy(units = Units.IMPERIAL), after)
    }

    // ── recordingActive (D7) ─────────────────────────────────────────────────

    @Test
    fun `recordingActive is false in initial Idle state`() {
        assertFalse(viewModel.recordingActive.value)
    }

    @Test
    fun `recordingActive transitions Idle-false Recording-true Idle-false Paused-true Idle-false`() = runTest {
        // StateFlow is distinctUntilChanged — Paused→true after Recording→true would not re-emit.
        // We therefore cycle back through Idle between the two active phases so every transition
        // produces a distinct boolean emission and can be observed via Turbine.
        viewModel.recordingActive.test {
            assertFalse(awaitItem()) // initial Idle → false

            val fakeMetrics = com.mototracker.domain.recording.RecordingMetrics()

            bridge.publish(fakeMetrics, RecordingPhase.Recording)
            assertTrue(awaitItem()) // Recording → true

            bridge.publish(fakeMetrics, RecordingPhase.Idle)
            assertFalse(awaitItem()) // Idle → false

            bridge.publish(fakeMetrics, RecordingPhase.Paused)
            assertTrue(awaitItem()) // Paused → true

            bridge.publish(fakeMetrics, RecordingPhase.Idle)
            assertFalse(awaitItem()) // back to Idle → false

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `recordingActive emits true for Recording phase`() = runTest {
        val fakeMetrics = com.mototracker.domain.recording.RecordingMetrics()
        bridge.publish(fakeMetrics, RecordingPhase.Recording)
        assertTrue(viewModel.recordingActive.value)
    }

    @Test
    fun `recordingActive emits true for Paused phase`() = runTest {
        val fakeMetrics = com.mototracker.domain.recording.RecordingMetrics()
        bridge.publish(fakeMetrics, RecordingPhase.Paused)
        assertTrue(viewModel.recordingActive.value)
    }
}
