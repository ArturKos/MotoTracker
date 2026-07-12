package com.mototracker.core.resource

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Minimal interface for resolving Android string resources without holding an Activity reference.
 *
 * Inject [ContextStringResolver] in production (via Hilt) and a simple fake in unit tests so
 * ViewModels that need localized strings remain testable without the Android runtime.
 */
interface StringResolver {

    /**
     * Returns the string associated with [resId].
     *
     * @param resId Android string resource identifier.
     */
    fun getString(resId: Int): String

    /**
     * Returns the string associated with [resId] formatted with [args].
     *
     * @param resId Android string resource identifier.
     * @param args  Format arguments forwarded to [Context.getString].
     */
    fun getString(resId: Int, vararg args: Any): String
}

/**
 * Application-context-backed [StringResolver].
 *
 * The application context is safe to hold long-term and survives configuration changes.
 *
 * @param context Application context injected by Hilt.
 */
class ContextStringResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : StringResolver {

    override fun getString(resId: Int): String = context.getString(resId)

    override fun getString(resId: Int, vararg args: Any): String = context.getString(resId, *args)
}
