package com.mototracker.ui.screens.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mototracker.BuildConfig
import com.mototracker.R
import com.mototracker.ui.theme.MotoTracker

/**
 * Help screen (J1) — a vertically scrollable list of feature-description sections
 * followed by an About/version footer.
 *
 * Content is driven by [topics] (defaulting to [HelpContent.topics]) so the
 * composable is decoupled from the static singleton and the content model is
 * separately unit-testable.
 *
 * Navigation: reached from Settings → Preferences → Help. Shows a back arrow in
 * the top bar; no bottom navigation bar.
 *
 * @param modifier Standard Compose modifier.
 * @param topics   Ordered list of help topics to render; defaults to [HelpContent.topics].
 */
@Composable
fun HelpScreen(
    modifier: Modifier = Modifier,
    topics: List<HelpTopic> = HelpContent.topics,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.help_intro),
            color = MotoTracker.colors.dim,
            style = MotoTracker.typography.label,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        topics.forEach { topic ->
            HelpTopicSection(topic = topic)
            Spacer(modifier = Modifier.height(16.dp))
        }

        HorizontalDivider(
            color = MotoTracker.colors.line,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // About / version footer
        Text(
            text = stringResource(R.string.help_about_title).uppercase(),
            color = MotoTracker.colors.accent,
            style = MotoTracker.typography.label.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            color = MotoTracker.colors.text,
            style = MotoTracker.typography.body,
        )
        Text(
            text = stringResource(R.string.help_version_label, BuildConfig.VERSION_NAME),
            color = MotoTracker.colors.dim,
            style = MotoTracker.typography.label,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}

/**
 * A single help topic row: bold title followed by a body paragraph.
 *
 * @param topic    The [HelpTopic] whose resources to render.
 * @param modifier Standard Compose modifier.
 */
@Composable
private fun HelpTopicSection(
    topic: HelpTopic,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(topic.titleRes),
            color = MotoTracker.colors.text,
            style = MotoTracker.typography.body.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = stringResource(topic.bodyRes),
            color = MotoTracker.colors.dim,
            style = MotoTracker.typography.label,
        )
    }
}
