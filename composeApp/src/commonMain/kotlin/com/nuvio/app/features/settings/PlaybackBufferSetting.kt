package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.features.player.PlaybackBufferPreset
import com.nuvio.app.features.player.PlayerSettingsRepository
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlaybackBufferSetting(isTablet: Boolean) {
    val settings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    PlaybackBufferSetting(isTablet, settings.playbackBufferPreset, PlayerSettingsRepository::setPlaybackBufferPreset)
}

@Composable
internal fun PlaybackBufferSetting(
    isTablet: Boolean,
    selectedPreset: PlaybackBufferPreset,
    onSelected: (PlaybackBufferPreset) -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    SettingsNavigationRow(
        title = stringResource(Res.string.playback_buffer_title),
        description = playbackBufferLabel(selectedPreset),
        isTablet = isTablet,
        onClick = { showDialog = true },
    )
    if (showDialog) {
        PlaybackBufferDialog(
            selectedPreset = selectedPreset,
            onSelected = {
                onSelected(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
internal fun playbackBufferLabel(preset: PlaybackBufferPreset): String = stringResource(
    when (preset) {
        PlaybackBufferPreset.LOW -> Res.string.playback_buffer_low
        PlaybackBufferPreset.MEDIUM -> Res.string.playback_buffer_medium
        PlaybackBufferPreset.LARGE -> Res.string.playback_buffer_large
    },
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PlaybackBufferDialog(
    selectedPreset: PlaybackBufferPreset,
    onSelected: (PlaybackBufferPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(onDismissRequest = onDismiss, title = stringResource(Res.string.playback_buffer_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.playback_buffer_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlaybackBufferPreset.entries.forEach { preset ->
                val description = when (preset) {
                    PlaybackBufferPreset.LOW -> Res.string.playback_buffer_low_description
                    PlaybackBufferPreset.MEDIUM -> Res.string.playback_buffer_medium_description
                    PlaybackBufferPreset.LARGE -> Res.string.playback_buffer_large_description
                }
                val sizes = when (preset) {
                    PlaybackBufferPreset.LOW -> Res.string.playback_buffer_low_sizes
                    PlaybackBufferPreset.MEDIUM -> Res.string.playback_buffer_medium_sizes
                    PlaybackBufferPreset.LARGE -> Res.string.playback_buffer_large_sizes
                }
                DialogOption(
                    text = playbackBufferLabel(preset),
                    description = stringResource(description) + "\n" + stringResource(sizes),
                    selected = preset == selectedPreset,
                    onClick = { onSelected(preset) },
                )
            }
            Text(
                stringResource(Res.string.playback_buffer_svp_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_done), onClick = onDismiss)
        }
    }
}
