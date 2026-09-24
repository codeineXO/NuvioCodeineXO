package com.nuvio.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.rememberModalBottomSheetState
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
import nuvio.composeapp.generated.resources.action_cancel
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
import nuvio.composeapp.generated.resources.settings_advanced_discord_rich_presence
import nuvio.composeapp.generated.resources.settings_advanced_discord_rich_presence_description
import nuvio.composeapp.generated.resources.settings_advanced_opengl_renderer
import nuvio.composeapp.generated.resources.settings_advanced_opengl_renderer_description
import nuvio.composeapp.generated.resources.settings_advanced_opengl_renderer_external_description
import nuvio.composeapp.generated.resources.settings_advanced_remember_last_profile
import nuvio.composeapp.generated.resources.settings_advanced_remember_last_profile_description
import nuvio.composeapp.generated.resources.settings_advanced_section_cache
import nuvio.composeapp.generated.resources.settings_advanced_section_diagnostics
import nuvio.composeapp.generated.resources.settings_advanced_section_discord
import nuvio.composeapp.generated.resources.settings_advanced_section_startup
import nuvio.composeapp.generated.resources.settings_advanced_section_windows_graphics
import nuvio.composeapp.generated.resources.settings_advanced_sentry_reports
import nuvio.composeapp.generated.resources.settings_advanced_sentry_reports_subtitle
import nuvio.composeapp.generated.resources.settings_advanced_sentry_reports_subtitle_desktop
import nuvio.composeapp.generated.resources.sentry_disable_dialog_subtitle
import nuvio.composeapp.generated.resources.sentry_disable_dialog_subtitle_desktop
import nuvio.composeapp.generated.resources.sentry_disable_dialog_title
import nuvio.composeapp.generated.resources.sentry_enable_dialog_subtitle
import nuvio.composeapp.generated.resources.sentry_enable_dialog_subtitle_desktop
import nuvio.composeapp.generated.resources.sentry_enable_dialog_title
import nuvio.composeapp.generated.resources.sentry_help_body
import nuvio.composeapp.generated.resources.sentry_help_body_desktop
import nuvio.composeapp.generated.resources.sentry_help_title
import nuvio.composeapp.generated.resources.sentry_keep_enabled
import nuvio.composeapp.generated.resources.sentry_not_sent_body
import nuvio.composeapp.generated.resources.sentry_not_sent_title
import nuvio.composeapp.generated.resources.sentry_sent_body
import nuvio.composeapp.generated.resources.sentry_sent_body_desktop
import nuvio.composeapp.generated.resources.sentry_sent_title
import nuvio.composeapp.generated.resources.sentry_turn_off
import nuvio.composeapp.generated.resources.sentry_turn_on
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
    if (SentrySettingsRepository.isSupported) {
        item {
            val sentryEnabledFlow = remember {
                SentrySettingsRepository.ensureLoaded()
                SentrySettingsRepository.enabled
            }
            val sentryEnabled by sentryEnabledFlow.collectAsStateWithLifecycle()
            var showSentryDialog by rememberSaveable { mutableStateOf(false) }

            SettingsSection(
                title = stringResource(Res.string.settings_advanced_section_diagnostics),
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_advanced_sentry_reports),
                        description = stringResource(
                            if (SentrySettingsPlatform.usesDesktopCopy) {
                                Res.string.settings_advanced_sentry_reports_subtitle_desktop
                            } else {
                                Res.string.settings_advanced_sentry_reports_subtitle
                            },
                        ),
                        checked = sentryEnabled,
                        isTablet = isTablet,
                        onCheckedChange = { showSentryDialog = true },
                    )
                }
            }

            if (showSentryDialog) {
                SentrySettingsDialog(
                    enabled = sentryEnabled,
                    onConfirm = {
                        SentrySettingsRepository.setEnabled(!sentryEnabled)
                    },
                    onDismiss = {
                        showSentryDialog = false
                    },
                )
            }
        }
    }
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SentrySettingsDialog(
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = tokens.colors.surfaceDialog,
            shape = tokens.shapes.dialog,
        ) {
            Column(
                modifier = Modifier.padding(tokens.spacing.dialogPadding),
            ) {
                Text(
                    text = stringResource(
                        if (enabled) {
                            Res.string.sentry_disable_dialog_title
                        } else {
                            Res.string.sentry_enable_dialog_title
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(tokens.spacing.controlGap))
                Text(
                    text = stringResource(
                        when {
                            enabled && SentrySettingsPlatform.usesDesktopCopy -> {
                                Res.string.sentry_disable_dialog_subtitle_desktop
                            }
                            enabled -> Res.string.sentry_disable_dialog_subtitle
                            SentrySettingsPlatform.usesDesktopCopy -> {
                                Res.string.sentry_enable_dialog_subtitle_desktop
                            }
                            else -> Res.string.sentry_enable_dialog_subtitle
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.colors.textMuted,
                )
                Spacer(modifier = Modifier.height(NuvioTokens.Space.s18))
                Column(
                    verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
                ) {
                    SentryInfoSection(
                        title = stringResource(Res.string.sentry_help_title),
                        body = stringResource(
                            if (SentrySettingsPlatform.usesDesktopCopy) {
                                Res.string.sentry_help_body_desktop
                            } else {
                                Res.string.sentry_help_body
                            },
                        ),
                    )
                    SentryInfoSection(
                        title = stringResource(Res.string.sentry_sent_title),
                        body = stringResource(
                            if (SentrySettingsPlatform.usesDesktopCopy) {
                                Res.string.sentry_sent_body_desktop
                            } else {
                                Res.string.sentry_sent_body
                            },
                        ),
                    )
                    SentryInfoSection(
                        title = stringResource(Res.string.sentry_not_sent_title),
                        body = stringResource(Res.string.sentry_not_sent_body),
                    )
                }
                Spacer(modifier = Modifier.height(NuvioTokens.Space.s18))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = tokens.shapes.button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.colors.surfaceCard,
                            contentColor = tokens.colors.textPrimary,
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                if (enabled) {
                                    Res.string.sentry_keep_enabled
                                } else {
                                    Res.string.action_cancel
                                },
                            ),
                        )
                    }
                    Spacer(modifier = Modifier.width(NuvioTokens.Space.s10))
                    Button(
                        onClick = {
                            onConfirm()
                            onDismiss()
                        },
                        shape = tokens.shapes.button,
                    ) {
                        Text(
                            text = stringResource(
                                if (enabled) {
                                    Res.string.sentry_turn_off
                                } else {
                                    Res.string.sentry_turn_on
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SentryInfoSection(
    title: String,
    body: String,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscordActivityModeBottomSheet(
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
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
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
                        .clickable {
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
