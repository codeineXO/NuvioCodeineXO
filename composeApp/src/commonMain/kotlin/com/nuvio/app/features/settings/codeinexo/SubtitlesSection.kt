package com.nuvio.app.features.settings.codeinexo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.SubtitleColorEditTarget
import com.nuvio.app.features.player.SubtitleColorSwatches
import com.nuvio.app.features.player.SubtitleOutlineColorSwatches
import com.nuvio.app.features.player.SubtitleOutlineEffect
import com.nuvio.app.features.player.SubtitleOutlineWidths
import com.nuvio.app.features.player.effectColor
import com.nuvio.app.features.player.outlineWidthLabel
import com.nuvio.app.features.player.selectFont
import com.nuvio.app.features.player.subtitleFontLabel
import com.nuvio.app.features.player.withEffectColor
import com.nuvio.app.features.player.withEffectOpacity
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsNavigationRow
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSliderRow
import com.nuvio.app.features.settings.SubtitleColorDialog
import com.nuvio.app.features.settings.SubtitleFontDialog
import com.nuvio.app.features.settings.SubtitleOutlineEffectDialog
import com.nuvio.app.features.settings.subtitleColorLabel
import kotlin.math.roundToInt
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SubtitlesSection(isTablet: Boolean) {
    val playerSettings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    var showSubtitleFontDialog by rememberSaveable { mutableStateOf(false) }
    var showSubtitleOutlineEffectDialog by rememberSaveable { mutableStateOf(false) }
    var showSubtitleTextColorDialog by rememberSaveable { mutableStateOf(false) }
    var showSubtitleOutlineColorDialog by rememberSaveable { mutableStateOf(false) }

    val subtitleStyle = playerSettings.subtitleStyle
    val isBackgroundBox = subtitleStyle.outlineEffect == SubtitleOutlineEffect.BACKGROUND_BOX

    SettingsSection(
        title = stringResource(CodeineXOSection.Subtitles.titleRes),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.xo_subtitle_font),
                description = subtitleFontLabel(subtitleStyle.importedFontId ?: subtitleStyle.fontName),
                isTablet = isTablet,
                onClick = { showSubtitleFontDialog = true },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.settings_playback_subtitle_text_color),
                description = subtitleColorLabel(subtitleStyle.textColor),
                isTablet = isTablet,
                onClick = { showSubtitleTextColorDialog = true },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.xo_outline_style),
                description = subtitleStyle.outlineEffect.label,
                isTablet = isTablet,
                onClick = { showSubtitleOutlineEffectDialog = true },
            )
            if (isBackgroundBox || (subtitleStyle.outlineEnabled && subtitleStyle.outlineEffect.needsOutline)) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSliderRow(
                    title = stringResource(Res.string.xo_outline_thickness),
                    enabled = !isBackgroundBox,
                    value = SubtitleOutlineWidths.indexOf(subtitleStyle.outlineWidth).coerceAtLeast(0),
                    valueText = { "${SubtitleOutlineWidths[it].outlineWidthLabel()} px" },
                    valueRange = SubtitleOutlineWidths.indices,
                    step = 1,
                    isTablet = isTablet,
                    onValueChange = { value ->
                        PlayerSettingsRepository.setSubtitleStyle(subtitleStyle.copy(outlineWidth = SubtitleOutlineWidths[value]))
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(if (isBackgroundBox) Res.string.compose_player_box_color else Res.string.settings_playback_subtitle_outline_color),
                    description = subtitleColorLabel(subtitleStyle.effectColor),
                    isTablet = isTablet,
                    onClick = { showSubtitleOutlineColorDialog = true },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSliderRow(
                    title = stringResource(if (isBackgroundBox) Res.string.compose_player_box_opacity else Res.string.compose_player_outline_opacity),
                    value = (subtitleStyle.effectColor.alpha * 100f).roundToInt(),
                    valueText = { "$it%" },
                    valueRange = 0..100,
                    step = 10,
                    isTablet = isTablet,
                    onValueChange = { value ->
                        PlayerSettingsRepository.setSubtitleStyle(subtitleStyle.withEffectOpacity(value))
                    },
                )
            }
        }
    }

    if (showSubtitleFontDialog) {
        SubtitleFontDialog(
            selectedFont = playerSettings.subtitleStyle.importedFontId ?: playerSettings.subtitleStyle.fontName,
            onFontSelected = { font ->
                PlayerSettingsRepository.setSubtitleStyle(playerSettings.subtitleStyle.selectFont(font))
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
            title = stringResource(if (isBackgroundBox) Res.string.compose_player_box_color else Res.string.settings_playback_subtitle_outline_color),
            colors = SubtitleOutlineColorSwatches,
            selectedColor = playerSettings.subtitleStyle.effectColor,
            previewStyle = playerSettings.subtitleStyle,
            target = if (isBackgroundBox) SubtitleColorEditTarget.BACKGROUND else SubtitleColorEditTarget.OUTLINE,
            onColorSelected = { color ->
                PlayerSettingsRepository.setSubtitleStyle(playerSettings.subtitleStyle.withEffectColor(color))
            },
            onDismiss = { showSubtitleOutlineColorDialog = false },
        )
    }
}
