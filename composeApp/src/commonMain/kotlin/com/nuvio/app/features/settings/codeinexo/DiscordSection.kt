package com.nuvio.app.features.settings.codeinexo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.settings.DiscordActivityMode
import com.nuvio.app.features.settings.DiscordActivityModeBottomSheet
import com.nuvio.app.features.settings.DiscordActivityType
import com.nuvio.app.features.settings.DiscordActivityTypeBottomSheet
import com.nuvio.app.features.settings.DiscordRichPresenceRepository
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DiscordSection(isTablet: Boolean) {
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
    val episodeSmallImageEnabled by DiscordRichPresenceRepository.episodeSmallImageEnabled.collectAsStateWithLifecycle()
    val episodeNameEnabled by DiscordRichPresenceRepository.episodeNameEnabled.collectAsStateWithLifecycle()
    var showActivityModeSheet by rememberSaveable { mutableStateOf(false) }
    var showActivityTypeSheet by rememberSaveable { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(CodeineXOSection.Discord.titleRes),
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
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_advanced_discord_episode_small_image),
                    description = stringResource(Res.string.settings_advanced_discord_episode_small_image_description),
                    checked = episodeSmallImageEnabled,
                    isTablet = isTablet,
                    onCheckedChange = DiscordRichPresenceRepository::setEpisodeSmallImageEnabled,
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_advanced_discord_episode_name),
                    description = stringResource(Res.string.settings_advanced_discord_episode_name_description),
                    checked = episodeNameEnabled,
                    isTablet = isTablet,
                    onCheckedChange = DiscordRichPresenceRepository::setEpisodeNameEnabled,
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
