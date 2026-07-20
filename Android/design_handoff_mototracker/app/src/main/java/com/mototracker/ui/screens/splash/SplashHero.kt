package com.mototracker.ui.screens.splash

/**
 * Describes which rendering path [SplashScreen] should use for the hero banner.
 *
 * [LAYERED] — all nine [com.mototracker.R.drawable.splash_l_*] layer PNGs loaded
 * successfully; the full Compose-animated layered stack driven by [SplashChoreography]
 * is active.
 *
 * [STATIC_FALLBACK] — one or more layer drawables failed to load; a plain raster
 * [com.mototracker.R.drawable.splash_portrait_hd] is shown instead so composition
 * can never throw due to a missing asset.
 */
enum class SplashRenderMode { LAYERED, STATIC_FALLBACK }

/**
 * Pure, stateless helper that decides the hero render mode from a single Boolean flag.
 *
 * Keeping the decision in a pure function makes it unit-testable without any Android
 * framework or Compose dependency — the layer-presence check happens at the call site
 * inside [SplashScreen], which passes the result here as [allLayersPresent].
 */
object SplashHero {

    /**
     * Returns [SplashRenderMode.LAYERED] when all nine splash layer drawables resolved
     * successfully, otherwise [SplashRenderMode.STATIC_FALLBACK].
     *
     * @param allLayersPresent `true` iff every [com.mototracker.R.drawable.splash_l_*]
     *   drawable loaded without throwing via [androidx.core.content.ContextCompat.getDrawable].
     */
    fun renderMode(allLayersPresent: Boolean): SplashRenderMode =
        if (allLayersPresent) SplashRenderMode.LAYERED else SplashRenderMode.STATIC_FALLBACK
}
