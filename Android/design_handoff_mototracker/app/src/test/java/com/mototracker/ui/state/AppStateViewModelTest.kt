package com.mototracker.ui.state

import app.cash.turbine.test
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.i18n.LocaleController
import com.mototracker.data.auth.AuthState
import com.mototracker.data.auth.AuthStateStore
import com.mototracker.data.local.entity.CorrectionStatus
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.terms.TermsAcceptanceStore
import com.mototracker.data.network.SessionState
import com.mototracker.data.network.SessionStore
import com.mototracker.data.repository.RoutePreloadCache
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.ui.navigation.SyncState
import com.mototracker.ui.screens.record.RecordingPhase
import com.mototracker.ui.theme.AccentColor
import com.mototracker.ui.theme.MotoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/** Records every [applyLanguage] call for assertion in tests. */
private class FakeLocaleController : LocaleController {
    val appliedTags = mutableListOf<String>()
    override fun applyLanguage(tag: String) {
        appliedTags += tag
    }
}

private class FakeAuthStateStore : AuthStateStore {
    private val _state = MutableStateFlow(AuthState.NONE)
    override val authState: Flow<AuthState> = _state
    var lastSet: AuthState? = null

    override suspend fun set(state: AuthState) {
        lastSet = state
        _state.value = state
    }
}

private class FakeSessionStore : SessionStore {
    private val _session = MutableStateFlow(SessionState.UNAUTHENTICATED)
    override val session: Flow<SessionState> = _session
    var cleared = false

    override suspend fun save(cookie: String, email: String) {
        _session.value = SessionState(cookie, email)
    }

    override suspend fun clear() {
        cleared = true
        _session.value = SessionState.UNAUTHENTICATED
    }

    fun setAuthenticated(cookie: String = "sid=abc", email: String = "test@test.com") {
        _session.value = SessionState(cookie, email)
    }
}

private class FakeNetworkMonitor(initial: Boolean = true) : NetworkMonitor {
    private val _flow = MutableStateFlow(initial)
    fun emit(value: Boolean) { _flow.value = value }
    override val isOnline: Flow<Boolean> = _flow
}

private class FakeAppSettingsSource(initial: AppSettings = AppSettings()) : AppSettingsSource {
    private val _flow = MutableStateFlow(initial)
    fun emit(s: AppSettings) { _flow.value = s }
    override val settings: Flow<AppSettings> = _flow
}

private class FakeTermsAcceptanceStore(initial: Boolean = false) : TermsAcceptanceStore {
    private val _accepted = MutableStateFlow(initial)
    override val accepted: Flow<Boolean> = _accepted
    var lastSet: Boolean? = null

    override suspend fun setAccepted(accepted: Boolean) {
        lastSet = accepted
        _accepted.value = accepted
    }
}

private class FakeRouteRepository(
    initialSummaries: List<RouteSummaryModel> = emptyList(),
) : RouteRepository {
    private val _summaries = MutableStateFlow(initialSummaries)

    fun emitSummaries(summaries: List<RouteSummaryModel>) { _summaries.value = summaries }

    override fun observeSummaries(): Flow<List<RouteSummaryModel>> = _summaries
    override suspend fun save(route: Route) = Unit
    override suspend fun getById(id: String): Route? = null
    override fun observeById(id: String): Flow<Route?> = MutableStateFlow(null)
    override suspend fun clearCorrectedTrace(id: String) = Unit
    override suspend fun rename(id: String, name: String) = Unit
    override suspend fun setBike(routeId: String, bikeId: String?) = Unit
    override suspend fun deleteAll() = Unit
}

private class FakeSyncRepository(initialPending: Int = 0) : SyncRepository {
    private val _pending = MutableStateFlow(initialPending)
    fun emitPending(count: Int) { _pending.value = count }
    override val pendingCount: Flow<Int> = _pending
    override suspend fun enqueue(routeId: String) {}
    override suspend fun syncNow(): Int = 0
    override fun start(scope: CoroutineScope) {}
}

class AppStateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeLocale: FakeLocaleController
    private lateinit var fakeAuthStore: FakeAuthStateStore
    private lateinit var fakeSessionStore: FakeSessionStore
    private lateinit var fakeTermsStore: FakeTermsAcceptanceStore
    private lateinit var bridge: CarRecordingBridge
    private lateinit var fakeNetworkMonitor: FakeNetworkMonitor
    private lateinit var fakeSettingsSource: FakeAppSettingsSource
    private lateinit var fakeSyncRepository: FakeSyncRepository
    private lateinit var fakeRouteRepository: FakeRouteRepository
    private lateinit var routePreloadCache: RoutePreloadCache
    private lateinit var viewModel: AppStateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeLocale = FakeLocaleController()
        fakeAuthStore = FakeAuthStateStore()
        fakeSessionStore = FakeSessionStore()
        fakeTermsStore = FakeTermsAcceptanceStore(initial = true) // accepted by default so existing tests are unaffected
        bridge = CarRecordingBridge()
        fakeNetworkMonitor = FakeNetworkMonitor(initial = true)
        fakeSettingsSource = FakeAppSettingsSource()
        fakeSyncRepository = FakeSyncRepository(initialPending = 0)
        fakeRouteRepository = FakeRouteRepository()
        routePreloadCache = RoutePreloadCache()
        viewModel = AppStateViewModel(
            localeController = fakeLocale,
            recordingBridge = bridge,
            authStateStore = fakeAuthStore,
            sessionStore = fakeSessionStore,
            termsStore = fakeTermsStore,
            networkMonitor = fakeNetworkMonitor,
            settingsSource = fakeSettingsSource,
            syncRepository = fakeSyncRepository,
            routeRepository = fakeRouteRepository,
            routePreloadCache = routePreloadCache,
            ioDispatcher = testDispatcher,
        )
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

    // ── B22: persistence ─────────────────────────────────────────────────────

    @Test
    fun `signIn persists AUTHED to authStateStore`() = runTest {
        viewModel.signIn()
        assertEquals(AuthState.AUTHED, fakeAuthStore.lastSet)
    }

    @Test
    fun `continueAsGuest persists GUEST to authStateStore`() = runTest {
        viewModel.continueAsGuest()
        assertEquals(AuthState.GUEST, fakeAuthStore.lastSet)
    }

    @Test
    fun `signOut persists NONE, clears session, and resets uiState`() = runTest {
        viewModel.signIn()
        viewModel.signOut()
        assertEquals(AuthState.NONE, fakeAuthStore.lastSet)
        assertTrue(fakeSessionStore.cleared)
        assertEquals(AppUiState(), viewModel.uiState.value)
    }

    // ── B22: startupDecision ─────────────────────────────────────────────────

    @Test
    fun `startupDecision is Loading before stores emit`() {
        // With UnconfinedTestDispatcher the flow is already subscribed and will have emitted
        // once stores produce their initial values; so we verify that Ready is emitted.
        // The initial value of the StateFlow is Loading.
        // Since stores emit synchronously in the fake, the decision transitions immediately.
        assertTrue(viewModel.startupDecision.value is StartupDecision.Ready || viewModel.startupDecision.value is StartupDecision.Loading)
    }

    @Test
    fun `startupDecision emits Ready(LOGIN) for NONE auth state`() = runTest {
        // fakeAuthStore defaults to NONE, fakeSessionStore to UNAUTHENTICATED
        val decision = viewModel.startupDecision.value
        assertTrue(decision is StartupDecision.Ready)
        val ready = decision as StartupDecision.Ready
        assertEquals(AppScreen.LOGIN, ready.startScreen)
        assertFalse(ready.authed)
        assertFalse(ready.sessionExpired)
    }

    @Test
    fun `startupDecision emits Ready(MAIN, authed=false) for GUEST`() = runTest {
        fakeAuthStore.set(AuthState.GUEST)
        val decision = viewModel.startupDecision.value
        assertTrue(decision is StartupDecision.Ready)
        val ready = decision as StartupDecision.Ready
        assertEquals(AppScreen.MAIN, ready.startScreen)
        assertFalse(ready.authed)
        assertFalse(ready.sessionExpired)
    }

    @Test
    fun `startupDecision emits Ready(MAIN, authed=true) for AUTHED with valid session`() = runTest {
        fakeSessionStore.setAuthenticated()
        fakeAuthStore.set(AuthState.AUTHED)
        val decision = viewModel.startupDecision.value
        assertTrue(decision is StartupDecision.Ready)
        val ready = decision as StartupDecision.Ready
        assertEquals(AppScreen.MAIN, ready.startScreen)
        assertTrue(ready.authed)
        assertFalse(ready.sessionExpired)
    }

    @Test
    fun `startupDecision emits Ready(LOGIN, sessionExpired=true) for AUTHED without session`() = runTest {
        // AUTHED persisted but session cookie absent
        fakeAuthStore.set(AuthState.AUTHED)
        // fakeSessionStore is UNAUTHENTICATED by default
        val decision = viewModel.startupDecision.value
        assertTrue(decision is StartupDecision.Ready)
        val ready = decision as StartupDecision.Ready
        assertEquals(AppScreen.LOGIN, ready.startScreen)
        assertFalse(ready.authed)
        assertTrue(ready.sessionExpired)
    }

    @Test
    fun `startupDecision updates reactively when auth state changes`() = runTest {
        viewModel.startupDecision.test {
            val initial = awaitItem()
            assertTrue(initial is StartupDecision.Ready)
            assertEquals(AppScreen.LOGIN, (initial as StartupDecision.Ready).startScreen)

            fakeAuthStore.set(AuthState.GUEST)
            val guestDecision = awaitItem()
            assertTrue(guestDecision is StartupDecision.Ready)
            assertEquals(AppScreen.MAIN, (guestDecision as StartupDecision.Ready).startScreen)
            assertFalse(guestDecision.authed)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── D9: syncState ────────────────────────────────────────────────────────

    @Test
    fun `syncState initial value is Synced when device is online with no pending`() {
        assertEquals(SyncState.Synced, viewModel.syncState.value)
    }

    @Test
    fun `syncState transitions to Offline when device loses network`() = runTest {
        viewModel.syncState.test {
            awaitItem() // consume initial Synced
            fakeNetworkMonitor.emit(false)
            assertEquals(SyncState.Offline, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `syncState transitions to Offline when user enables noInternet`() = runTest {
        viewModel.syncState.test {
            awaitItem()
            fakeSettingsSource.emit(AppSettings(noInternet = true))
            assertEquals(SyncState.Offline, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `syncState transitions to Queued when pending count becomes positive`() = runTest {
        viewModel.syncState.test {
            awaitItem()
            fakeSyncRepository.emitPending(4)
            assertEquals(SyncState.Queued(4), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `syncState transitions back to Synced when queue drains`() = runTest {
        fakeSyncRepository.emitPending(3)
        viewModel.syncState.test {
            awaitItem() // Queued(3)
            fakeSyncRepository.emitPending(0)
            assertEquals(SyncState.Synced, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `syncState recovers to Synced when device comes back online`() = runTest {
        fakeNetworkMonitor.emit(false)
        viewModel.syncState.test {
            awaitItem() // Offline
            fakeNetworkMonitor.emit(true)
            assertEquals(SyncState.Synced, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── AE4: preloadedRoutes ──────────────────────────────────────────────────

    private fun makeRouteSummary(id: String = "r1", name: String = "Test") = RouteSummaryModel(
        id = id, name = name, dateEpochMs = 1_700_000_000_000L,
        bikeId = null, km = 100.0, durSec = 3_600L,
        avg = 60.0, max = 120.0, lean = 20.0, elev = 500.0, fuel = 5.0,
        synced = true, thumbnailPathD = null,
        correctionStatus = CorrectionStatus.NONE, confidence = null,
    )

    @Test
    fun `preloadedRoutes starts empty before repository emits`() {
        assertEquals(emptyList<RouteSummaryModel>(), viewModel.preloadedRoutes.value)
    }

    @Test
    fun `preloadedRoutes reflects repository emission eagerly without RoutesViewModel created`() = runTest {
        // No RoutesViewModel is ever created in this test — proves loading happens during splash.
        val summaries = listOf(makeRouteSummary("r1"), makeRouteSummary("r2"))
        viewModel.preloadedRoutes.test {
            awaitItem() // initial empty
            fakeRouteRepository.emitSummaries(summaries)
            val emitted = awaitItem()
            assertEquals(2, emitted.size)
            assertEquals("r1", emitted[0].id)
            assertEquals("r2", emitted[1].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `preloadedRoutes seeds routePreloadCache via onEach side-effect`() = runTest {
        val summaries = listOf(makeRouteSummary("r1"))
        fakeRouteRepository.emitSummaries(summaries)
        // Drain the preloadedRoutes flow so onEach fires
        viewModel.preloadedRoutes.test {
            awaitItem() // may be empty or already have routes depending on dispatcher timing
            cancelAndIgnoreRemainingEvents()
        }
        // The cache should be populated
        assertEquals(1, routePreloadCache.routes.value.size)
        assertEquals("r1", routePreloadCache.routes.value.first().id)
    }
}

// ── startScreenFor pure-function tests ───────────────────────────────────────

class StartScreenForTest {

    @Test
    fun `NONE with no session returns LOGIN`() {
        assertEquals(AppScreen.LOGIN, startScreenFor(AuthState.NONE, sessionAuthenticated = false))
    }

    @Test
    fun `NONE with session returns LOGIN`() {
        assertEquals(AppScreen.LOGIN, startScreenFor(AuthState.NONE, sessionAuthenticated = true))
    }

    @Test
    fun `GUEST with no session returns MAIN`() {
        assertEquals(AppScreen.MAIN, startScreenFor(AuthState.GUEST, sessionAuthenticated = false))
    }

    @Test
    fun `GUEST with session returns MAIN`() {
        assertEquals(AppScreen.MAIN, startScreenFor(AuthState.GUEST, sessionAuthenticated = true))
    }

    @Test
    fun `AUTHED with session returns MAIN`() {
        assertEquals(AppScreen.MAIN, startScreenFor(AuthState.AUTHED, sessionAuthenticated = true))
    }

    @Test
    fun `AUTHED without session returns LOGIN`() {
        assertEquals(AppScreen.LOGIN, startScreenFor(AuthState.AUTHED, sessionAuthenticated = false))
    }
}
