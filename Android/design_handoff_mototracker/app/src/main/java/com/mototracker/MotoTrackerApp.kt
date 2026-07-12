package com.mototracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

/**
 * Application subclass required by Hilt. Triggers the Hilt component code generation
 * that makes dependency injection available throughout the app.
 *
 * Also initialises osmdroid with a correct user-agent (prevents tile-server 418 bans).
 */
@HiltAndroidApp
class MotoTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
    }
}
