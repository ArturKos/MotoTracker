package com.mototracker.ui.state

import app.cash.turbine.test
import com.mototracker.core.i18n.LocaleController
import com.mototracker.ui.theme.AccentColor
import com.mototracker.ui.theme.MotoTheme
import kotlinx.coroutines.test.runTest
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
    private lateinit var viewModel: AppStateViewModel

    @Before
    fun setUp() {
        fakeLocale = FakeLocaleController()
        viewModel = AppStateViewModel(fakeLocale)
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
}
