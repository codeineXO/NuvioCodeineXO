package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.player.AnimeUpscalerMode
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerUiMode
import com.nuvio.app.features.player.SubtitleColorEditTarget
import com.nuvio.app.features.player.SubtitleColorSwatches
import com.nuvio.app.features.player.SubtitleOutlineColorSwatches
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_mode
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_mode_all
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_mode_only_watching
import nuvio.composeapp.generated.resources.settings_advanced_discord_rich_presence
import nuvio.composeapp.generated.resources.settings_advanced_discord_rich_presence_description
import nuvio.composeapp.generated.resources.settings_advanced_section_discord
import nuvio.composeapp.generated.resources.settings_appearance_section_display
import nuvio.composeapp.generated.resources.settings_playback_player_ui
import nuvio.composeapp.generated.resources.settings_playback_section_player
import nuvio.composeapp.generated.resources.settings_playback_section_subtitle_rendering
import nuvio.composeapp.generated.resources.settings_playback_subtitle_outline_color
import nuvio.composeapp.generated.resources.settings_playback_subtitle_text_color
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.codeineXOSettingsContent(
    isTablet: Boolean,
) {
    // ─── Display / Interface ───
    item {
        val auraBackgroundEnabled by remember {
            ThemeSettingsRepository.ensureLoaded()
            ThemeSettingsRepository.auraBackgroundEnabled
        }.collectAsStateWithLifecycle()

        SettingsSection(
            title = stringResource(Res.string.settings_appearance_section_display),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = "CodeineXO Aura",
                    description = "Dynamic chromatic aurora ambient background",
                    checked = auraBackgroundEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ThemeSettingsRepository::setAuraBackgroundEnabled,
                )
            }
        }
    }

    // ─── Player ───
    item {
        val playerSettings by remember {
            PlayerSettingsRepository.ensureLoaded()
            PlayerSettingsRepository.uiState
        }.collectAsStateWithLifecycle()

        var showPlayerUiDialog by rememberSaveable { mutableStateOf(false) }
        var showAnimeUpscalerDialog by rememberSaveable { mutableStateOf(false) }

        SettingsSection(
            title = stringResource(Res.string.settings_playback_section_player),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_playback_player_ui),
                    description = stringResource(playerSettings.playerUiMode.labelRes),
                    isTablet = isTablet,
                    onClick = { showPlayerUiDialog = true },
                )
                if (isDesktop) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = "Enable Anime Upscaler",
                        description = "Enhance anime and cartoons using real-time Anime4K shaders",
                        checked = playerSettings.animeUpscalerEnabled,
                        isTablet = isTablet,
                        onCheckedChange = PlayerSettingsRepository::setAnimeUpscalerEnabled,
                    )
                    if (playerSettings.animeUpscalerEnabled) {
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = "Upscaler Mode",
                            description = playerSettings.animeUpscalerMode.label,
                            isTablet = isTablet,
                            onClick = { showAnimeUpscalerDialog = true },
                        )
                    }
                }
            }
        }

        if (showPlayerUiDialog) {
            IosEnumSelectionDialog(
                title = stringResource(Res.string.settings_playback_player_ui),
                options = PlayerUiMode.entries,
                selected = playerSettings.playerUiMode,
                label = { stringResource(it.labelRes) },
                description = { stringResource(it.descriptionRes) },
                onSelect = { mode ->
                    PlayerSettingsRepository.setPlayerUiMode(mode)
                    showPlayerUiDialog = false
                },
                onDismiss = { showPlayerUiDialog = false },
            )
        }

        if (showAnimeUpscalerDialog) {
            AnimeUpscalerModeDialog(
                selectedMode = playerSettings.animeUpscalerMode,
                onModeSelected = { mode ->
                    PlayerSettingsRepository.setAnimeUpscalerMode(mode)
                    showAnimeUpscalerDialog = false
                },
                onDismiss = { showAnimeUpscalerDialog = false },
            )
        }
    }

    // ─── Subtitle Rendering ───
    item {
        val playerSettings by remember {
            PlayerSettingsRepository.ensureLoaded()
            PlayerSettingsRepository.uiState
        }.collectAsStateWithLifecycle()

        var showSubtitleFontDialog by rememberSaveable { mutableStateOf(false) }
        var showSubtitleOutlineEffectDialog by rememberSaveable { mutableStateOf(false) }
        var showSubtitleTextColorDialog by rememberSaveable { mutableStateOf(false) }
        var showSubtitleOutlineColorDialog by rememberSaveable { mutableStateOf(false) }

        val subtitleStyle = playerSettings.subtitleStyle

        SettingsSection(
            title = stringResource(Res.string.settings_playback_section_subtitle_rendering),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = "Subtitle Font",
                    description = subtitleStyle.fontName,
                    isTablet = isTablet,
                    onClick = { showSubtitleFontDialog = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = "Outline Style",
                    description = subtitleStyle.outlineEffect.label,
                    isTablet = isTablet,
                    onClick = { showSubtitleOutlineEffectDialog = true },
                )
                if (subtitleStyle.outlineEnabled) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSliderRow(
                        title = "Outline Thickness",
                        value = subtitleStyle.outlineWidth,
                        valueText = "${subtitleStyle.outlineWidth} px",
                        valueRange = 1..8,
                        step = 1,
                        isTablet = isTablet,
                        onValueChange = { value ->
                            PlayerSettingsRepository.setSubtitleStyle(subtitleStyle.copy(outlineWidth = value))
                        },
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_playback_subtitle_text_color),
                    description = subtitleColorLabel(subtitleStyle.textColor),
                    isTablet = isTablet,
                    onClick = { showSubtitleTextColorDialog = true },
                )
                if (subtitleStyle.outlineEnabled) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_playback_subtitle_outline_color),
                        description = subtitleColorLabel(subtitleStyle.outlineColor),
                        isTablet = isTablet,
                        onClick = { showSubtitleOutlineColorDialog = true },
                    )
                }
            }
        }

        if (showSubtitleFontDialog) {
            SubtitleFontDialog(
                selectedFont = playerSettings.subtitleStyle.fontName,
                onFontSelected = { font ->
                    PlayerSettingsRepository.setSubtitleStyle(playerSettings.subtitleStyle.copy(fontName = font))
                    showSubtitleFontDialog = false
                },
                onDismiss = { showSubtitleFontDialog = false },
            )
        }

        if (showSubtitleOutlineEffectDialog) {
            SubtitleOutlineEffectDialog(
                selectedEffect = playerSettings.subtitleStyle.outlineEffect,
                onEffectSelected = { effect ->
                    val updated = playerSettings.subtitleStyle.copy(outlineEffect = effect)
                    PlayerSettingsRepository.setSubtitleStyle(updated)
                    showSubtitleOutlineEffectDialog = false
                },
                onDismiss = { showSubtitleOutlineEffectDialog = false },
            )
        }

        if (showSubtitleTextColorDialog) {
            SubtitleColorDialog(
                title = stringResource(Res.string.settings_playback_subtitle_text_color),
                colors = SubtitleColorSwatches,
                selectedColor = playerSettings.subtitleStyle.textColor,
                previewStyle = playerSettings.subtitleStyle,
                target = SubtitleColorEditTarget.TEXT,
                onColorSelected = { color ->
                    PlayerSettingsRepository.setSubtitleStyle(playerSettings.subtitleStyle.copy(textColor = color))
                },
                onDismiss = { showSubtitleTextColorDialog = false },
            )
        }

        if (showSubtitleOutlineColorDialog) {
            SubtitleColorDialog(
                title = stringResource(Res.string.settings_playback_subtitle_outline_color),
                colors = SubtitleOutlineColorSwatches,
                selectedColor = playerSettings.subtitleStyle.outlineColor,
                previewStyle = playerSettings.subtitleStyle,
                target = SubtitleColorEditTarget.OUTLINE,
                onColorSelected = { color ->
                    PlayerSettingsRepository.setSubtitleStyle(playerSettings.subtitleStyle.copy(outlineColor = color))
                },
                onDismiss = { showSubtitleOutlineColorDialog = false },
            )
        }
    }

    // ─── Discord ───
    if (isDesktop && DiscordRichPresenceRepository.isSupported) {
        item {
            val discordEnabledFlow = remember {
                DiscordRichPresenceRepository.ensureLoaded()
                DiscordRichPresenceRepository.enabled
            }
            val discordEnabled by discordEnabledFlow.collectAsStateWithLifecycle()
            val activityModeFlow = remember {
                DiscordRichPresenceRepository.activityMode
            }
            val activityMode by activityModeFlow.collectAsStateWithLifecycle()
            var showActivityModeSheet by rememberSaveable { mutableStateOf(false) }

            SettingsSection(
                title = stringResource(Res.string.settings_advanced_section_discord),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_advanced_discord_rich_presence),
                        description = stringResource(Res.string.settings_advanced_discord_rich_presence_description),
                        checked = discordEnabled,
                        isTablet = isTablet,
                        onCheckedChange = DiscordRichPresenceRepository::setEnabled,
                    )
                    if (discordEnabled) {
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = stringResource(Res.string.settings_advanced_discord_activity_mode),
                            description = stringResource(
                                if (activityMode == DiscordActivityMode.ONLY_WATCHING) {
                                    Res.string.settings_advanced_discord_activity_mode_only_watching
                                } else {
                                    Res.string.settings_advanced_discord_activity_mode_all
                                }
                            ),
                            isTablet = isTablet,
                            onClick = { showActivityModeSheet = true },
                        )
                    }
                }
            }

            if (showActivityModeSheet) {
                DiscordActivityModeBottomSheet(
                    selectedMode = activityMode,
                    onModeSelected = { mode ->
                        DiscordRichPresenceRepository.setActivityMode(mode)
                        showActivityModeSheet = false
                    },
                    onDismiss = { showActivityModeSheet = false },
                )
            }
        }
    }
}
