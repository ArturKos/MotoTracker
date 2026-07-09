package com.mototracker.ui.screens.login

import app.cash.turbine.test
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.WritableSettingsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

// ---------------------------------------------------------------------------
// Fake
// ---------------------------------------------------------------------------

private class FakeWritableSettingsSource(
    initialAddress: String = "http://192.168.1.145/gpstrack",
) : WritableSettingsSource {
    val setServerAddressCalls = mutableListOf<String>()
    private val _settings = MutableStateFlow(AppSettings(serverAddress = initialAddress))
    override val settings: Flow<AppSettings> = _settings
    override suspend fun setServerAddress(address: String) {
        setServerAddressCalls += address
    }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeSettings: FakeWritableSettingsSource
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettings = FakeWritableSettingsSource(initialAddress = "http://192.168.1.145/gpstrack")
        viewModel = LoginViewModel(fakeSettings)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── initial state ────────────────────────────────────────────────────────

    @Test
    fun `initial serverAddress loads from settings`() {
        assertEquals("http://192.168.1.145/gpstrack", viewModel.uiState.value.serverAddress)
    }

    @Test
    fun `initial canSubmit is true when settings serverAddress is non-blank`() {
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `initial email and password are blank`() {
        assertEquals("", viewModel.uiState.value.email)
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun `initial state loaded from custom settings address`() {
        val customSettings = FakeWritableSettingsSource(initialAddress = "http://custom.server/api")
        val vm = LoginViewModel(customSettings)
        assertEquals("http://custom.server/api", vm.uiState.value.serverAddress)
    }

    // ── field updates ────────────────────────────────────────────────────────

    @Test
    fun `updateServerAddress with non-blank value sets canSubmit=true`() {
        viewModel.updateServerAddress("http://example.com")
        val state = viewModel.uiState.value
        assertEquals("http://example.com", state.serverAddress)
        assertTrue(state.canSubmit)
    }

    @Test
    fun `updateServerAddress with blank string sets canSubmit=false`() {
        viewModel.updateServerAddress("   ")
        val state = viewModel.uiState.value
        assertEquals("   ", state.serverAddress)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `updateServerAddress with empty string sets canSubmit=false`() {
        viewModel.updateServerAddress("")
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `updateEmail updates only email field`() {
        val before = viewModel.uiState.value
        viewModel.updateEmail("rider@example.com")
        val after = viewModel.uiState.value
        assertEquals("rider@example.com", after.email)
        // Other fields unchanged
        assertEquals(before.serverAddress, after.serverAddress)
        assertEquals(before.password, after.password)
        assertEquals(before.canSubmit, after.canSubmit)
    }

    @Test
    fun `updatePassword updates only password field`() {
        val before = viewModel.uiState.value
        viewModel.updatePassword("s3cr3t!")
        val after = viewModel.uiState.value
        assertEquals("s3cr3t!", after.password)
        assertEquals(before.serverAddress, after.serverAddress)
        assertEquals(before.email, after.email)
        assertEquals(before.canSubmit, after.canSubmit)
    }

    // ── signIn ───────────────────────────────────────────────────────────────

    @Test
    fun `signIn persists trimmed serverAddress`() = runTest {
        viewModel.updateServerAddress("  http://test.server/  ")
        viewModel.events.test {
            viewModel.signIn()
            awaitItem()
            assertEquals(listOf("http://test.server/"), fakeSettings.setServerAddressCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signIn emits NavigateToMain with authed=true`() = runTest {
        viewModel.events.test {
            viewModel.signIn()
            val event = awaitItem()
            assertTrue(event is LoginEvent.NavigateToMain && event.authed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── continueAsGuest ──────────────────────────────────────────────────────

    @Test
    fun `continueAsGuest persists trimmed serverAddress`() = runTest {
        viewModel.updateServerAddress("  http://test.server/  ")
        viewModel.events.test {
            viewModel.continueAsGuest()
            awaitItem()
            assertEquals(listOf("http://test.server/"), fakeSettings.setServerAddressCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `continueAsGuest emits NavigateToMain with authed=false`() = runTest {
        viewModel.events.test {
            viewModel.continueAsGuest()
            val event = awaitItem()
            assertTrue(event is LoginEvent.NavigateToMain && !event.authed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signIn and continueAsGuest trim leading and trailing whitespace from serverAddress`() = runTest {
        viewModel.updateServerAddress("\t http://192.168.1.145/gpstrack \n")
        viewModel.events.test {
            viewModel.signIn()
            awaitItem()
            assertEquals("http://192.168.1.145/gpstrack", fakeSettings.setServerAddressCalls.last())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
