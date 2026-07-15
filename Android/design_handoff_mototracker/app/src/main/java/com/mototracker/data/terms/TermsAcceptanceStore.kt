package com.mototracker.data.terms

import kotlinx.coroutines.flow.Flow

/**
 * Persists and exposes the user's first-launch terms acceptance choice.
 *
 * Mirrors [com.mototracker.data.auth.AuthStateStore] in structure.
 * All implementations must be main-safe.
 */
interface TermsAcceptanceStore {

    /**
     * Live stream of whether the user has accepted the terms.
     *
     * Emits immediately with the persisted value on first collection, then on every subsequent
     * change. Absent stored value yields `false` (terms not yet accepted).
     */
    val accepted: Flow<Boolean>

    /**
     * Persists the user's acceptance choice.
     *
     * @param accepted `true` when the user accepted; `false` to reset the gate.
     */
    suspend fun setAccepted(accepted: Boolean)
}
