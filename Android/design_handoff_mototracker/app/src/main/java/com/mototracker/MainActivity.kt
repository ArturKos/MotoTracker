package com.mototracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mototracker.ui.navigation.MotoApp
import com.mototracker.ui.theme.MotoTrackerTheme

/**
 * Entry point activity for the MotoTracker app.
 *
 * Sets up the Compose content root: edge-to-edge rendering, [MotoTrackerTheme],
 * and the navigation shell [MotoApp] which owns the [androidx.navigation.NavController]
 * and all top-level screen destinations.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MotoTrackerTheme {
                MotoApp()
            }
        }
    }
}
