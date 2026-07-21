package com.mototracker.data.network

import com.mototracker.data.model.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val ENDPOINT_ROUTES = "/api_routes.php"
private const val ENDPOINT_LOGIN = "/login.php"
private const val ENDPOINT_REGISTER = "/register.php"

/**
 * [GpStrackClient] implementation that delegates transport to [HttpTransport] and
 * persists / attaches session cookies via [SessionStore].
 *
 * The actual HTTP round-trip is handled by [UrlConnectionHttpTransport] in production
 * (on-device only, 🔬). A [FakeHttpTransport] is injected in unit tests.
 *
 * @param transport    Injectable HTTP transport seam.
 * @param sessionStore Persists the session cookie between requests.
 */
@Singleton
class HttpGpStrackClient @Inject constructor(
    private val transport: HttpTransport,
    private val sessionStore: SessionStore,
) : GpStrackClient {

    /**
     * POSTs credentials to `<serverAddress>/login.php` as `application/x-www-form-urlencoded`.
     *
     * Field names are `email` and `password`. On a 2xx response the first `Set-Cookie` value's
     * `name=value` pair is extracted (required) and the `write_api_key` JSON field is parsed
     * from the response body (optional; null if absent or body is not valid JSON). Both are
     * persisted via [SessionStore.save].
     *
     * @return [Result.success] on HTTP 2xx + valid Set-Cookie;
     *         [Result.failure] on non-2xx, missing cookie, or transport error.
     */
    override suspend fun login(
        serverAddress: String,
        email: String,
        password: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyStr = "email=${URLEncoder.encode(email, "UTF-8")}" +
                "&password=${URLEncoder.encode(password, "UTF-8")}"
            val body = bodyStr.toByteArray(Charsets.UTF_8)
            val request = HttpRequest(
                url = "$serverAddress$ENDPOINT_LOGIN",
                method = "POST",
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Content-Length" to body.size.toString(),
                ),
                body = body,
            )
            val response = transport.execute(request).getOrThrow()
            check(response.code in 200..299) { "HTTP ${response.code}" }
            val cookie = parseSessionCookie(response.headers)
                ?: error("No Set-Cookie in login response")
            val writeApiKey = parseWriteApiKey(response.body)
            sessionStore.save(cookie, email, writeApiKey)
        }
    }

    /**
     * POSTs a JSON registration payload to `<serverAddress>/register.php`.
     *
     * On 409 throws [EmailTakenException]; on 400 throws [InvalidRegistrationException];
     * on any other non-2xx status the `check` call throws with the HTTP code. On 2xx the
     * `Set-Cookie` header and `write_api_key` JSON body field are both parsed (each optional).
     * If either is present, the session is persisted via [SessionStore.save]; if both are
     * absent the call still succeeds without persisting a session.
     *
     * @return [Result.success] on HTTP 2xx;
     *         [Result.failure] wrapping [EmailTakenException] on 409;
     *         [Result.failure] wrapping [InvalidRegistrationException] on 400;
     *         [Result.failure] wrapping the underlying [Throwable] on other non-2xx or I/O error.
     */
    override suspend fun register(
        serverAddress: String,
        email: String,
        password: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyJson = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()
            val bodyBytes = bodyJson.toByteArray(Charsets.UTF_8)
            val request = HttpRequest(
                url = "$serverAddress$ENDPOINT_REGISTER",
                method = "POST",
                headers = mapOf(
                    "Content-Type" to "application/json; charset=utf-8",
                    "Content-Length" to bodyBytes.size.toString(),
                ),
                body = bodyBytes,
            )
            val response = transport.execute(request).getOrThrow()
            when (response.code) {
                409 -> throw EmailTakenException()
                400 -> throw InvalidRegistrationException()
            }
            check(response.code in 200..299) { "HTTP ${response.code}" }
            val cookie = parseSessionCookie(response.headers)
            val writeApiKey = parseWriteApiKey(response.body)
            if (cookie != null || writeApiKey != null) {
                sessionStore.save(cookie, email, writeApiKey)
            }
        }
    }

    /**
     * POSTs [route] as a JSON body to `<serverAddress>/api_routes.php`.
     *
     * Attaches `Authorization: Bearer <writeApiKey>` when a write API key is stored, and the
     * `Cookie` header when a session cookie is stored. Both may be sent simultaneously (the
     * backend tries the Bearer token first and falls back to the cookie). On a 401 response
     * the session is cleared and [UnauthorizedException] is returned.
     *
     * @return [Result.success] on HTTP 2xx; [Result.failure] wrapping
     *         [UnauthorizedException] on 401, or any other error otherwise.
     */
    override suspend fun uploadRoute(serverAddress: String, route: Route): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = sessionStore.session.first()
                val body = buildJson(route).toString().toByteArray(Charsets.UTF_8)
                val headers = buildMap<String, String> {
                    put("Content-Type", "application/json; charset=utf-8")
                    session.cookie?.let { put("Cookie", it) }
                    session.writeApiKey?.let { put("Authorization", "Bearer $it") }
                }
                val request = HttpRequest(
                    url = "$serverAddress$ENDPOINT_ROUTES",
                    method = "POST",
                    headers = headers,
                    body = body,
                )
                val response = transport.execute(request).getOrThrow()
                if (response.code == 401) {
                    sessionStore.clear()
                    throw UnauthorizedException()
                }
                check(response.code in 200..299) { "HTTP ${response.code}" }
            }
        }

    /**
     * Extracts `write_api_key` from a JSON response body string.
     *
     * Returns null if [body] is null, blank, not valid JSON, or the field is absent/empty.
     * Never throws — a non-JSON body (e.g. plain-text "OK") is silently treated as no key.
     */
    private fun parseWriteApiKey(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            JSONObject(body).optString("write_api_key").ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Finds the first `Set-Cookie` header in [headers] (case-insensitive key lookup)
     * and returns the `name=value` pair before the first semicolon, or null if absent.
     */
    private fun parseSessionCookie(headers: Map<String, List<String>>): String? {
        val values = headers.entries
            .firstOrNull { it.key.equals("Set-Cookie", ignoreCase = true) }
            ?.value
            ?: return null
        return values.firstOrNull()
            ?.split(";")
            ?.firstOrNull()
            ?.trim()
    }

    private fun buildJson(route: Route): JSONObject = JSONObject().apply {
        put("id", route.id)
        put("name", route.name)
        put("dateEpochMs", route.dateEpochMs)
        put("bikeId", route.bikeId ?: JSONObject.NULL)
        put("km", route.km)
        put("durSec", route.durSec)
        put("avg", route.avg)
        put("max", route.max)
        put("lean", route.lean)
        put("elev", route.elev)
        put("fuel", route.fuel)
        route.wxJson?.let { put("wxJson", it) }
        route.pathJson?.let { put("pathJson", it) }
        route.speedJson?.let { put("speedJson", it) }
        route.elevProfileJson?.let { put("elevProfileJson", it) }
        route.notes?.let { put("notes", it) }
    }
}
