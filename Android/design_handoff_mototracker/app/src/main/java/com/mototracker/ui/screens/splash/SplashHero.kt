package com.mototracker.ui.screens.splash

/**
 * Describes which rendering path [SplashScreen] should use for the hero banner.
 *
 * [ANIMATED] — the AVD drawable loaded and cast to [android.graphics.drawable.AnimatedVectorDrawable]
 * successfully; the [android.widget.ImageView]-hosted AVD path is active.
 *
 * [STATIC_FALLBACK] — the AVD drawable failed to load or cast; a plain raster
 * [R.drawable.splash_hero_hd] is shown instead so composition can never throw.
 */
enum class SplashRenderMode { ANIMATED, STATIC_FALLBACK }

/**
 * Pure, stateless helper that decides the hero render mode from a single Boolean flag.
 *
 * Keeping the decision in a pure function makes it unit-testable without any Android
 * framework or Compose dependency — the AVD load/cast attempt happens at the call site
 * inside [SplashScreen], which passes the result here as [avdLoadSucceeded].
 */
object SplashHero {

    /**
     * Returns [SplashRenderMode.ANIMATED] when [avdLoadSucceeded] is `true`,
     * otherwise [SplashRenderMode.STATIC_FALLBACK].
     *
     * @param avdLoadSucceeded `true` iff the AVD drawable loaded and cast to
     *   [android.graphics.drawable.AnimatedVectorDrawable] without throwing.
     */
    fun renderMode(avdLoadSucceeded: Boolean): SplashRenderMode =
        if (avdLoadSucceeded) SplashRenderMode.ANIMATED else SplashRenderMode.STATIC_FALLBACK
}
