package com.nuvio.app.features.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import com.nuvio.app.core.ui.AppBasicAlertDialog

import com.nuvio.app.core.ui.remoteNavigationTarget
import com.nuvio.app.core.ui.remoteNavigationModal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import com.nuvio.app.core.ui.nuvioVerticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import com.nuvio.app.core.ui.PlatformBackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.nuvio.app.core.ui.SettingsAwareSurface
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.player.AnimeUpscalerMode
import com.nuvio.app.features.player.AnimeUpscalerOption
import com.nuvio.app.features.player.SvpMotionOption
import com.nuvio.app.features.player.SvpRuntimeStatus
import com.nuvio.app.features.player.SvpRuntimeInstaller
import com.nuvio.app.features.player.SvpRuntimeDownloadState
import com.nuvio.app.features.player.SubtitleColorEditTarget
import com.nuvio.app.features.player.SubtitleCustomColorPicker
import com.nuvio.app.features.player.SubtitleOutlineEffect
import com.nuvio.app.features.player.SubtitleStyleState
import nuvio.composeapp.generated.resources.action_done
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AnimeUpscalerOptionDialog(
    selectedOption: AnimeUpscalerOption,
    onOptionSelected: (AnimeUpscalerOption) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = "Anime Upscaler",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimeUpscalerOption.entries.forEach { option ->
                val isSelected = option == selectedOption
                DialogOption(
                    text = option.label,
                    description = when (option) {
                        AnimeUpscalerOption.AUTO -> "Enhance detected anime and cartoons; bypass live action"
                        AnimeUpscalerOption.ALWAYS_ON -> "Force Anime4K upscaler shaders on all content"
                        AnimeUpscalerOption.OFF -> "Disable real-time upscaler shaders"
                    },
                    selected = isSelected,
                    onClick = { onOptionSelected(option) },
                )
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SvpMotionOptionDialog(
    selectedOption: SvpMotionOption,
    onOptionSelected: (SvpMotionOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val runtimeState by SvpRuntimeInstaller.state.collectAsStateWithLifecycle()
    val isInstalled = runtimeState is SvpRuntimeDownloadState.Installed

    DialogSurface(
        onDismissRequest = onDismiss,
        title = "Motion Interpolation (SVP)",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.motion_smoothing_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (SvpRuntimeInstaller.isSupported) {
                SvpRuntimeStatus(
                    state = runtimeState,
                    onRetry = { SvpRuntimeInstaller.startDownload() },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else {
                Text(
                    text = "Requires VapourSynth or SVP 4 to be installed on your system.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            SvpMotionOption.entries.forEach { option ->
                DialogOption(
                    text = option.label,
                    description = option.description,
                    selected = option == selectedOption,
                    enabled = !SvpRuntimeInstaller.isSupported || isInstalled || option == SvpMotionOption.OFF,
                    onClick = { onOptionSelected(option) },
                )
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AnimeUpscalerModeDialog(
    selectedMode: AnimeUpscalerMode,
    onModeSelected: (AnimeUpscalerMode) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = "Anime Upscaler Mode",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimeUpscalerMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                DialogOption(
                    text = mode.label,
                    description = mode.description,
                    selected = isSelected,
                    onClick = { onModeSelected(mode) },
                )
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SubtitleColorDialog(
    title: String,
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit,
    previewStyle: SubtitleStyleState? = null,
    showAlpha: Boolean = false,
    target: SubtitleColorEditTarget = SubtitleColorEditTarget.TEXT,
) {
    var currentColor by remember(selectedColor) { mutableStateOf(selectedColor) }
    PlatformBackHandler(enabled = true, onBack = onDismiss)

    AppBasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        SettingsAwareSurface(
            modifier = Modifier
                .fillMaxWidth()
                .remoteNavigationModal()
                .widthIn(max = 520.dp),
            panel = true,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .nuvioVerticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )

                SubtitleCustomColorPicker(
                    selectedColor = currentColor,
                    onColorChanged = { color ->
                        currentColor = color
                        onColorSelected(color)
                    },
                    swatches = colors,
                    showAlpha = showAlpha,
                    previewStyle = previewStyle,
                    target = target,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.remoteNavigationTarget(shape = androidx.compose.material3.ButtonDefaults.shape)) {
                        Text(stringResource(Res.string.action_done))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SubtitleFontDialog(selectedFont: String, onFontSelected: (String) -> Unit, onDismiss: () -> Unit) {
    com.nuvio.app.features.player.SubtitleFontPicker(selectedFont, onFontSelected, onDismiss)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SubtitleOutlineEffectDialog(
    selectedEffect: SubtitleOutlineEffect,
    onEffectSelected: (SubtitleOutlineEffect) -> Unit,
    onDismiss: () -> Unit,
) {
    PlatformBackHandler(enabled = true, onBack = onDismiss)
    val effectDescriptions = mapOf(
        SubtitleOutlineEffect.OUTLINE to "Clean sharp border with adjustable thickness",
        SubtitleOutlineEffect.SOFT_GLOW to "Soft blurred glow around text edges",
        SubtitleOutlineEffect.OUTLINE_AND_SHADOW to "Outline with feathered shadow — natural Netflix-style",
        SubtitleOutlineEffect.BACKGROUND_BOX to "Semi-transparent dark box behind subtitles",
        SubtitleOutlineEffect.NONE to "Plain text without outline or shadow",
    )

    AppBasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        SettingsAwareSurface(
            modifier = Modifier
                .fillMaxWidth()
                .remoteNavigationModal()
                .widthIn(max = 500.dp),
            panel = true,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .nuvioVerticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Outline Style",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )

                SubtitleOutlineEffect.entries.forEach { effect ->
                    val isSelected = effect == selectedEffect
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .remoteNavigationTarget(shape = RoundedCornerShape(12.dp)).clickable { onEffectSelected(effect) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = effect.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                                effectDescriptions[effect]?.let { desc ->
                                    Text(
                                        text = desc,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.remoteNavigationTarget(shape = androidx.compose.material3.ButtonDefaults.shape)) {
                        Text(stringResource(Res.string.action_done))
                    }
                }
            }
        }
    }
}


@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun <T> IosEnumSelectionDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    description: @Composable (T) -> String? = { null },
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = title,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val optionDescription = description(option)
                DialogOption(
                    text = label(option),
                    description = optionDescription,
                    selected = isSelected,
                    onClick = { onSelect(option) },
                )
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
            )
        }
    }
}
