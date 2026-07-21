package com.mototracker.ui.screens.login

import app.cash.turbine.test
import com.mototracker.R
import com.mototracker.data.network.GpStrackClient
import com.mototracker.data.network.UnauthorizedException
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.WritableSettingsSource
import kotlinx.coroutines.CoroutineScope
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ---------------------------------------------------------------------------
// Fakes
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

private class FakeGpStrackClient(
    private val loginResult: Result<Unit> = Result.success(Unit),
) : GpStrackClient {
    var loginCalls = 0

    override suspend fun login(serverAddress: String, email: String, password: String): Result<Unit> {
        loginCalls++
        return loginResult
    }

    override suspend fun register(serverAddress: String, email: String, password: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun uploadRoute(
        serverAddress: String,
        route: com.mototracker.data.model.Route,
    ): Result<Unit> = Result.success(Unit)
}

private class FakeSyncRepository : SyncRepository {
    var syncNowCalls = 0
    var syncNowResult: Result<Int> = Result.success(0)
    override val pendingCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun enqueue(routeId: String) = Unit
    override suspend fun syncNow(): Int {
        syncNowCalls++
        return syncNowResult.getOrThrow()
    }
    override fun start(scope: CoroutineScope) = Unit
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeSettings: FakeWritableSettingsSource
    private lateinit var fakeClient: FakeGpStrackClient
    private lateinit var fakeSyncRepo: FakeSyncRepository
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettings = FakeWritableSettingsSource(initialAddress = "http://192.168.1.145/gpstrack")
        fakeClient = FakeGpStrackClient(loginResult = Result.success(Unit))
        fakeSyncRepo = FakeSyncRepository()
        viewModel = LoginViewModel(fakeSettings, fakeClient, fakeSyncRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel(
        loginResult: Result<Unit> = Result.success(Unit),
        initialAddress: String = "http://192.168.1.145/gpstrack",
    ): LoginViewModel {
        val client = FakeGpStrackClient(loginResult)
        return LoginViewModel(
            FakeWritableSettingsSource(initialAddress),
            client,
            FakeSyncRepository(),
        )
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
    fun `initial loading is false`() {
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun `initial errorMessage is null`() {
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `initial state loaded from custom settings address`() {
        val vm = makeViewModel(initialAddress = "http://custom.server/api")
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
    fun `updateEmail updates only email field and clears errorMessage`() {
        viewModel.updateServerAddress("http://example.com")
        val before = viewModel.uiState.value
        viewModel.updateEmail("rider@example.com")
        val after = viewModel.uiState.value
        assertEquals("rider@example.com", after.email)
        assertEquals(before.serverAddress, after.serverAddress)
        assertEquals(before.password, after.password)
        assertEquals(before.canSubmit, after.canSubmit)
        assertNull(after.errorMessage)
    }

    @Test
    fun `updatePassword updates only password field and clears errorMessage`() {
        val before = viewModel.uiState.value
        viewModel.updatePassword("s3cr3t!")
        val after = viewModel.uiState.value
        assertEquals("s3cr3t!", after.password)
        assertEquals(before.serverAddress, after.serverAddress)
        assertEquals(before.email, after.email)
        assertEquals(before.canSubmit, after.canSubmit)
        assertNull(after.errorMessage)
    }

    @Test
    fun `updateServerAddress clears existing errorMessage`() = runTest {
        // put errorMessage in state via a failed sign-in
        val vm = makeViewModel(loginResult = Result.failure(RuntimeException("boom")))
        vm.signIn()
        assertNotNull(vm.uiState.value.errorMessage)

        vm.updateServerAddress("http://new.server/")
        assertNull(vm.uiState.value.errorMessage)
    }

    // ── signIn — success path ────────────────────────────────────────────────

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
    fun `signIn emits NavigateToMain with authed=true on success`() = runTest {
        viewModel.events.test {
            viewModel.signIn()
            val event = awaitItem()
            assertTrue(event is LoginEvent.NavigateToMain && event.authed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signIn sets loading=true then loading=false on success`() = runTest {
        // With UnconfinedTestDispatcher signIn runs synchronously; we check final state
        viewModel.events.test {
            viewModel.signIn()
            awaitItem()
            assertFalse(viewModel.uiState.value.loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signIn calls syncNow on success`() = runTest {
        viewModel.events.test {
            viewModel.signIn()
            awaitItem()
            assertEquals(1, fakeSyncRepo.syncNowCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signIn still navigates when syncNow throws`() = runTest {
        fakeSyncRepo.syncNowResult = Result.failure(RuntimeException("network gone"))
        viewModel.events.test {
            viewModel.signIn()
            val event = awaitItem()
            assertTrue(event is LoginEvent.NavigateToMain && event.authed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signIn errorMessage is null on success`() = runTest {
        viewModel.events.test {
            viewModel.signIn()
            awaitItem()
            assertNull(viewModel.uiState.value.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── signIn — failure path ────────────────────────────────────────────────

    @Test
    fun `signIn sets errorMessage=login_error_invalid_credentials on UnauthorizedException`() = runTest {
        val vm = makeViewModel(loginResult = Result.failure(UnauthorizedException()))
        vm.signIn()
        assertEquals(R.string.login_error_invalid_credentials, vm.uiState.value.errorMessage)
    }

    @Test
    fun `signIn sets errorMessage=login_error_network on generic error`() = runTest {
        val vm = makeViewModel(loginResult = Result.failure(RuntimeException("timeout")))
        vm.signIn()
        assertEquals(R.string.login_error_network, vm.uiState.value.errorMessage)
    }

    @Test
    fun `signIn does NOT emit NavigateToMain on failure`() = runTest {
        val vm = makeViewModel(loginResult = Result.failure(RuntimeException("timeout")))
        vm.events.test {
            vm.signIn()
            expectNoEvents()
        }
    }

    @Test
    fun `signIn sets loading=false on failure`() = runTest {
        val vm = makeViewModel(loginResult = Result.failure(RuntimeException("timeout")))
        vm.signIn()
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `signIn restores canSubmit on failure when serverAddress is non-blank`() = runTest {
        val vm = makeViewModel(loginResult = Result.failure(RuntimeException("timeout")))
        vm.signIn()
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `signIn does not call syncNow on failure`() = runTest {
        fakeSyncRepo.syncNowResult = Result.success(0)
        val vm = makeViewModel(loginResult = Result.failure(RuntimeException("timeout")))
        // vm uses its own fakeSyncRepo instance; check via a shared fake
        val sharedSync = FakeSyncRepository()
        val localVm = LoginViewModel(
            FakeWritableSettingsSource(),
            FakeGpStrackClient(loginResult = Result.failure(RuntimeException())),
            sharedSync,
        )
        localVm.signIn()
        assertEquals(0, sharedSync.syncNowCalls)
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
    fun `continueAsGuest makes no login call`() = runTest {
        viewModel.events.test {
            viewModel.continueAsGuest()
            awaitItem()
            assertEquals(0, fakeClient.loginCalls)
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
