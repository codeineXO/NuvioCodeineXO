package com.nuvio.app.features.settings.codeinexo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.p2p.P2pCacheClearResult
import com.nuvio.app.features.p2p.P2pCacheSize
import com.nuvio.app.features.p2p.P2pConsentDialog
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.p2p.P2pStreamingEngine
import com.nuvio.app.features.p2p.P2pStreamingState
import com.nuvio.app.features.p2p.P2pTorrentProfile
import com.nuvio.app.features.settings.IosEnumSelectionDialog
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun P2pSection(isTablet: Boolean) {
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
        title = stringResource(CodeineXOSection.P2p.titleRes),
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
