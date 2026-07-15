package com.mototracker.ui.screens.help

import androidx.annotation.StringRes
import com.mototracker.R

/**
 * A single help topic consisting of a localised title and body, both represented
 * as string resource IDs so the model is testable on the JVM without an Android context.
 *
 * @param titleRes String resource ID for the section heading.
 * @param bodyRes  String resource ID for the explanatory paragraph.
 */
data class HelpTopic(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

/**
 * Static content model for the Help screen (J1).
 *
 * Exposes an ordered list of [HelpTopic] entries covering the major app features.
 * The composable [HelpScreen] maps over [topics] and resolves each resource via
 * `stringResource`, keeping this object free of UI dependencies and fully
 * unit-testable.
 */
object HelpContent {
    /** Ordered help topics covering each major feature of the app. */
    val topics: List<HelpTopic> = listOf(
        HelpTopic(R.string.help_topic_recording_title, R.string.help_topic_recording_body),
        HelpTopic(R.string.help_topic_routes_title, R.string.help_topic_routes_body),
        HelpTopic(R.string.help_topic_gps_title, R.string.help_topic_gps_body),
        HelpTopic(R.string.help_topic_fuel_title, R.string.help_topic_fuel_body),
        HelpTopic(R.string.help_topic_waves_title, R.string.help_topic_waves_body),
        HelpTopic(R.string.help_topic_backup_title, R.string.help_topic_backup_body),
        HelpTopic(R.string.help_topic_themes_title, R.string.help_topic_themes_body),
    )
}
