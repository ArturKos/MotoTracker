package com.mototracker.data.network

/**
 * Thrown by [GpStrackClient.register] when the server returns HTTP 409 (Conflict),
 * indicating the supplied e-mail address is already registered.
 */
class EmailTakenException : Exception("E-mail address is already registered")

/**
 * Thrown by [GpStrackClient.register] when the server returns HTTP 400 (Bad Request),
 * indicating the registration input is invalid (e.g. malformed e-mail or too-short password).
 */
class InvalidRegistrationException : Exception("Invalid registration data")
