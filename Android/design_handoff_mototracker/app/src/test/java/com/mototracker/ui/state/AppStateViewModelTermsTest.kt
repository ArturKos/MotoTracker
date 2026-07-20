package com.mototracker.ui.state

import app.cash.turbine.test
import com.mototracker.car.CarRecordingBridge
import com.mototracker.core.i18n.LocaleController
import com.mototracker.data.auth.AuthState
import com.mototracker.data.auth.AuthStateStore
import com.mototracker.data.model.Route
import com.mototracker.data.model.RouteSummaryModel
import com.mototracker.data.network.NetworkMonitor
import com.mototracker.data.network.SessionState
import com.mototracker.data.network.SessionStore
import com.mototracker.data.repository.RoutePreloadCache
import com.mototracker.data.repository.RouteRepository
import com.mototracker.data.repository.SyncRepository
import com.mototracker.data.settings.AppSettings
import com.mototracker.data.settings.AppSettingsSource
import com.mototracker.data.terms.TermsAcceptanceStore
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

// ---------------------------------------------------------------------------
// Fakes (local to this file; the ones in AppStateViewModelTest.kt are private)
// ---------------------------------------------------------------------------

private class TermsTestLocaleController : LocaleController {
    override fun applyLanguage(tag: String) = Unit
}

private class TermsTestAuthStateStore(initial: AuthState = AuthState.GUEST) : AuthStateStore {
    private val _state = MutableStateFlow(initial)
    override val authState: Flow<AuthState> = _state
    override suspend fun set(state: AuthState) { _state.value = state }
}

private class TermsTestSessionStore : SessionStore {
    private val _session = MutableStateFlow(SessionState.UNAUTHENTICATED)
    override val session: Flow<SessionState> = _session
    override suspend fun save(cookie: String, email: String) { _session.value = SessionState(cookie, email) }
    override suspend fun clear() { _session.value = SessionState.UNAUTHENTICATED }
}

private class TermsTestTermsStore(initial: Boolean = false) : TermsAcceptanceStore {
    private val _accepted = MutableStateFlow(initial)
    override val accepted: Flow<Boolean> = _accepted
    var lastSet: Boolean? = null

    override suspend fun setAccepted(accepted: Boolean) {
        lastSet = accepted
        _accepted.value = accepted
    }
}

private class TermsTestNetworkMonitor : NetworkMonitor {
    override val isOnline: Flow<Boolean> = MutableStateFlow(true)
}

private class TermsTestSettingsSource : AppSettingsSource {
    override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())
}

private class TermsTestSyncRepository : SyncRepository {
    override val pendingCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun enqueue(routeId: String) = Unit
    override suspend fun syncNow(): Int = 0
    override fun start(scope: CoroutineScope) = Unit
}

private class TermsTestRouteRepository : RouteRepository {
    private val _summaries = MutableStateFlow<List<RouteSummaryModel>>(emptyList())
    override fun observeSummaries(): Flow<List<RouteSummaryModel>> = _summaries
    override suspend fun save(route: Route) = Unit
    override suspend fun getById(id: String): Route? = null
    override fun observeById(id: String): Flow<Route?> = MutableStateFlow(null)
    override suspend fun clearCorrectedTrace(id: String) = Unit
    override suspend fun rename(id: String, name: String) = Unit
    override suspend fun setBike(routeId: String, bikeId: String?) = Unit
    override suspend fun deleteAll() = Unit
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

/**
 * Unit tests for the J2 terms acceptance gate wired into [AppStateViewModel].
 *
 * Verifies three scenarios:
 * - terms not accepted → [StartupDecision.Ready.termsAccepted] is `false`
 * - after [AppStateViewModel.acceptTerms] the flag is persisted and re-emitted as `true`
 * - terms already accepted on startup → [StartupDecision.Ready.termsAccepted] is `true`
 */
class AppStateViewModelTermsTest {

    private lateinit var fakeTermsStore: TermsTestTermsStore
    private lateinit var viewModel: AppStateViewModel

    private fun buildViewModel(termsAccepted: Boolean): AppStateViewModel {
        fakeTermsStore = TermsTestTermsStore(initial = termsAccepted)
        return AppStateViewModel(
            localeController = TermsTestLocaleController(),
            recordingBridge = CarRecordingBridge(),
            authStateStore = TermsTestAuthStateStore(),
            sessionStore = TermsTestSessionStore(),
            termsStore = fakeTermsStore,
            networkMonitor = TermsTestNetworkMonitor(),
            settingsSource = TermsTestSettingsSource(),
            syncRepository = TermsTestSyncRepository(),
            routeRepository = TermsTestRouteRepository(),
            routePreloadCache = RoutePreloadCache(),
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** (i) When terms have not been accepted the Ready decision carries termsAccepted=false. */
    @Test
    fun `startupDecision Ready has termsAccepted=false when store emits false`() = runTest {
        viewModel = buildViewModel(termsAccepted = false)
        val decision = viewModel.startupDecision.value
        assertTrue(decision is StartupDecision.Ready)
        assertFalse((decision as StartupDecision.Ready).termsAccepted)
    }

    /**
     * (ii) After acceptTerms() the fake store persists true and the flow re-emits
     * Ready.termsAccepted == true so the gate is no longer shown.
     */
    @Test
    fun `acceptTerms persists true and startupDecision re-emits termsAccepted=true`() = runTest {
        viewModel = buildViewModel(termsAccepted = false)

        viewModel.startupDecision.test {
            val first = awaitItem()
            assertTrue(first is StartupDecision.Ready)
            assertFalse((first as StartupDecision.Ready).termsAccepted)

            viewModel.acceptTerms()

            val second = awaitItem()
            assertTrue(second is StartupDecision.Ready)
            assertTrue((second as StartupDecision.Ready).termsAccepted)

            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(true, fakeTermsStore.lastSet)
    }

    /** (iii) When terms were already accepted the Ready decision carries termsAccepted=true. */
    @Test
    fun `startupDecision Ready has termsAccepted=true when store emits true`() = runTest {
        viewModel = buildViewModel(termsAccepted = true)
        val decision = viewModel.startupDecision.value
        assertTrue(decision is StartupDecision.Ready)
        assertTrue((decision as StartupDecision.Ready).termsAccepted)
    }

    /** acceptTerms() writes true to the backing store exactly once. */
    @Test
    fun `acceptTerms writes true to TermsAcceptanceStore`() = runTest {
        viewModel = buildViewModel(termsAccepted = false)
        viewModel.acceptTerms()
        assertEquals(true, fakeTermsStore.lastSet)
    }
}
