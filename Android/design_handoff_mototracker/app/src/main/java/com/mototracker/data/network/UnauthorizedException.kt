package com.mototracker.data.network

/**
 * Thrown by [GpStrackClient] when the server returns HTTP 401 (Unauthorized).
 *
 * Callers should surface this to the user so they can re-authenticate via the
 * Login screen (wired in B12).
 */
class UnauthorizedException : Exception("GPStrack session expired — please log in again")
