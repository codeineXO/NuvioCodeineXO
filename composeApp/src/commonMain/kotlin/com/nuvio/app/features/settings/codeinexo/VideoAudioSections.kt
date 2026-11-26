package com.nuvio.app.features.settings.codeinexo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.player.AnimeUpscalerOption
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.VideoEnhancementsHardwareNote
import com.nuvio.app.features.settings.AnimeUpscalerModeDialog
import com.nuvio.app.features.settings.AnimeUpscalerOptionDialog
import com.nuvio.app.features.settings.HlsMaxResolutionSetting
import com.nuvio.app.features.settings.LocalWindowsMpvConfigRow
import com.nuvio.app.features.settings.PlaybackBufferSetting
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import com.nuvio.app.features.settings.SvpMotionOptionDialog
import com.nuvio.app.isWindows
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlaybackEngineSection(isTablet: Boolean) {
    SettingsSection(title = stringResource(CodeineXOSection.PlaybackEngine.titleRes), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            PlaybackBufferSetting(isTablet)
            SettingsGroupDivider(isTablet = isTablet)
            HlsMaxResolutionSetting(isTablet)
        }
    }
}

@Composable
internal fun VideoEnhancementsSection(isTablet: Boolean) {
    val playerSettings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    var showAnimeUpscalerOptionDialog by rememberSaveable { mutableStateOf(false) }
    var showAnimeUpscalerModeDialog by rememberSaveable { mutableStateOf(false) }
    var showSvpMotionOptionDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSection(title = stringResource(CodeineXOSection.Video.titleRes), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            VideoEnhancementsHardwareNote()
            SettingsNavigationRow(
                title = stringResource(Res.string.xo_anime_upscaler),
                description = playerSettings.animeUpscalerOption.label,
                isTablet = isTablet,
                onClick = { showAnimeUpscalerOptionDialog = true },
            )
            if (playerSettings.animeUpscalerOption != AnimeUpscalerOption.OFF) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.xo_upscaler_mode),
                    description = playerSettings.animeUpscalerMode.label,
                    isTablet = isTablet,
                    onClick = { showAnimeUpscalerModeDialog = true },
                )
            }
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.xo_motion_interpolation),
                description = playerSettings.svpMotionOption.label,
                isTablet = isTablet,
                onClick = { showSvpMotionOptionDialog = true },
            )
            if (isWindows) {
                SettingsGroupDivider(isTablet = isTablet)
                LocalWindowsMpvConfigRow.current(isTablet, !playerSettings.externalPlayerEnabled)
            }
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

@Composable
internal fun AudioEnhancementSection(isTablet: Boolean) {
    val playerSettings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    SettingsSection(title = stringResource(CodeineXOSection.Audio.titleRes), isTablet = isTablet) {
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
