package com.nuvio.app.features.settings.codeinexo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerUiMode
import com.nuvio.app.features.settings.IosEnumSelectionDialog
import com.nuvio.app.features.settings.SettingsDropdownRow
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import com.nuvio.app.features.settings.uiScaleDropdownOptions
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerInterfaceSection(isTablet: Boolean) {
    val playerSettings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val p2pSettings by remember {
        P2pSettingsRepository.ensureLoaded()
        P2pSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    var showPlayerUiDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSection(title = stringResource(CodeineXOSection.PlayerInterface.titleRes), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.settings_playback_player_ui),
                description = stringResource(playerSettings.playerUiMode.labelRes),
                isTablet = isTablet,
                onClick = { showPlayerUiDialog = true },
            )
            if (isDesktop) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsDropdownRow(
                    title = stringResource(Res.string.settings_playback_player_ui_scale),
                    description = stringResource(Res.string.settings_playback_player_ui_scale_description),
                    selectedKey = playerSettings.playerUiScale.toString(),
                    selectedLabel = "${playerSettings.playerUiScale}%",
                    options = uiScaleDropdownOptions,
                    isTablet = isTablet,
                    onSelected = { option -> option.key.toIntOrNull()?.let(PlayerSettingsRepository::setPlayerUiScale) },
                )
            }
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_playback_show_clock_in_player),
                description = stringResource(Res.string.settings_playback_show_clock_in_player_description),
                checked = playerSettings.showClockInPlayer,
                isTablet = isTablet,
                onCheckedChange = PlayerSettingsRepository::setShowClockInPlayer,
            )
            if (P2pSettingsRepository.isVisible) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_p2p_show_stats_title),
                    description = stringResource(Res.string.settings_p2p_show_stats_subtitle),
                    checked = p2pSettings.showTorrentStatsOverlay,
                    isTablet = isTablet,
                    onCheckedChange = P2pSettingsRepository::setShowTorrentStatsOverlay,
                )
            }
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_playback_show_seekbar_while_seeking),
                description = stringResource(Res.string.settings_playback_show_seekbar_while_seeking_description),
                checked = playerSettings.showSeekbarWhileSeeking,
                isTablet = isTablet,
                onCheckedChange = PlayerSettingsRepository::setShowSeekbarWhileSeeking,
            )
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
}

private val EyeFilterOpacityOptions = listOf(0.10f, 0.20f, 0.30f, 0.35f, 0.40f, 0.50f, 0.60f, 0.70f, 0.80f)
private const val DefaultEyeFilterOpacity = 0.35f

@Composable
internal fun ViewingComfortSection(isTablet: Boolean) {
    val playerSettings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    var showEyeFilterColorDialog by rememberSaveable { mutableStateOf(false) }
    var showEyeFilterOpacityDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSection(title = stringResource(CodeineXOSection.ViewingComfort.titleRes), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.xo_eye_filter_title),
                description = stringResource(Res.string.xo_eye_filter_description),
                checked = playerSettings.eyeFilterEnabled,
                isTablet = isTablet,
                onCheckedChange = PlayerSettingsRepository::setEyeFilterEnabled,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.xo_eye_filter_color),
                description = when (playerSettings.eyeFilterColor.uppercase()) {
                    "#000000" -> stringResource(Res.string.xo_eye_filter_color_black)
                    "#0F172A", "#1C2833" -> stringResource(Res.string.xo_eye_filter_color_deep_navy)
                    "BW", "#BW" -> stringResource(Res.string.xo_eye_filter_color_black_white)
                    else -> stringResource(Res.string.xo_eye_filter_color_custom, playerSettings.eyeFilterColor)
                },
                isTablet = isTablet,
                onClick = { showEyeFilterColorDialog = true },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.xo_eye_filter_opacity),
                description = "${(playerSettings.eyeFilterOpacity * 100).toInt()}%",
                isTablet = isTablet,
                onClick = { showEyeFilterOpacityDialog = true },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.xo_frosted_matte_title),
                description = stringResource(Res.string.xo_frosted_matte_description),
                checked = playerSettings.grainFilterEnabled,
                isTablet = isTablet,
                onCheckedChange = PlayerSettingsRepository::setGrainFilterEnabled,
            )
        }
    }

    if (showEyeFilterColorDialog) {
        val colorOptions = listOf(
            "#000000" to stringResource(Res.string.xo_eye_filter_color_black),
            "#0F172A" to stringResource(Res.string.xo_eye_filter_color_deep_navy),
            "BW" to stringResource(Res.string.xo_eye_filter_color_black_white),
        )
        IosEnumSelectionDialog(
            title = stringResource(Res.string.xo_eye_filter_color),
            options = colorOptions.map { it.first },
            selected = colorOptions.firstOrNull { it.first.equals(playerSettings.eyeFilterColor, ignoreCase = true) }?.first ?: "#000000",
            label = { hex -> colorOptions.firstOrNull { it.first == hex }?.second ?: hex },
            description = { hex -> hex },
            onSelect = { hex ->
                PlayerSettingsRepository.setEyeFilterColor(hex)
                showEyeFilterColorDialog = false
            },
            onDismiss = { showEyeFilterColorDialog = false },
        )
    }

    if (showEyeFilterOpacityDialog) {
        IosEnumSelectionDialog(
            title = stringResource(Res.string.xo_eye_filter_opacity),
            options = EyeFilterOpacityOptions,
            selected = EyeFilterOpacityOptions.minByOrNull { kotlin.math.abs(it - playerSettings.eyeFilterOpacity) } ?: DefaultEyeFilterOpacity,
            label = { opacity ->
                val percent = "${(opacity * 100).toInt()}%"
                if (opacity == DefaultEyeFilterOpacity) stringResource(Res.string.xo_eye_filter_opacity_default, percent) else percent
            },
            description = { "" },
            onSelect = { opacity ->
                PlayerSettingsRepository.setEyeFilterOpacity(opacity)
                showEyeFilterOpacityDialog = false
            },
            onDismiss = { showEyeFilterOpacityDialog = false },
        )
    }
}
