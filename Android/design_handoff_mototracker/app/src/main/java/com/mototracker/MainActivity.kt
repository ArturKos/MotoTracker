package com.mototracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.mototracker.ui.theme.MotoTrackerTheme

/**
 * Entry point activity for the MotoTracker app.
 *
 * Hosts the Compose UI root and will delegate screen routing to
 * the navigation graph once the navigation shell (A3) is in place.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MotoTrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppPlaceholder(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Placeholder root composable rendered until the navigation shell is wired up in A3.
 */
@Composable
fun AppPlaceholder(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.app_name),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun AppPlaceholderPreview() {
    MotoTrackerTheme {
        AppPlaceholder()
    }
}
