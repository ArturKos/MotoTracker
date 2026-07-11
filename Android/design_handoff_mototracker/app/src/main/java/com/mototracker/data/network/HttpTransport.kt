package com.mototracker.data.network

/**
 * HTTP request model passed to [HttpTransport.execute].
 *
 * @param url     Full URL including scheme, host, path, and query string.
 * @param method  HTTP verb, e.g. "GET", "POST".
 * @param headers Request headers. Multi-value headers must be joined by the caller.
 * @param body    Request body bytes, or null for requests with no body.
 */
data class HttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

/**
 * HTTP response model returned by [HttpTransport.execute].
 *
 * @param code    HTTP status code.
 * @param headers Response headers; values are lists to preserve multiple `Set-Cookie` lines.
 * @param body    Response body decoded as UTF-8, or an empty string if none.
 */
data class HttpResponse(
    val code: Int,
    val headers: Map<String, List<String>>,
    val body: String,
)

/**
 * Injectable seam for making HTTP requests.
 *
 * Separating the transport from the client logic allows the login / cookie /
 * 401-handling paths to be unit-tested without a real network.
 *
 * Implementations must be main-safe (switch to [kotlinx.coroutines.Dispatchers.IO]
 * internally when performing blocking I/O).
 */
interface HttpTransport {

    /**
     * Executes [request] and returns the server response.
     *
     * @return [Result.success] wrapping the [HttpResponse], or
     *         [Result.failure] wrapping the underlying [Throwable] on any I/O error.
     */
    suspend fun execute(request: HttpRequest): Result<HttpResponse>
}
