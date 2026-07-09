package com.mototracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application subclass required by Hilt. Triggers the Hilt component code generation
 * that makes dependency injection available throughout the app.
 */
@HiltAndroidApp
class MotoTrackerApp : Application()
