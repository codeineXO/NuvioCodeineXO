package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.player.HlsMaxResolutionOptions
import com.nuvio.app.features.player.PlayerSettingsRepository
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Max starting resolution for HLS streams; shown in Playback → Video and the CodeineXO player section. */
@Composable
internal fun HlsMaxResolutionSetting(isTablet: Boolean) {
    val settings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    SettingsNavigationRow(
        title = stringResource(Res.string.hls_max_resolution_title),
        description = hlsMaxResolutionLabel(settings.hlsMaxResolution) + " · " +
            stringResource(Res.string.hls_max_resolution_description),
        isTablet = isTablet,
        onClick = { showDialog = true },
    )
    if (showDialog) {
        SettingsChoiceDialog(
            title = stringResource(Res.string.hls_max_resolution_title),
            options = HlsMaxResolutionOptions,
            selected = settings.hlsMaxResolution,
            label = { hlsMaxResolutionLabel(it) },
            onSelect = {
                PlayerSettingsRepository.setHlsMaxResolution(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun hlsMaxResolutionLabel(height: Int): String =
    if (height <= 0) stringResource(Res.string.hls_quality_best) else "${height}p"
