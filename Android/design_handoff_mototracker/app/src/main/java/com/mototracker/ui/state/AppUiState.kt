package com.mototracker.ui.state

import com.mototracker.ui.theme.AccentColor
import com.mototracker.ui.theme.MotoTheme

/**
 * Top-level navigation screens of the app.
 *
 * [LOGIN] is shown when the user is not authenticated (or before any action is taken).
 * [MAIN] covers all in-app screens behind the navigation shell.
 */
enum class AppScreen { LOGIN, MAIN }

/**
 * Supported UI languages, each keyed to a BCP-47 language tag.
 *
 * @property tag BCP-47 language tag used when resolving locales.
 */
enum class Language(val tag: String) {
    PL("pl"),
    EN("en"),
    DE("de"),
    FR("fr"),
    CS("cs"),
    RU("ru"),
}

/**
 * Measurement system preference.
 *
 * [METRIC] uses km/h and km; [IMPERIAL] uses mph and miles.
 */
enum class Units { METRIC, IMPERIAL }

/**
 * Immutable snapshot of the global UI state.
 *
 * Defaults mirror the prototype initial state: login screen, unauthenticated guest,
 * Polish language, Cockpit theme, Teal accent, Metric units.
 *
 * NOTE: Persistence is deferred to task A6, which will back this state with DataStore.
 * Until then, state is in-memory only and resets on process death.
 *
 * @param screen        Which top-level screen is currently active.
 * @param authed        Whether the user has authenticated (false = guest / local-only).
 * @param language      Active UI language.
 * @param theme         Active visual theme (cockpit / grid / light).
 * @param accent        User-selected accent colour override.
 * @param units         Measurement system preference.
 */
data class AppUiState(
    val screen: AppScreen = AppScreen.LOGIN,
    val authed: Boolean = false,
    val language: Language = Language.PL,
    val theme: MotoTheme = MotoTheme.COCKPIT,
    val accent: AccentColor = AccentColor.TEAL,
    val units: Units = Units.METRIC,
)
