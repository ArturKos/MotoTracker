package com.mototracker.data.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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

private class FakeRegisterSessionStore : SessionStore {
    private val _session = MutableStateFlow(SessionState.UNAUTHENTICATED)
    override val session: Flow<SessionState> = _session
    var saveCalls = 0

    override suspend fun save(cookie: String, email: String) {
        saveCalls++
        _session.value = SessionState(cookie = cookie, email = email)
    }

    override suspend fun clear() {
        _session.value = SessionState.UNAUTHENTICATED
    }
}

private const val SERVER = "http://192.168.1.145/gpstrack"
private const val TEST_EMAIL = "rider@example.com"
private const val TEST_PASSWORD = "s3cr3t!!"

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Unit tests for [HttpGpStrackClient.register] behaviour.
 *
 * All I/O is replaced by [FakeHttpTransport] (scripted responses) and
 * [FakeRegisterSessionStore] (in-memory StateFlow). No real network or DataStore is used.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HttpGpStrackClientRegisterTest {

    private lateinit var transport: FakeHttpTransport
    private lateinit var sessionStore: FakeRegisterSessionStore
    private lateinit var client: HttpGpStrackClient

    @Before
    fun setUp() {
        transport = FakeHttpTransport()
        sessionStore = FakeRegisterSessionStore()
        client = HttpGpStrackClient(transport, sessionStore)
    }

    /**
     * (a) 200 + Set-Cookie → success and PHPSESSID cookie is persisted with the email.
     */
    @Test
    fun `register on 200 with Set-Cookie persists cookie and returns success`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("PHPSESSID=xyz; Path=/; HttpOnly")),
            body = "",
        )

        val result = client.register(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertTrue("register() must succeed on 200", result.isSuccess)
        assertEquals("cookie must be persisted", 1, sessionStore.saveCalls)
        val state = sessionStore.session.let {
            var s = SessionState.UNAUTHENTICATED
            // Read the current value of the flow
            val flow = sessionStore.session as MutableStateFlow
            flow.value
        }
        assertEquals("PHPSESSID=xyz", state.cookie)
        assertEquals(TEST_EMAIL, state.email)
    }

    /**
     * (b) The request must go to `/register.php`, use POST, send Content-Type application/json,
     *     and include both email and password in the JSON body.
     */
    @Test
    fun `register sends JSON POST to register endpoint with email and password`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("PHPSESSID=abc")),
            body = "",
        )

        client.register(SERVER, TEST_EMAIL, TEST_PASSWORD)

        val req = assertNotNull(transport.lastRequest).let { transport.lastRequest!! }
        assertTrue("URL must end with /register.php", req.url.endsWith("/register.php"))
        assertEquals("POST", req.method)
        assertEquals(
            "Content-Type must be application/json; charset=utf-8",
            "application/json; charset=utf-8",
            req.headers["Content-Type"],
        )
        val bodyStr = req.body?.toString(Charsets.UTF_8) ?: ""
        val json = JSONObject(bodyStr)
        assertEquals("email must be in JSON body", TEST_EMAIL, json.getString("email"))
        assertEquals("password must be in JSON body", TEST_PASSWORD, json.getString("password"))
    }

    /**
     * (c) 409 → failure wrapping [EmailTakenException], no cookie saved.
     */
    @Test
    fun `register on 409 returns EmailTakenException and does not save cookie`() = runTest {
        transport.nextResponse = HttpResponse(code = 409, headers = emptyMap(), body = "")

        val result = client.register(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertTrue("register() must fail on 409", result.isFailure)
        assertTrue(
            "failure must be EmailTakenException",
            result.exceptionOrNull() is EmailTakenException,
        )
        assertEquals("no cookie must be saved", 0, sessionStore.saveCalls)
    }

    /**
     * (d) 400 → failure wrapping [InvalidRegistrationException], no cookie saved.
     */
    @Test
    fun `register on 400 returns InvalidRegistrationException and does not save cookie`() = runTest {
        transport.nextResponse = HttpResponse(code = 400, headers = emptyMap(), body = "")

        val result = client.register(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertTrue("register() must fail on 400", result.isFailure)
        assertTrue(
            "failure must be InvalidRegistrationException",
            result.exceptionOrNull() is InvalidRegistrationException,
        )
        assertEquals("no cookie must be saved", 0, sessionStore.saveCalls)
    }

    /**
     * (e) 2xx without Set-Cookie → success, no cookie saved, no crash.
     */
    @Test
    fun `register on 2xx without Set-Cookie returns success without saving cookie`() = runTest {
        transport.nextResponse = HttpResponse(code = 201, headers = emptyMap(), body = "")

        val result = client.register(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertTrue("register() must succeed on 2xx even without Set-Cookie", result.isSuccess)
        assertEquals("no cookie must be saved when header absent", 0, sessionStore.saveCalls)
        assertFalse("session must remain unauthenticated", sessionStore.session.let {
            (it as MutableStateFlow).value.isAuthenticated
        })
    }

    /**
     * (f) Non-2xx (not 400/409), e.g. 500 → failure (not EmailTaken or InvalidRegistration).
     */
    @Test
    fun `register on 500 returns failure wrapping generic exception`() = runTest {
        transport.nextResponse = HttpResponse(code = 500, headers = emptyMap(), body = "")

        val result = client.register(SERVER, TEST_EMAIL, TEST_PASSWORD)

        assertTrue("register() must fail on 500", result.isFailure)
        val ex = result.exceptionOrNull()
        assertFalse("failure must not be EmailTakenException", ex is EmailTakenException)
        assertFalse("failure must not be InvalidRegistrationException", ex is InvalidRegistrationException)
    }

    /**
     * (g) 200 + Set-Cookie → cookie name=value is trimmed at first semicolon.
     */
    @Test
    fun `register parses Set-Cookie and persists only name=value pair`() = runTest {
        transport.nextResponse = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("PHPSESSID=testtoken; Path=/; HttpOnly")),
            body = "",
        )

        client.register(SERVER, TEST_EMAIL, TEST_PASSWORD)

        val cookie = (sessionStore.session as MutableStateFlow).value.cookie
        assertNotNull(cookie)
        assertEquals("PHPSESSID=testtoken", cookie)
        assertNull("cookie must not contain semicolons", cookie?.let { if (it.contains(';')) it else null })
    }
}
