package com.mototracker.data.network

import com.mototracker.data.model.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fake [SessionStore] that records the last values passed to [save] and exposes a
 * [MutableStateFlow] so tests can observe the current [SessionState].
 */
private class RecordingSessionStore : SessionStore {
    private val _session = MutableStateFlow(SessionState.UNAUTHENTICATED)
    override val session: Flow<SessionState> = _session

    var lastSavedCookie: String? = null
    var lastSavedEmail: String? = null
    var lastSavedWriteApiKey: String? = null
    var saveCalls = 0

    override suspend fun save(cookie: String?, email: String, writeApiKey: String?) {
        saveCalls++
        lastSavedCookie = cookie
        lastSavedEmail = email
        lastSavedWriteApiKey = writeApiKey
        _session.value = SessionState(cookie = cookie, email = email, writeApiKey = writeApiKey)
    }

    override suspend fun clear() {
        _session.value = SessionState.UNAUTHENTICATED
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Test helpers
// ─────────────────────────────────────────────────────────────────────────────

private val minimalRoute = Route(
    id = "route-ai1",
    name = "AI1 Test Route",
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
private const val TEST_EMAIL = "rider@example.com"
private const val TEST_PASSWORD = "pass1234"
private const val TEST_WRITE_KEY = "wak_abcdef123456"

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Unit tests verifying that [HttpGpStrackClient] correctly parses and persists
 * `write_api_key` from JSON response bodies and attaches it as an `Authorization: Bearer`
 * header on route uploads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HttpGpStrackClientTest {

    private lateinit var transport: FakeHttpTransport
    private lateinit var sessionStore: RecordingSessionStore
    private lateinit var client: HttpGpStrackClient

    @Before
    fun setUp() {
        transport = FakeHttpTransport()
        sessionStore = RecordingSessionStore()
        client = HttpGpStrackClient(transport, sessionStore)
    }

    // ── login ─────────────────────────────────────────────────────────────────

    /**
     * login() must parse `write_api_key` from a JSON response body and persist it
     * alongside the cookie via [SessionStore.save].
     */
    @Test
    fun `login parses write_api_key from JSON body and persists it`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("PHPSESSID=abc; Path=/; HttpOnly")),
            body = """{"write_api_key":"$TEST_WRITE_KEY","status":"ok"}""",
        )

        val result = client.login(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertTrue("login must succeed", result.isSuccess)
        assertEquals("write_api_key must be persisted", TEST_WRITE_KEY, sessionStore.lastSavedWriteApiKey)
        assertEquals("PHPSESSID=abc", sessionStore.lastSavedCookie)
    }

    /**
     * login() with an empty response body must still succeed; [SessionState.writeApiKey] must be null.
     */
    @Test
    fun `login with empty body succeeds and writeApiKey is null`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("PHPSESSID=abc")),
            body = "",
        )

        val result = client.login(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertTrue("login must succeed", result.isSuccess)
        assertNull("writeApiKey must be null when body is empty", sessionStore.lastSavedWriteApiKey)
    }

    /**
     * login() with a non-JSON body (e.g. plain-text "OK") must still succeed; writeApiKey null.
     */
    @Test
    fun `login with non-JSON body succeeds and writeApiKey is null`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("PHPSESSID=abc")),
            body = "OK",
        )

        val result = client.login(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertTrue("login must succeed with non-JSON body", result.isSuccess)
        assertNull("writeApiKey must be null for non-JSON body", sessionStore.lastSavedWriteApiKey)
    }

    /**
     * login() with JSON body lacking `write_api_key` field must still succeed; writeApiKey null.
     */
    @Test
    fun `login with JSON body missing write_api_key field succeeds with null key`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("PHPSESSID=abc")),
            body = """{"status":"ok","user_id":42}""",
        )

        val result = client.login(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertTrue("login must succeed", result.isSuccess)
        assertNull("writeApiKey must be null when field is absent", sessionStore.lastSavedWriteApiKey)
    }

    // ── register ──────────────────────────────────────────────────────────────

    /**
     * register() must parse `write_api_key` from a JSON body even when no Set-Cookie header
     * is present, and persist via [SessionStore.save].
     */
    @Test
    fun `register parses write_api_key from JSON body without Set-Cookie and persists it`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = emptyMap(),
            body = """{"write_api_key":"$TEST_WRITE_KEY"}""",
        )

        val result = client.register(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertTrue("register must succeed", result.isSuccess)
        assertEquals("save must be called once", 1, sessionStore.saveCalls)
        assertNull("cookie must be null when no Set-Cookie", sessionStore.lastSavedCookie)
        assertEquals("write_api_key must be persisted", TEST_WRITE_KEY, sessionStore.lastSavedWriteApiKey)
    }

    /**
     * register() with both Set-Cookie and write_api_key must persist both.
     */
    @Test
    fun `register persists both cookie and write_api_key when both present`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("PHPSESSID=xyz; Path=/")),
            body = """{"write_api_key":"$TEST_WRITE_KEY"}""",
        )

        client.register(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertEquals("PHPSESSID=xyz", sessionStore.lastSavedCookie)
        assertEquals(TEST_WRITE_KEY, sessionStore.lastSavedWriteApiKey)
        assertEquals("save called once", 1, sessionStore.saveCalls)
    }

    /**
     * register() with non-JSON body and no cookie must succeed without calling save().
     */
    @Test
    fun `register with non-JSON body and no cookie succeeds without persisting`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = emptyMap(),
            body = "Created",
        )

        val result = client.register(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertTrue("register must succeed", result.isSuccess)
        assertEquals("save must not be called", 0, sessionStore.saveCalls)
    }

    // ── uploadRoute ───────────────────────────────────────────────────────────

    /**
     * uploadRoute() must include `Authorization: Bearer <key>` when a write API key is stored.
     */
    @Test
    fun `uploadRoute sends Authorization Bearer header when writeApiKey stored`() = runTest {
        sessionStore.save(null, TEST_EMAIL, TEST_WRITE_KEY)
        transport.nextResponse = HttpResponse(code = 200, headers = emptyMap(), body = "")

        client.uploadRoute(SERVER, minimalRoute)

        val req = transport.lastRequest!!
        assertEquals(
            "Authorization header must be Bearer token",
            "Bearer $TEST_WRITE_KEY",
            req.headers["Authorization"],
        )
    }

    /**
     * uploadRoute() must omit the `Authorization` header when no write API key is stored.
     */
    @Test
    fun `uploadRoute omits Authorization header when no writeApiKey stored`() = runTest {
        sessionStore.save("PHPSESSID=abc", TEST_EMAIL, null)
        transport.nextResponse = HttpResponse(code = 200, headers = emptyMap(), body = "")

        client.uploadRoute(SERVER, minimalRoute)

        val req = transport.lastRequest!!
        assertNull(
            "Authorization header must be absent when no writeApiKey",
            req.headers["Authorization"],
        )
    }

    /**
     * uploadRoute() must send both `Cookie` and `Authorization` headers when both are stored.
     */
    @Test
    fun `uploadRoute sends both Cookie and Authorization headers when both stored`() = runTest {
        sessionStore.save("PHPSESSID=abc", TEST_EMAIL, TEST_WRITE_KEY)
        transport.nextResponse = HttpResponse(code = 200, headers = emptyMap(), body = "")

        client.uploadRoute(SERVER, minimalRoute)

        val req = transport.lastRequest!!
        assertEquals("Cookie header must be present", "PHPSESSID=abc", req.headers["Cookie"])
        assertEquals("Authorization header must be present", "Bearer $TEST_WRITE_KEY", req.headers["Authorization"])
    }

    /**
     * uploadRoute() with no session at all must omit both Cookie and Authorization headers.
     */
    @Test
    fun `uploadRoute with empty session omits both Cookie and Authorization headers`() = runTest {
        transport.nextResponse = HttpResponse(code = 200, headers = emptyMap(), body = "")

        client.uploadRoute(SERVER, minimalRoute)

        val req = transport.lastRequest!!
        assertNull("Cookie must be absent", req.headers["Cookie"])
        assertNull("Authorization must be absent", req.headers["Authorization"])
    }
}
