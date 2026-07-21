package com.mototracker.ui.screens.splash

import com.mototracker.R

/**
 * Single source of truth for the nine splash layer drawable resource IDs.
 *
 * Listed back-to-front (z-order: index 0 is furthest back, index 8 is closest to the viewer):
 *
 * | Index | Layer         | Note                               |
 * |-------|---------------|------------------------------------|
 * | 0     | mountains     | static; always fully opaque        |
 * | 1     | trail         | fades + scales in 600–1300 ms      |
 * | 2     | beam          | rides with bike_body transform     |
 * | 3     | bike_body     | slides in from left 0–800 ms       |
 * | 4     | wheel_rear    | spins 720° with bike_body          |
 * | 5     | wheel_front   | spins 720° with bike_body          |
 * | 6     | pin           | drops in 1200–1600 ms              |
 * | 7     | wordmark      | fades + scales up 1500–2200 ms     |
 * | 8     | tagline       | rides with wordmark transform      |
 *
 * Both the layer presence-check in [SplashScreen] and the animated renderer in [LayeredHero]
 * derive their layer set from this single list — no duplicate id lists.
 */
object SplashAssets {

    /** Nine splash PNG layer resource IDs ordered back-to-front (mountains → tagline). */
    val layerDrawableRes: IntArray = intArrayOf(
        R.drawable.splash_l_mountains,
        R.drawable.splash_l_trail,
        R.drawable.splash_l_beam,
        R.drawable.splash_l_bike_body,
        R.drawable.splash_l_wheel_rear,
        R.drawable.splash_l_wheel_front,
        R.drawable.splash_l_pin,
        R.drawable.splash_l_wordmark,
        R.drawable.splash_l_tagline,
    )
}
