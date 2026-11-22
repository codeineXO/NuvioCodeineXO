package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nuvio.app.core.diagnostics.DebugLogSettings
import com.nuvio.app.features.player.shortcuts.PlayerShortcutAction
import com.nuvio.app.features.player.shortcuts.PlayerShortcutSettings
import com.nuvio.app.features.player.shortcuts.displayLabel
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.debug_log_enabled
import nuvio.composeapp.generated.resources.debug_log_enabled_description
import nuvio.composeapp.generated.resources.debug_log_open_folder
import nuvio.composeapp.generated.resources.debug_log_redact
import nuvio.composeapp.generated.resources.debug_log_redact_description
import nuvio.composeapp.generated.resources.debug_log_section
import org.jetbrains.compose.resources.stringResource

/** Shown on both the Advanced and CodeineXO pages; both edit the same [DebugLogSettings]. */
internal fun LazyListScope.debugLoggingSettingsItem(isTablet: Boolean) {
    if (!DebugLogSettings.isSupported) return
    item { DebugLoggingSettingsSection(isTablet) }
}

@Composable
private fun DebugLoggingSettingsSection(isTablet: Boolean) {
    val enabled by DebugLogSettings.enabled.collectAsState()
    val redact by DebugLogSettings.redactSecrets.collectAsState()
    val keymap by PlayerShortcutSettings.keymap.collectAsState()
    val shortcut = keymap.bindings(PlayerShortcutAction.ToggleDebugLog)
        .filterNotNull()
        .joinToString(" / ") { it.displayLabel() }
        .ifEmpty { "—" }

    SettingsSection(
        title = stringResource(Res.string.debug_log_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.debug_log_enabled),
                description = stringResource(Res.string.debug_log_enabled_description, shortcut),
                checked = enabled,
                isTablet = isTablet,
                onCheckedChange = DebugLogSettings::setEnabled,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.debug_log_redact),
                description = stringResource(Res.string.debug_log_redact_description),
                checked = redact,
                isTablet = isTablet,
                onCheckedChange = DebugLogSettings::setRedactSecrets,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.debug_log_open_folder),
                description = DebugLogSettings.logFolderPath,
                icon = Icons.Rounded.FolderOpen,
                isTablet = isTablet,
                onClick = DebugLogSettings::openLogFolder,
            )
        }
    }
}
