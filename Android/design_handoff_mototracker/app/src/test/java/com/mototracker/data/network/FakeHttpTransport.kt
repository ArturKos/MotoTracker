package com.mototracker.data.network

/**
 * Test double for [HttpTransport].
 *
 * Records the last [HttpRequest] received and returns the scripted [nextResponse].
 * Set [nextResponse] before each call to control what the client under test sees.
 */
class FakeHttpTransport(
    initialResponse: HttpResponse = HttpResponse(code = 200, headers = emptyMap(), body = ""),
) : HttpTransport {

    /** The response returned by the next [execute] call. */
    var nextResponse: HttpResponse = initialResponse

    /** The request received by the most recent [execute] call, or null if never called. */
    var lastRequest: HttpRequest? = null

    override suspend fun execute(request: HttpRequest): Result<HttpResponse> {
        lastRequest = request
        return Result.success(nextResponse)
    }
}
