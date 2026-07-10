package com.mototracker.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker

/** Placeholder for [com.mototracker.ui.navigation.MotoDestination.SETTINGS] — replaced in B7. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderContent(title = stringResource(R.string.screen_settings), modifier = modifier)
}

@Composable
private fun PlaceholderContent(title: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Text(
            text = title.uppercase(),
            style = MotoTracker.typography.screenTitle,
            color = MotoTracker.colors.text,
        )
    }
}
