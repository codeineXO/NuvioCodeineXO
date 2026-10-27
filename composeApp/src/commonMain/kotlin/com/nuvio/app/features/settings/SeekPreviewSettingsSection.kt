package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsUiState
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SeekPreviewSettingsSection(settings: PlayerSettingsUiState, isTablet: Boolean) {
    var showKeyDialog by rememberSaveable { mutableStateOf(false) }
    var showCountDialog by rememberSaveable { mutableStateOf(false) }
    SettingsSection(
        title = stringResource(Res.string.settings_seek_preview_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_playback_seek_previews),
                description = stringResource(Res.string.settings_playback_seek_previews_description),
                checked = settings.seekPreviewEnabled,
                isTablet = isTablet,
                onCheckedChange = PlayerSettingsRepository::setSeekPreviewEnabled,
            )
            if (settings.seekPreviewEnabled) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_playback_seekr_api_key),
                    description = if (settings.seekrApiKey.isBlank()) {
                        stringResource(Res.string.settings_playback_seekr_key_not_set)
                    } else "••••••••",
                    isTablet = isTablet,
                    onClick = { showKeyDialog = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_seek_preview_dragging),
                    description = null,
                    checked = settings.seekPreviewWhileDragging,
                    isTablet = isTablet,
                    onCheckedChange = PlayerSettingsRepository::setSeekPreviewWhileDragging,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_seek_preview_hovering),
                    description = null,
                    checked = settings.seekPreviewWhileHovering,
                    isTablet = isTablet,
                    onCheckedChange = PlayerSettingsRepository::setSeekPreviewWhileHovering,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_seek_preview_keyboard),
                    description = null,
                    checked = settings.seekPreviewWhileKeyboardSeeking,
                    isTablet = isTablet,
                    onCheckedChange = PlayerSettingsRepository::setSeekPreviewWhileKeyboardSeeking,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_seek_preview_count),
                    description = settings.seekPreviewThumbnailCount.toString(),
                    isTablet = isTablet,
                    onClick = { showCountDialog = true },
                )
            }
        }
    }
    if (showKeyDialog && settings.seekPreviewEnabled) {
        SeekrApiKeyDialog(
            initialValue = settings.seekrApiKey,
            onSave = {
                PlayerSettingsRepository.setSeekrApiKey(it)
                showKeyDialog = false
            },
            onDismiss = { showKeyDialog = false },
        )
    }
    if (showCountDialog && settings.seekPreviewEnabled) {
        IosEnumSelectionDialog(
            title = stringResource(Res.string.settings_seek_preview_count),
            options = listOf(1, 3, 5),
            selected = settings.seekPreviewThumbnailCount,
            label = { it.toString() },
            onSelect = {
                PlayerSettingsRepository.setSeekPreviewThumbnailCount(it)
                showCountDialog = false
            },
            onDismiss = { showCountDialog = false },
        )
    }
}
