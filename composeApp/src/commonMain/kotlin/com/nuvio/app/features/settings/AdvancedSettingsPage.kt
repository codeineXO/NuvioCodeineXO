package com.nuvio.app.features.settings

import com.nuvio.app.core.ui.NuvioLazyColumn

import com.nuvio.app.core.ui.remoteNavigationTarget
import com.nuvio.app.core.ui.remoteNavigationGroup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingEnrichmentCache
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_advanced_clear_cw_cache
import nuvio.composeapp.generated.resources.settings_advanced_clear_cw_cache_done
import nuvio.composeapp.generated.resources.settings_advanced_clear_cw_cache_subtitle
import nuvio.composeapp.generated.resources.cd_selected
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_mode
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_mode_all
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_mode_all_desc
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_mode_only_watching
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_mode_only_watching_desc
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_mode_sheet_title
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_type
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_type_description
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_type_playing
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_type_playing_desc
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_type_sheet_title
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_type_watching
import nuvio.composeapp.generated.resources.settings_advanced_discord_activity_type_watching_desc
import nuvio.composeapp.generated.resources.settings_advanced_discord_episode_small_image
import nuvio.composeapp.generated.resources.settings_advanced_discord_episode_small_image_description
import nuvio.composeapp.generated.resources.settings_advanced_discord_episode_name
import nuvio.composeapp.generated.resources.settings_advanced_discord_episode_name_description
import nuvio.composeapp.generated.resources.settings_advanced_discord_full_poster
import nuvio.composeapp.generated.resources.settings_advanced_discord_full_poster_description
import nuvio.composeapp.generated.resources.settings_advanced_discord_rich_presence
import nuvio.composeapp.generated.resources.settings_advanced_discord_rich_presence_description
import nuvio.composeapp.generated.resources.settings_advanced_opengl_renderer
import nuvio.composeapp.generated.resources.settings_advanced_opengl_renderer_description
import nuvio.composeapp.generated.resources.settings_advanced_opengl_renderer_external_description
import nuvio.composeapp.generated.resources.settings_advanced_remember_last_profile
import nuvio.composeapp.generated.resources.settings_advanced_remember_last_profile_description
import nuvio.composeapp.generated.resources.settings_advanced_section_cache
import nuvio.composeapp.generated.resources.settings_advanced_section_discord
import nuvio.composeapp.generated.resources.settings_advanced_section_startup
import nuvio.composeapp.generated.resources.settings_advanced_section_windows_graphics
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.advancedSettingsContent(
    isTablet: Boolean,
    rememberLastProfileEnabled: Boolean,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_advanced_section_startup),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_advanced_remember_last_profile),
                    description = stringResource(Res.string.settings_advanced_remember_last_profile_description),
                    checked = rememberLastProfileEnabled,
                    isTablet = isTablet,
                    onCheckedChange = ProfileRepository::setRememberLastProfileEnabled,
                )
            }
        }
    }
    if (DesktopRendererSettings.isSupported) {
        item {
            val externallyControlled = remember { DesktopRendererSettings.isExternallyControlled }
            var useOpenGl by remember { mutableStateOf(DesktopRendererSettings.useOpenGl) }

            SettingsSection(
                title = stringResource(Res.string.settings_advanced_section_windows_graphics),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_advanced_opengl_renderer),
                        description = stringResource(
                            if (externallyControlled) {
                                Res.string.settings_advanced_opengl_renderer_external_description
                            } else {
                                Res.string.settings_advanced_opengl_renderer_description
                            },
                        ),
                        checked = useOpenGl,
                        enabled = !externallyControlled,
                        isTablet = isTablet,
                        onCheckedChange = { enabled ->
                            DesktopRendererSettings.setUseOpenGl(enabled)
                            useOpenGl = enabled
                        },
                    )
                }
            }
        }
    }
    debugLoggingSettingsItem(isTablet)
    if (DiscordRichPresenceRepository.isSupported) {
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
            val episodeSmallImageEnabled by DiscordRichPresenceRepository.episodeSmallImageEnabled.collectAsStateWithLifecycle()
            val episodeNameEnabled by DiscordRichPresenceRepository.episodeNameEnabled.collectAsStateWithLifecycle()
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
    }
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_advanced_section_cache),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                val scope = rememberCoroutineScope()
                var cleared by rememberSaveable { mutableStateOf(false) }
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_advanced_clear_cw_cache),
                    description = if (cleared) {
                        stringResource(Res.string.settings_advanced_clear_cw_cache_done)
                    } else {
                        stringResource(Res.string.settings_advanced_clear_cw_cache_subtitle)
                    },
                    isTablet = isTablet,
                    onClick = {
                        if (!cleared) {
                            ContinueWatchingEnrichmentCache.clearAll(ProfileRepository.activeProfileId)
                            cleared = true
                            scope.launch {
                                WatchProgressRepository.clearLocalAndForceSnapshotRefreshFromServer(
                                    ProfileRepository.activeProfileId,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscordActivityModeBottomSheet(
    selectedMode: DiscordActivityMode,
    onModeSelected: (DiscordActivityMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val modes = listOf(
        DiscordActivityMode.ALL to (
            Res.string.settings_advanced_discord_activity_mode_all to
                Res.string.settings_advanced_discord_activity_mode_all_desc
        ),
        DiscordActivityMode.ONLY_WATCHING to (
            Res.string.settings_advanced_discord_activity_mode_only_watching to
                Res.string.settings_advanced_discord_activity_mode_only_watching_desc
        ),
    )

    NuvioModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        NuvioLazyColumn(
            modifier = Modifier.remoteNavigationGroup().then(Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.settings_advanced_discord_activity_mode_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }

            itemsIndexed(modes) { index, (mode, stringRes) ->
                if (index > 0) {
                    NuvioBottomSheetDivider()
                }
                val (titleRes, descRes) = stringRes
                val tokens = MaterialTheme.nuvio
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .remoteNavigationTarget().clickable {
                            onModeSelected(mode)
                            coroutineScope.launch {
                                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                            }
                        }
                        .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s14),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.colors.textPrimary,
                        )
                        Text(
                            text = stringResource(descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textMuted,
                        )
                    }
                    if (mode == selectedMode) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(Res.string.cd_selected),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscordActivityTypeBottomSheet(
    selectedType: DiscordActivityType,
    onTypeSelected: (DiscordActivityType) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val types = listOf(
        DiscordActivityType.WATCHING to (
            Res.string.settings_advanced_discord_activity_type_watching to
                Res.string.settings_advanced_discord_activity_type_watching_desc
        ),
        DiscordActivityType.PLAYING to (
            Res.string.settings_advanced_discord_activity_type_playing to
                Res.string.settings_advanced_discord_activity_type_playing_desc
        ),
    )

    NuvioModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        NuvioLazyColumn(
            modifier = Modifier.remoteNavigationGroup().then(Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)),
        ) {
            item {
                Text(
                    text = stringResource(Res.string.settings_advanced_discord_activity_type_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }

            itemsIndexed(types) { index, (type, stringRes) ->
                if (index > 0) {
                    NuvioBottomSheetDivider()
                }
                val (titleRes, descRes) = stringRes
                val tokens = MaterialTheme.nuvio
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .remoteNavigationTarget().clickable {
                            onTypeSelected(type)
                            coroutineScope.launch {
                                dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                            }
                        }
                        .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s14),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.colors.textPrimary,
                        )
                        Text(
                            text = stringResource(descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.textMuted,
                        )
                    }
                    if (type == selectedType) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(Res.string.cd_selected),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
