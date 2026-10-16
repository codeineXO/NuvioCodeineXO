package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.p2p.P2pConsentDialog
import com.nuvio.app.features.p2p.P2pCacheClearResult
import com.nuvio.app.features.p2p.P2pCacheSize
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.p2p.P2pStreamingEngine
import com.nuvio.app.features.p2p.P2pStreamingState
import com.nuvio.app.features.p2p.P2pTorrentProfile
import com.nuvio.app.features.anime.AnimeFillerSettingsRepository
import com.nuvio.app.features.player.AnimeUpscalerMode
import com.nuvio.app.features.player.AnimeUpscalerOption
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerUiMode
import com.nuvio.app.features.player.SvpMotionOption
import com.nuvio.app.features.player.SvpRuntimeInstaller
import com.nuvio.app.features.player.SvpRuntimeDownloadState
import com.nuvio.app.features.player.SubtitleColorEditTarget
import com.nuvio.app.features.player.SubtitleColorSwatches
import com.nuvio.app.features.player.SubtitleOutlineColorSwatches
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.isDesktop
import kotlinx.coroutines.launch
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
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.codeineXOSettingsContent(
    isTablet: Boolean,
    onOnboardingClick: (() -> Unit)? = null,
) {
    // ─── Quick Setup & Onboarding ───
    if (onOnboardingClick != null) {
        item {
            SettingsSection(
                title = "QUICK SETUP",
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsNavigationRow(
                        title = "Revisit CodeineXO Onboarding",
                        description = "Re-run setup wizard or review and apply recommended presets",
                        icon = Icons.Rounded.AutoAwesome,
                        isTablet = isTablet,
                        onClick = onOnboardingClick,
                    )
                }
            }
        }
    }

    // ─── Discord Support / Issue Reporting ───
    item {
        val tokens = MaterialTheme.nuvio
        val clipboardManager = LocalClipboardManager.current
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = "DM me on Discord @ codeine_xo to report issues",
                description = null,
                icon = Icons.Rounded.Chat,
                isTablet = isTablet,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy Discord username",
                        tint = tokens.colors.textMuted,
                        modifier = Modifier.size(if (isTablet) 20.dp else 18.dp),
                    )
                },
                onClick = {
                    clipboardManager.setText(AnnotatedString("codeine_xo"))
                    NuvioToastController.show("Copied @codeine_xo to clipboard")
                },
            )
        }
    }
    // ─── Display / Interface ───
    item {
        val auraBackgroundEnabled by remember {
            ThemeSettingsRepository.ensureLoaded()
            ThemeSettingsRepository.auraBackgroundEnabled
        }.collectAsStateWithLifecycle()
        val homeCatalogSettings by remember {
            HomeCatalogSettingsRepository.snapshot()
            HomeCatalogSettingsRepository.uiState
        }.collectAsStateWithLifecycle()
        val metaScreenSettings by remember {
            MetaScreenSettingsRepository.ensureLoaded()
            MetaScreenSettingsRepository.uiState
        }.collectAsStateWithLifecycle()

        SettingsSection(
            title = "LAYOUT",
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
                if (isDesktop) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_homescreen_scroll_posters_layout),
                        description = stringResource(Res.string.settings_homescreen_scroll_posters_layout_description),
                        checked = homeCatalogSettings.scrollPostersLayoutEnabled,
                        isTablet = isTablet,
                        onCheckedChange = HomeCatalogSettingsRepository::setScrollPostersLayoutEnabled,
                    )
                }
                SettingsGroupDivider(isTablet = isTablet)
                MetaEpisodeCardStyleSelector(
                    isTablet = isTablet,
                    selectedStyle = metaScreenSettings.episodeCardStyle,
                    onStyleSelected = MetaScreenSettingsRepository::setEpisodeCardStyle,
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
        var showSeekrApiKeyDialog by rememberSaveable { mutableStateOf(false) }

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
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_playback_show_seekbar_while_seeking),
                    description = stringResource(Res.string.settings_playback_show_seekbar_while_seeking_description),
                    checked = playerSettings.showSeekbarWhileSeeking,
                    isTablet = isTablet,
                    onCheckedChange = PlayerSettingsRepository::setShowSeekbarWhileSeeking,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_playback_seek_previews),
                    description = stringResource(Res.string.settings_playback_seek_previews_description),
                    checked = playerSettings.seekPreviewEnabled,
                    isTablet = isTablet,
                    onCheckedChange = PlayerSettingsRepository::setSeekPreviewEnabled,
                )
                if (playerSettings.seekPreviewEnabled) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_playback_seekr_api_key),
                        description = playerSettings.seekrApiKey.ifBlank {
                            stringResource(Res.string.settings_playback_seekr_key_not_set)
                        },
                        isTablet = isTablet,
                        onClick = { showSeekrApiKeyDialog = true },
                    )
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

        if (showSeekrApiKeyDialog) {
            SeekrApiKeyDialog(
                initialValue = playerSettings.seekrApiKey,
                onSave = { key ->
                    PlayerSettingsRepository.setSeekrApiKey(key)
                    showSeekrApiKeyDialog = false
                },
                onDismiss = { showSeekrApiKeyDialog = false },
            )
        }
    }

    if (isDesktop) {
        // ─── Video Enhancements ───
        item {
            val playerSettings by remember {
                PlayerSettingsRepository.ensureLoaded()
                PlayerSettingsRepository.uiState
            }.collectAsStateWithLifecycle()

            var showAnimeUpscalerOptionDialog by rememberSaveable { mutableStateOf(false) }
            var showAnimeUpscalerModeDialog by rememberSaveable { mutableStateOf(false) }
            var showSvpMotionOptionDialog by rememberSaveable { mutableStateOf(false) }

            SettingsSection(
                title = "VIDEO ENHANCEMENTS",
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsNavigationRow(
                        title = "Anime Upscaler",
                        description = playerSettings.animeUpscalerOption.label,
                        isTablet = isTablet,
                        onClick = { showAnimeUpscalerOptionDialog = true },
                    )
                    if (playerSettings.animeUpscalerOption != AnimeUpscalerOption.OFF) {
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsNavigationRow(
                            title = "Upscaler Mode",
                            description = playerSettings.animeUpscalerMode.label,
                            isTablet = isTablet,
                            onClick = { showAnimeUpscalerModeDialog = true },
                        )
                    }
                    SettingsGroupDivider(isTablet = isTablet)
                    val svpDownloadState by SvpRuntimeInstaller.state.collectAsStateWithLifecycle()
                    val svpDescription = when (val s = svpDownloadState) {
                        is SvpRuntimeDownloadState.Downloading -> "Downloading runtime... ${(s.progress * 100).toInt()}%"
                        is SvpRuntimeDownloadState.Extracting -> "Extracting runtime pack..."
                        else -> playerSettings.svpMotionOption.label
                    }
                    SettingsNavigationRow(
                        title = "Motion Interpolation (SVP)",
                        description = svpDescription,
                        isTablet = isTablet,
                        onClick = { showSvpMotionOptionDialog = true },
                    )
                }
            }

            if (showAnimeUpscalerOptionDialog) {
                AnimeUpscalerOptionDialog(
                    selectedOption = playerSettings.animeUpscalerOption,
                    onOptionSelected = { option ->
                        PlayerSettingsRepository.setAnimeUpscalerOption(option)
                        showAnimeUpscalerOptionDialog = false
                    },
                    onDismiss = { showAnimeUpscalerOptionDialog = false },
                )
            }

            if (showAnimeUpscalerModeDialog) {
                AnimeUpscalerModeDialog(
                    selectedMode = playerSettings.animeUpscalerMode,
                    onModeSelected = { mode ->
                        PlayerSettingsRepository.setAnimeUpscalerMode(mode)
                        showAnimeUpscalerModeDialog = false
                    },
                    onDismiss = { showAnimeUpscalerModeDialog = false },
                )
            }

            if (showSvpMotionOptionDialog) {
                SvpMotionOptionDialog(
                    selectedOption = playerSettings.svpMotionOption,
                    onOptionSelected = { option ->
                        PlayerSettingsRepository.setSvpMotionOption(option)
                        showSvpMotionOptionDialog = false
                    },
                    onDismiss = { showSvpMotionOptionDialog = false },
                )
            }
        }
    }

    // ─── Anime ───
    item {
        val animeFillerEnabled by remember {
            AnimeFillerSettingsRepository.ensureLoaded()
            AnimeFillerSettingsRepository.enabled
        }.collectAsStateWithLifecycle()

        SettingsSection(
            title = "ANIME",
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = "Anime Filler Detection",
                    description = "Identify and mark filler episodes across catalog and metadata addons",
                    checked = animeFillerEnabled,
                    isTablet = isTablet,
                    onCheckedChange = AnimeFillerSettingsRepository::setEnabled,
                )
            }
        }
    }

    if (isDesktop) {
        // ─── Audio Enhancement ───
        item {
            val playerSettings by remember {
                PlayerSettingsRepository.ensureLoaded()
                PlayerSettingsRepository.uiState
            }.collectAsStateWithLifecycle()

            SettingsSection(
                title = "AUDIO ENHANCEMENT",
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_playback_audio_night_mode),
                        description = stringResource(Res.string.settings_playback_audio_night_mode_desc),
                        checked = playerSettings.audioNightModeEnabled,
                        isTablet = isTablet,
                        onCheckedChange = PlayerSettingsRepository::setAudioNightModeEnabled,
                    )
                }
            }
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
                        valueText = { "$it px" },
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
            val activityTypeFlow = remember {
                DiscordRichPresenceRepository.activityType
            }
            val activityType by activityTypeFlow.collectAsStateWithLifecycle()
            val fullPosterEnabledFlow = remember {
                DiscordRichPresenceRepository.fullPosterEnabled
            }
            val fullPosterEnabled by fullPosterEnabledFlow.collectAsStateWithLifecycle()
            var showActivityModeSheet by rememberSaveable { mutableStateOf(false) }
            var showActivityTypeSheet by rememberSaveable { mutableStateOf(false) }

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
                            title = stringResource(Res.string.settings_advanced_discord_activity_type),
                            description = stringResource(
                                if (activityType == DiscordActivityType.WATCHING) {
                                    Res.string.settings_advanced_discord_activity_type_watching
                                } else {
                                    Res.string.settings_advanced_discord_activity_type_playing
                                }
                            ),
                            isTablet = isTablet,
                            onClick = { showActivityTypeSheet = true },
                        )
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
                        SettingsGroupDivider(isTablet = isTablet)
                        SettingsSwitchRow(
                            title = stringResource(Res.string.settings_advanced_discord_full_poster),
                            description = stringResource(Res.string.settings_advanced_discord_full_poster_description),
                            checked = fullPosterEnabled,
                            isTablet = isTablet,
                            onCheckedChange = DiscordRichPresenceRepository::setFullPosterEnabled,
                        )
                    }
                }
            }

            if (showActivityTypeSheet) {
                DiscordActivityTypeBottomSheet(
                    selectedType = activityType,
                    onTypeSelected = { type ->
                        DiscordRichPresenceRepository.setActivityType(type)
                        showActivityTypeSheet = false
                    },
                    onDismiss = { showActivityTypeSheet = false },
                )
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

    // ─── P2P Torrent Streaming ───
    if (P2pSettingsRepository.isVisible) {
        item {
            val p2pSettings by remember {
                P2pSettingsRepository.ensureLoaded()
                P2pSettingsRepository.uiState
            }.collectAsStateWithLifecycle()
            val p2pStreamingState by P2pStreamingEngine.state.collectAsStateWithLifecycle()
            val p2pCacheState by P2pStreamingEngine.cacheState.collectAsStateWithLifecycle()
            val coroutineScope = rememberCoroutineScope()

            var showP2pConsentDialog by remember { mutableStateOf(false) }
            var showP2pProfileDialog by remember { mutableStateOf(false) }
            var showP2pCacheSizeDialog by remember { mutableStateOf(false) }
            var p2pCacheClearResult by remember { mutableStateOf<P2pCacheClearResult?>(null) }
            var p2pCacheClearFailed by remember { mutableStateOf(false) }

            SettingsSection(
                title = stringResource(Res.string.settings_playback_section_p2p),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_p2p_title),
                        description = stringResource(Res.string.settings_p2p_subtitle),
                        checked = p2pSettings.p2pEnabled,
                        isTablet = isTablet,
                        onCheckedChange = { enabled ->
                            if (enabled && !p2pSettings.p2pEnabled) {
                                showP2pConsentDialog = true
                            } else {
                                P2pSettingsRepository.setP2pEnabled(enabled)
                            }
                        },
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_p2p_hide_stats_title),
                        description = stringResource(Res.string.settings_p2p_hide_stats_subtitle),
                        checked = p2pSettings.hideTorrentStats,
                        isTablet = isTablet,
                        onCheckedChange = P2pSettingsRepository::setHideTorrentStats,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_p2p_show_stats_title),
                        description = stringResource(Res.string.settings_p2p_show_stats_subtitle),
                        checked = p2pSettings.showTorrentStatsOverlay,
                        isTablet = isTablet,
                        onCheckedChange = P2pSettingsRepository::setShowTorrentStatsOverlay,
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_p2p_profile_title),
                        description = when (p2pSettings.torrentProfile) {
                            P2pTorrentProfile.SOFT -> stringResource(Res.string.settings_p2p_profile_soft)
                            P2pTorrentProfile.BALANCED -> stringResource(Res.string.settings_p2p_profile_balanced)
                            P2pTorrentProfile.FAST -> stringResource(Res.string.settings_p2p_profile_fast)
                        },
                        isTablet = isTablet,
                        onClick = { showP2pProfileDialog = true },
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_p2p_cache_size_title),
                        description = when (p2pSettings.cacheSize) {
                            P2pCacheSize.NONE -> stringResource(Res.string.settings_p2p_cache_none)
                            P2pCacheSize.GB_2 -> stringResource(Res.string.settings_p2p_cache_2_gb)
                            P2pCacheSize.GB_5 -> stringResource(Res.string.settings_p2p_cache_5_gb)
                            P2pCacheSize.GB_10 -> stringResource(Res.string.settings_p2p_cache_10_gb)
                            P2pCacheSize.GB_20 -> stringResource(Res.string.settings_p2p_cache_20_gb)
                            P2pCacheSize.GB_50 -> stringResource(Res.string.settings_p2p_cache_50_gb)
                        },
                        isTablet = isTablet,
                        onClick = { showP2pCacheSizeDialog = true },
                    )
                    SettingsGroupDivider(isTablet = isTablet)
                    val cacheClearAvailable = p2pStreamingState !is P2pStreamingState.Connecting &&
                        p2pStreamingState !is P2pStreamingState.Streaming &&
                        !p2pCacheState.isClearing
                    SettingsNavigationRow(
                        title = stringResource(Res.string.settings_p2p_clear_cache_title),
                        description = when {
                            p2pCacheState.isClearing ->
                                stringResource(Res.string.settings_p2p_clear_cache_clearing)
                            !cacheClearAvailable ->
                                stringResource(Res.string.settings_p2p_clear_cache_playback_active)
                            p2pCacheClearFailed ->
                                stringResource(Res.string.settings_p2p_clear_cache_failed)
                            p2pCacheClearResult != null -> stringResource(
                                Res.string.settings_p2p_clear_cache_done,
                                run {
                                    val b = p2pCacheClearResult!!.reclaimedBytes
                                    val gib = 1024.0 * 1024.0 * 1024.0
                                    val mib = 1024.0 * 1024.0
                                    if (b >= gib) "${kotlin.math.round(b / gib * 10.0) / 10.0} GB" else "${kotlin.math.round(b / mib * 10.0) / 10.0} MB"
                                },
                            )
                            !p2pCacheState.hasMeasurement ->
                                stringResource(Res.string.settings_p2p_clear_cache_usage_pending)
                            else -> stringResource(
                                Res.string.settings_p2p_clear_cache_usage,
                                run {
                                    val b = p2pCacheState.usedBytes
                                    val gib = 1024.0 * 1024.0 * 1024.0
                                    val mib = 1024.0 * 1024.0
                                    if (b >= gib) "${kotlin.math.round(b / gib * 10.0) / 10.0} GB" else "${kotlin.math.round(b / mib * 10.0) / 10.0} MB"
                                },
                            )
                        },
                        enabled = cacheClearAvailable,
                        isTablet = isTablet,
                        onClick = {
                            p2pCacheClearResult = null
                            p2pCacheClearFailed = false
                            coroutineScope.launch {
                                runCatching { P2pStreamingEngine.clearCache() }
                                    .onSuccess { p2pCacheClearResult = it }
                                    .onFailure { p2pCacheClearFailed = true }
                            }
                        },
                    )
                }
            }

            if (showP2pConsentDialog) {
                P2pConsentDialog(
                    onEnableP2p = {
                        P2pSettingsRepository.setP2pEnabled(true)
                        showP2pConsentDialog = false
                    },
                    onDismiss = { showP2pConsentDialog = false },
                )
            }

            if (showP2pProfileDialog) {
                IosEnumSelectionDialog(
                    title = stringResource(Res.string.settings_p2p_profile_title),
                    options = P2pTorrentProfile.entries,
                    selected = p2pSettings.torrentProfile,
                    label = { profile ->
                        when (profile) {
                            P2pTorrentProfile.SOFT -> stringResource(Res.string.settings_p2p_profile_soft)
                            P2pTorrentProfile.BALANCED -> stringResource(Res.string.settings_p2p_profile_balanced)
                            P2pTorrentProfile.FAST -> stringResource(Res.string.settings_p2p_profile_fast)
                        }
                    },
                    description = { profile ->
                        when (profile) {
                            P2pTorrentProfile.SOFT ->
                                stringResource(Res.string.settings_p2p_profile_soft_description)
                            P2pTorrentProfile.BALANCED ->
                                stringResource(Res.string.settings_p2p_profile_balanced_description)
                            P2pTorrentProfile.FAST ->
                                stringResource(Res.string.settings_p2p_profile_fast_description)
                        }
                    },
                    onSelect = { profile ->
                        P2pSettingsRepository.setTorrentProfile(profile)
                        showP2pProfileDialog = false
                    },
                    onDismiss = { showP2pProfileDialog = false },
                )
            }

            if (showP2pCacheSizeDialog) {
                IosEnumSelectionDialog(
                    title = stringResource(Res.string.settings_p2p_cache_size_title),
                    options = P2pCacheSize.entries,
                    selected = p2pSettings.cacheSize,
                    label = { size ->
                        when (size) {
                            P2pCacheSize.NONE -> stringResource(Res.string.settings_p2p_cache_none)
                            P2pCacheSize.GB_2 -> stringResource(Res.string.settings_p2p_cache_2_gb)
                            P2pCacheSize.GB_5 -> stringResource(Res.string.settings_p2p_cache_5_gb)
                            P2pCacheSize.GB_10 -> stringResource(Res.string.settings_p2p_cache_10_gb)
                            P2pCacheSize.GB_20 -> stringResource(Res.string.settings_p2p_cache_20_gb)
                            P2pCacheSize.GB_50 -> stringResource(Res.string.settings_p2p_cache_50_gb)
                        }
                    },
                    onSelect = { size ->
                        P2pSettingsRepository.setCacheSize(size)
                        p2pCacheClearResult = null
                        showP2pCacheSizeDialog = false
                    },
                    onDismiss = { showP2pCacheSizeDialog = false },
                )
            }
        }
    }
}
