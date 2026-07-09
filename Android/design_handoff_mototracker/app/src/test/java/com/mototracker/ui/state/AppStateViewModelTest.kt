package com.mototracker.ui.state

import app.cash.turbine.test
import com.mototracker.ui.theme.AccentColor
import com.mototracker.ui.theme.MotoTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppStateViewModelTest {

    private lateinit var viewModel: AppStateViewModel

    @Before
    fun setUp() {
        viewModel = AppStateViewModel()
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
    fun `setLanguage mutates only language field`() = runTest {
        val before = viewModel.uiState.value
        viewModel.setLanguage(Language.DE)
        val after = viewModel.uiState.value
        assertEquals(Language.DE, after.language)
        assertEquals(before.copy(language = Language.DE), after)
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
}
