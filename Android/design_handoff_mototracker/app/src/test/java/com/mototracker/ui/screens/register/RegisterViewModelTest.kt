package com.mototracker.ui.screens.register

import app.cash.turbine.test
import com.mototracker.R
import com.mototracker.data.network.EmailTakenException
import com.mototracker.data.network.GpStrackClient
import com.mototracker.data.network.InvalidRegistrationException
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

private class FakeRegisterSettingsSource(
    initialAddress: String = "http://192.168.1.145/gpstrack",
) : WritableSettingsSource {
    val setServerAddressCalls = mutableListOf<String>()
    private val _settings = MutableStateFlow(AppSettings(serverAddress = initialAddress))
    override val settings: Flow<AppSettings> = _settings
    override suspend fun setServerAddress(address: String) {
        setServerAddressCalls += address
    }
}

private class FakeRegisterGpStrackClient(
    private val registerResult: Result<Unit> = Result.success(Unit),
) : GpStrackClient {
    var registerCalls = 0

    override suspend fun login(serverAddress: String, email: String, password: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun register(serverAddress: String, email: String, password: String): Result<Unit> {
        registerCalls++
        return registerResult
    }

    override suspend fun uploadRoute(
        serverAddress: String,
        route: com.mototracker.data.model.Route,
    ): Result<Unit> = Result.success(Unit)
}

private class FakeRegisterSyncRepository : SyncRepository {
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
// Helpers
// ---------------------------------------------------------------------------

private const val VALID_SERVER = "http://192.168.1.145/gpstrack"
private const val VALID_EMAIL = "rider@example.com"
private const val VALID_PASSWORD = "securepassword"

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

/**
 * Unit tests for [RegisterViewModel] covering canSubmit computation, event emission,
 * error mapping, and form persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeSettings: FakeRegisterSettingsSource
    private lateinit var fakeClient: FakeRegisterGpStrackClient
    private lateinit var fakeSyncRepo: FakeRegisterSyncRepository
    private lateinit var viewModel: RegisterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettings = FakeRegisterSettingsSource(VALID_SERVER)
        fakeClient = FakeRegisterGpStrackClient(Result.success(Unit))
        fakeSyncRepo = FakeRegisterSyncRepository()
        viewModel = RegisterViewModel(fakeSettings, fakeClient, fakeSyncRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel(
        registerResult: Result<Unit> = Result.success(Unit),
        initialAddress: String = VALID_SERVER,
    ): RegisterViewModel {
        val client = FakeRegisterGpStrackClient(registerResult)
        return RegisterViewModel(
            FakeRegisterSettingsSource(initialAddress),
            client,
            FakeRegisterSyncRepository(),
        )
    }

    // ── initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial serverAddress loads from settings`() {
        assertEquals(VALID_SERVER, viewModel.uiState.value.serverAddress)
    }

    @Test
    fun `initial canSubmit is false because email and password are blank`() {
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `initial loading is false`() {
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun `initial errorMessage is null`() {
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // ── canSubmit validation ──────────────────────────────────────────────────

    @Test
    fun `canSubmit is true when all fields are valid`() {
        viewModel.updateServerAddress(VALID_SERVER)
        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword(VALID_PASSWORD)
        viewModel.updateConfirm(VALID_PASSWORD)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit is false when email is invalid`() {
        viewModel.updateServerAddress(VALID_SERVER)
        viewModel.updateEmail("notanemail")
        viewModel.updatePassword(VALID_PASSWORD)
        viewModel.updateConfirm(VALID_PASSWORD)
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit is false when email is blank`() {
        viewModel.updateServerAddress(VALID_SERVER)
        viewModel.updateEmail("")
        viewModel.updatePassword(VALID_PASSWORD)
        viewModel.updateConfirm(VALID_PASSWORD)
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit is false when password shorter than 8 chars`() {
        viewModel.updateServerAddress(VALID_SERVER)
        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword("short")
        viewModel.updateConfirm("short")
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit is false when confirm does not match password`() {
        viewModel.updateServerAddress(VALID_SERVER)
        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword(VALID_PASSWORD)
        viewModel.updateConfirm("differentpassword")
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit is false when serverAddress is blank`() {
        viewModel.updateServerAddress("   ")
        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword(VALID_PASSWORD)
        viewModel.updateConfirm(VALID_PASSWORD)
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit is false when serverAddress is empty`() {
        viewModel.updateServerAddress("")
        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword(VALID_PASSWORD)
        viewModel.updateConfirm(VALID_PASSWORD)
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `password exactly 8 chars long satisfies length requirement`() {
        viewModel.updateServerAddress(VALID_SERVER)
        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword("12345678")
        viewModel.updateConfirm("12345678")
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    // ── field updates clear errorMessage ──────────────────────────────────────

    @Test
    fun `updateEmail clears errorMessage`() = runTest {
        val vm = makeViewModel(registerResult = Result.failure(RuntimeException()))
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)
        vm.register()
        assertNotNull(vm.uiState.value.errorMessage)
        vm.updateEmail("new@email.com")
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `updatePassword clears errorMessage`() = runTest {
        val vm = makeViewModel(registerResult = Result.failure(RuntimeException()))
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)
        vm.register()
        assertNotNull(vm.uiState.value.errorMessage)
        vm.updatePassword("newpassword")
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `updateConfirm clears errorMessage`() = runTest {
        val vm = makeViewModel(registerResult = Result.failure(RuntimeException()))
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)
        vm.register()
        assertNotNull(vm.uiState.value.errorMessage)
        vm.updateConfirm(VALID_PASSWORD)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun `updateServerAddress clears errorMessage`() = runTest {
        val vm = makeViewModel(registerResult = Result.failure(RuntimeException()))
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)
        vm.register()
        assertNotNull(vm.uiState.value.errorMessage)
        vm.updateServerAddress("http://other.server/")
        assertNull(vm.uiState.value.errorMessage)
    }

    // ── register() success path ───────────────────────────────────────────────

    @Test
    fun `register emits NavigateToMain with authed=true on success`() = runTest {
        viewModel.updateServerAddress(VALID_SERVER)
        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword(VALID_PASSWORD)
        viewModel.updateConfirm(VALID_PASSWORD)

        viewModel.events.test {
            viewModel.register()
            val event = awaitItem()
            assertTrue(event is RegisterEvent.NavigateToMain && event.authed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `register calls syncNow on success`() = runTest {
        val sharedSync = FakeRegisterSyncRepository()
        val vm = RegisterViewModel(
            FakeRegisterSettingsSource(VALID_SERVER),
            FakeRegisterGpStrackClient(Result.success(Unit)),
            sharedSync,
        )
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)

        vm.events.test {
            vm.register()
            awaitItem()
            assertEquals(1, sharedSync.syncNowCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `register navigates even when syncNow throws`() = runTest {
        fakeSyncRepo.syncNowResult = Result.failure(RuntimeException("network gone"))
        viewModel.updateServerAddress(VALID_SERVER)
        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword(VALID_PASSWORD)
        viewModel.updateConfirm(VALID_PASSWORD)

        viewModel.events.test {
            viewModel.register()
            val event = awaitItem()
            assertTrue(event is RegisterEvent.NavigateToMain && event.authed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `register sets loading=false and errorMessage=null on success`() = runTest {
        viewModel.updateServerAddress(VALID_SERVER)
        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword(VALID_PASSWORD)
        viewModel.updateConfirm(VALID_PASSWORD)

        viewModel.events.test {
            viewModel.register()
            awaitItem()
            assertFalse(viewModel.uiState.value.loading)
            assertNull(viewModel.uiState.value.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `register persists trimmed serverAddress`() = runTest {
        val settings = FakeRegisterSettingsSource()
        val vm = RegisterViewModel(settings, FakeRegisterGpStrackClient(), FakeRegisterSyncRepository())
        vm.updateServerAddress("  http://test.server/  ")
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)

        vm.events.test {
            vm.register()
            awaitItem()
            assertEquals(listOf("http://test.server/"), settings.setServerAddressCalls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── register() failure paths ──────────────────────────────────────────────

    @Test
    fun `register on EmailTakenException sets errorMessage=register_error_email_taken`() = runTest {
        val vm = makeViewModel(registerResult = Result.failure(EmailTakenException()))
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)
        vm.register()
        assertEquals(R.string.register_error_email_taken, vm.uiState.value.errorMessage)
    }

    @Test
    fun `register on InvalidRegistrationException sets errorMessage=register_error_invalid_input`() = runTest {
        val vm = makeViewModel(registerResult = Result.failure(InvalidRegistrationException()))
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)
        vm.register()
        assertEquals(R.string.register_error_invalid_input, vm.uiState.value.errorMessage)
    }

    @Test
    fun `register on generic error sets errorMessage=register_error_network`() = runTest {
        val vm = makeViewModel(registerResult = Result.failure(RuntimeException("timeout")))
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)
        vm.register()
        assertEquals(R.string.register_error_network, vm.uiState.value.errorMessage)
    }

    @Test
    fun `register does NOT emit NavigateToMain on failure`() = runTest {
        val vm = makeViewModel(registerResult = Result.failure(RuntimeException("timeout")))
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)
        vm.events.test {
            vm.register()
            expectNoEvents()
        }
    }

    @Test
    fun `register sets loading=false on failure`() = runTest {
        val vm = makeViewModel(registerResult = Result.failure(RuntimeException("timeout")))
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)
        vm.register()
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun `register leaves form editable on failure when serverAddress is non-blank`() = runTest {
        val vm = makeViewModel(registerResult = Result.failure(RuntimeException("timeout")))
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)
        vm.register()
        assertTrue("canSubmit must be restored on failure", vm.uiState.value.canSubmit)
    }

    @Test
    fun `register does not call syncNow on failure`() = runTest {
        val sharedSync = FakeRegisterSyncRepository()
        val vm = RegisterViewModel(
            FakeRegisterSettingsSource(VALID_SERVER),
            FakeRegisterGpStrackClient(Result.failure(RuntimeException())),
            sharedSync,
        )
        vm.updateServerAddress(VALID_SERVER)
        vm.updateEmail(VALID_EMAIL)
        vm.updatePassword(VALID_PASSWORD)
        vm.updateConfirm(VALID_PASSWORD)
        vm.register()
        assertEquals(0, sharedSync.syncNowCalls)
    }

    // ── navigateBackToLogin ───────────────────────────────────────────────────

    @Test
    fun `navigateBackToLogin emits NavigateBackToLogin event`() = runTest {
        viewModel.events.test {
            viewModel.navigateBackToLogin()
            val event = awaitItem()
            assertTrue(event is RegisterEvent.NavigateBackToLogin)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
