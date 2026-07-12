package com.mototracker.ui.screens.settings

import androidx.annotation.StringRes
import com.mototracker.R

/**
 * Tabs for the tabbed Settings screen (B18).
 *
 * Each entry maps to one or more settings sections and carries the string
 * resource used as the tab label. Tab labels reuse existing section strings
 * so no new translations are required.
 *
 * Mapping:
 * - [ACCOUNT]            → §1 Account
 * - [MOTORCYCLES]        → §2 My motorcycles
 * - [APPEARANCE]         → §3 Appearance & language
 * - [SERVER_SYNC]        → §4 Server & sync + §5 Sync queue + §6 Bluetooth broadcast
 * - [SYSTEM_DIAGNOSTICS] → §7 System & privacy (Diagnostics + Backup sub-groups)
 * - [PREFERENCES]        → §8 Preferences + §9 version footer
 *
 * @property titleRes String resource ID for the tab label.
 */
enum class SettingsTab(@StringRes val titleRes: Int) {
    ACCOUNT(R.string.section_account),
    MOTORCYCLES(R.string.section_bikes),
    APPEARANCE(R.string.section_appearance),
    SERVER_SYNC(R.string.section_server),
    SYSTEM_DIAGNOSTICS(R.string.section_system),
    PREFERENCES(R.string.section_preferences),
}
