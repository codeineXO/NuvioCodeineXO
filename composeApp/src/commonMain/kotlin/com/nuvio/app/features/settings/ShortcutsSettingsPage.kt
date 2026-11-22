package com.nuvio.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.party.ConfirmDialog
import com.nuvio.app.features.player.shortcuts.KeyChord
import com.nuvio.app.features.player.shortcuts.PlayerShortcutAction
import com.nuvio.app.features.player.shortcuts.PlayerShortcutCapture
import com.nuvio.app.features.player.shortcuts.PlayerShortcutGroup
import com.nuvio.app.features.player.shortcuts.PlayerShortcutSettings
import com.nuvio.app.features.player.shortcuts.PlayerShortcutSteps
import com.nuvio.app.features.player.shortcuts.displayLabel
import com.nuvio.app.features.player.shortcuts.titleRes
import nuvio.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.shortcutsSettingsContent(isTablet: Boolean) {
    item(key = "shortcuts") { ShortcutsSettingsBody(isTablet) }
}

private data class ShortcutSlot(val action: PlayerShortcutAction, val slot: Int)

private data class ShortcutConflict(val target: ShortcutSlot, val chord: KeyChord, val holder: PlayerShortcutAction)

@Composable
private fun ShortcutsSettingsBody(isTablet: Boolean) {
    val keymap by PlayerShortcutSettings.keymap.collectAsState()
    val steps by PlayerShortcutSettings.steps.collectAsState()
    var capturing by remember { mutableStateOf<ShortcutSlot?>(null) }
    var conflict by remember { mutableStateOf<ShortcutConflict?>(null) }
    var confirmResetAll by remember { mutableStateOf(false) }
    val tokens = MaterialTheme.nuvio
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) { onDispose { PlayerShortcutCapture.cancel() } }

    fun startCapture(target: ShortcutSlot) {
        PlayerShortcutCapture.cancel()
        capturing = target
        PlayerShortcutCapture.begin { chord ->
            capturing = null
            when {
                chord == null -> Unit
                chord.isReserved -> scope.launch {
                    NuvioToastController.show(getString(Res.string.settings_shortcuts_reserved, chord.displayLabel()))
                }
                else -> {
                    val current = PlayerShortcutSettings.keymap.value
                    val holder = current.conflict(chord, target.action, target.slot)
                    if (holder != null && holder.first != target.action) {
                        conflict = ShortcutConflict(target, chord, holder.first)
                    } else {
                        PlayerShortcutSettings.setKeymap(current.assign(target.action, target.slot, chord))
                    }
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(if (isTablet) 20.dp else 16.dp)) {
        Text(
            text = stringResource(Res.string.settings_shortcuts_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        PlayerShortcutGroup.entries.forEach { group ->
            SettingsSection(title = stringResource(group.titleRes).uppercase(), isTablet = isTablet) {
                SettingsGroup(isTablet = isTablet) {
                    val actions = PlayerShortcutAction.entries.filter { it.group == group }
                    actions.forEachIndexed { index, action ->
                        if (index > 0) SettingsGroupDivider(isTablet)
                        ShortcutRow(
                            action = action,
                            bindings = keymap.bindings(action),
                            customized = keymap.isCustomized(action),
                            capturingSlot = capturing?.takeIf { it.action == action }?.slot,
                            isTablet = isTablet,
                            onSlotClick = { slot -> startCapture(ShortcutSlot(action, slot)) },
                            onClear = { slot ->
                                PlayerShortcutSettings.setKeymap(PlayerShortcutSettings.keymap.value.assign(action, slot, null))
                            },
                            onReset = { PlayerShortcutSettings.setKeymap(PlayerShortcutSettings.keymap.value.reset(action)) },
                        )
                    }
                }
            }
        }
        ShortcutStepsSection(steps, isTablet)
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.settings_shortcuts_reset_all),
                description = null,
                icon = Icons.Rounded.RestartAlt,
                isTablet = isTablet,
                onClick = { confirmResetAll = true },
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    conflict?.let { pending ->
        ConfirmDialog(
            title = stringResource(Res.string.settings_shortcuts_conflict_title),
            message = stringResource(
                Res.string.settings_shortcuts_conflict_body,
                pending.chord.displayLabel(),
                stringResource(pending.holder.titleRes),
            ),
            confirmLabel = stringResource(Res.string.settings_shortcuts_swap),
            confirmStyle = DialogButtonStyle.Primary,
            onConfirm = {
                val current = PlayerShortcutSettings.keymap.value
                PlayerShortcutSettings.setKeymap(current.assign(pending.target.action, pending.target.slot, pending.chord))
                conflict = null
            },
            onDismiss = { conflict = null },
        )
    }
    if (confirmResetAll) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_shortcuts_reset_all_title),
            message = stringResource(Res.string.settings_shortcuts_reset_all_body),
            confirmLabel = stringResource(Res.string.action_reset),
            onConfirm = {
                PlayerShortcutSettings.resetAll()
                confirmResetAll = false
            },
            onDismiss = { confirmResetAll = false },
        )
    }
}

@Composable
private fun ShortcutRow(
    action: PlayerShortcutAction,
    bindings: List<KeyChord?>,
    customized: Boolean,
    capturingSlot: Int?,
    isTablet: Boolean,
    onSlotClick: (Int) -> Unit,
    onClear: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(action.titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textPrimary,
            fontWeight = if (customized) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        bindings.forEachIndexed { slot, chord ->
            ShortcutKeyChip(
                chord = chord,
                capturing = capturingSlot == slot,
                onClick = { onSlotClick(slot) },
                onClear = { onClear(slot) },
            )
        }
        val resetLabel = stringResource(Res.string.settings_shortcuts_reset_one)
        IconButton(onClick = onReset, enabled = customized, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = resetLabel,
                tint = if (customized) tokens.colors.textSecondary else tokens.colors.textMuted.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ShortcutKeyChip(
    chord: KeyChord?,
    capturing: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (capturing) tokens.colors.accent else tokens.colors.borderSubtle
    Row(
        modifier = Modifier
            .widthIn(min = 120.dp)
            .clip(shape)
            .background(if (capturing) tokens.colors.accent.copy(alpha = 0.15f) else tokens.colors.surfaceElevated)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = if (chord != null && !capturing) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                capturing -> stringResource(Res.string.settings_shortcuts_press_key)
                chord != null -> chord.displayLabel()
                else -> stringResource(Res.string.settings_shortcuts_add)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (chord != null || capturing) tokens.colors.textPrimary else tokens.colors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false).widthIn(min = 80.dp),
        )
        if (chord != null && !capturing) {
            val clearLabel = stringResource(Res.string.settings_shortcuts_clear)
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = clearLabel,
                tint = tokens.colors.textMuted,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable(onClick = onClear)
                    .padding(3.dp),
            )
        }
    }
}

@Composable
private fun ShortcutStepsSection(steps: PlayerShortcutSteps, isTablet: Boolean) {
    SettingsSection(title = stringResource(Res.string.settings_shortcuts_steps).uppercase(), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            val seconds: @Composable (Int) -> String = { ms ->
                val text = if (ms % 1000 == 0) (ms / 1000).toString() else (ms / 1000.0).toString()
                stringResource(Res.string.settings_shortcuts_seconds, text)
            }
            SettingsSliderRow(
                title = stringResource(Res.string.settings_shortcuts_step_seek_short),
                value = steps.seekShortMs,
                valueText = seconds,
                valueRange = PlayerShortcutSteps.SeekShortRange,
                step = 1_000,
                isTablet = isTablet,
                onValueChange = { PlayerShortcutSettings.setSteps(PlayerShortcutSettings.steps.value.copy(seekShortMs = it)) },
            )
            SettingsGroupDivider(isTablet)
            SettingsSliderRow(
                title = stringResource(Res.string.settings_shortcuts_step_seek_long),
                value = steps.seekLongMs,
                valueText = seconds,
                valueRange = PlayerShortcutSteps.SeekLongRange,
                step = 5_000,
                isTablet = isTablet,
                onValueChange = { PlayerShortcutSettings.setSteps(PlayerShortcutSettings.steps.value.copy(seekLongMs = it)) },
            )
            SettingsGroupDivider(isTablet)
            SettingsSliderRow(
                title = stringResource(Res.string.settings_shortcuts_step_seek_fine),
                value = steps.seekFineMs,
                valueText = seconds,
                valueRange = PlayerShortcutSteps.SeekFineRange,
                step = 500,
                isTablet = isTablet,
                onValueChange = { PlayerShortcutSettings.setSteps(PlayerShortcutSettings.steps.value.copy(seekFineMs = it)) },
            )
            SettingsGroupDivider(isTablet)
            SettingsSliderRow(
                title = stringResource(Res.string.settings_shortcuts_step_volume),
                value = steps.volumePercent,
                valueText = { stringResource(Res.string.settings_shortcuts_percent, it) },
                valueRange = PlayerShortcutSteps.VolumeRange,
                step = 1,
                isTablet = isTablet,
                onValueChange = { PlayerShortcutSettings.setSteps(PlayerShortcutSettings.steps.value.copy(volumePercent = it)) },
            )
            SettingsGroupDivider(isTablet)
            SettingsSliderRow(
                title = stringResource(Res.string.settings_shortcuts_step_subtitle_delay),
                value = steps.subtitleDelayMs,
                valueText = { stringResource(Res.string.settings_shortcuts_milliseconds, it) },
                valueRange = PlayerShortcutSteps.SubtitleDelayRange,
                step = 50,
                isTablet = isTablet,
                onValueChange = { PlayerShortcutSettings.setSteps(PlayerShortcutSettings.steps.value.copy(subtitleDelayMs = it)) },
            )
        }
    }
}
