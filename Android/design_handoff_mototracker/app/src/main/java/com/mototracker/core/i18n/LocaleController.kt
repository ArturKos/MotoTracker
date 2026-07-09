package com.mototracker.core.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import javax.inject.Inject

/**
 * Seam for applying a BCP-47 language tag as the app-level locale.
 *
 * Implementations are injectable so ViewModels remain testable without
 * an Android runtime.
 */
interface LocaleController {
    /** Applies [tag] (BCP-47, e.g. "pl", "de") as the active app locale. */
    fun applyLanguage(tag: String)
}

/**
 * Production implementation backed by [AppCompatDelegate.setApplicationLocales].
 *
 * Requires the `AppLocalesMetadataHolderService` declared in the manifest so
 * that the chosen locale persists across process restarts on API < 33.
 */
class AppCompatLocaleController @Inject constructor() : LocaleController {
    override fun applyLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
