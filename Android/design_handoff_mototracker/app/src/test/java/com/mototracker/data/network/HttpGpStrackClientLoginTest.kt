package com.mototracker.data.network

import app.cash.turbine.test
import com.mototracker.data.model.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeSessionStore : SessionStore {
    private val _session = MutableStateFlow(SessionState.UNAUTHENTICATED)
    override val session: Flow<SessionState> = _session

    override suspend fun save(cookie: String?, email: String, writeApiKey: String?) {
        _session.value = SessionState(cookie = cookie, email = email, writeApiKey = writeApiKey)
    }

    override suspend fun clear() {
        _session.value = SessionState.UNAUTHENTICATED
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private val testRoute = Route(
    id = "route-1",
    name = "Test Route",
    dateEpochMs = 0L,
    bikeId = null,
    km = 10.0,
    durSec = 600L,
    avg = 60.0,
    max = 120.0,
    lean = 30.0,
    elev = 100.0,
    fuel = 1.0,
    synced = false,
    wxJson = null,
    pathJson = null,
    speedJson = null,
    elevProfileJson = null,
    notes = null,
)

private const val SERVER = "http://192.168.1.145/gpstrack"

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Unit tests for [HttpGpStrackClient] login/cookie/401 behaviour.
 *
 * All I/O is replaced by [FakeHttpTransport] (scripted responses) and
 * [FakeSessionStore] (in-memory StateFlow). No real network or DataStore is used.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HttpGpStrackClientLoginTest {

    private lateinit var transport: FakeHttpTransport
    private lateinit var sessionStore: FakeSessionStore
    private lateinit var client: HttpGpStrackClient

    @Before
    fun setUp() {
        transport = FakeHttpTransport()
        sessionStore = FakeSessionStore()
        client = HttpGpStrackClient(transport, sessionStore)
    }

    /**
     * (a) login() must send an `application/x-www-form-urlencoded` body whose decoded
     *     content contains the `email` and `password` field names.
     */
    @Test
    fun `login sends form-urlencoded body with email and password fields`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("PHPSESSID=abc; Path=/; HttpOnly")),
            body = "",
        )

        client.login(SERVER, "user@example.com", "s3cr3t")

        val req = assertNotNull(transport.lastRequest).let { transport.lastRequest!! }
        assertEquals("POST", req.method)
        assertTrue(
            "URL must end with /login.php",
            req.url.endsWith("/login.php"),
        )
        assertEquals(
            "Content-Type must be application/x-www-form-urlencoded",
            "application/x-www-form-urlencoded",
            req.headers["Content-Type"],
        )
        val bodyStr = req.body?.toString(Charsets.UTF_8) ?: ""
        assertTrue("body must contain 'email=' field name", bodyStr.contains("email="))
        assertTrue("body must contain 'password=' field name", bodyStr.contains("password="))
    }

    /**
     * (b) A `Set-Cookie: PHPSESSID=abc; Path=/; HttpOnly` response must result in
     *     exactly `PHPSESSID=abc` being persisted in [SessionStore].
     */
    @Test
    fun `login parses Set-Cookie and persists name=value pair`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("PHPSESSID=abc; Path=/; HttpOnly")),
            body = "",
        )

        val result = client.login(SERVER, "rider@example.com", "pass")

        assertTrue("login() must succeed on 2xx with Set-Cookie", result.isSuccess)
        sessionStore.session.test {
            val state = awaitItem()
            assertEquals("PHPSESSID=abc", state.cookie)
            assertEquals("rider@example.com", state.email)
            assertTrue(state.isAuthenticated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * (c) After a successful login the next uploadRoute() call must include a
     *     `Cookie: PHPSESSID=abc` request header.
     */
    @Test
    fun `uploadRoute attaches Cookie header from persisted session`() = runTest {
        // Seed the session store directly (simulates a prior successful login)
        sessionStore.save("PHPSESSID=abc", "rider@example.com", null)

        transport.nextResponse = HttpResponse(code = 200, headers = emptyMap(), body = "")

        client.uploadRoute(SERVER, testRoute)

        val req = transport.lastRequest!!
        assertTrue(
            "URL must end with /api_routes.php",
            req.url.endsWith("/api_routes.php"),
        )
        assertEquals(
            "Cookie header must carry the persisted session",
            "PHPSESSID=abc",
            req.headers["Cookie"],
        )
    }

    /**
     * (d) A 401 response from uploadRoute() must clear the session (Flow emits
     *     [SessionState.UNAUTHENTICATED]) and return [Result.failure] wrapping
     *     [UnauthorizedException].
     */
    @Test
    fun `uploadRoute on 401 clears session and returns UnauthorizedException`() = runTest {
        sessionStore.save("PHPSESSID=abc", "rider@example.com", null)
        transport.nextResponse = HttpResponse(code = 401, headers = emptyMap(), body = "")

        sessionStore.session.test {
            // Consume the initial authenticated state
            val initial = awaitItem()
            assertTrue("pre-condition: session is authenticated", initial.isAuthenticated)

            val result = client.uploadRoute(SERVER, testRoute)

            assertTrue("uploadRoute must fail on 401", result.isFailure)
            assertTrue(
                "failure cause must be UnauthorizedException",
                result.exceptionOrNull() is UnauthorizedException,
            )

            val cleared = awaitItem()
            assertFalse("session must be cleared after 401", cleared.isAuthenticated)
            assertNull(cleared.cookie)

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * (e) A non-2xx login response must return [Result.failure] and must NOT persist
     *     any cookie in [SessionStore].
     */
    @Test
    fun `login on non-2xx returns failure and does not persist cookie`() = runTest {
        transport.nextResponse = HttpResponse(code = 401, headers = emptyMap(), body = "")

        val result = client.login(SERVER, "user@example.com", "wrong")

        assertTrue("login must fail on non-2xx", result.isFailure)
        sessionStore.session.test {
            val state = awaitItem()
            assertNull("cookie must not be persisted after failed login", state.cookie)
            assertFalse(state.isAuthenticated)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
