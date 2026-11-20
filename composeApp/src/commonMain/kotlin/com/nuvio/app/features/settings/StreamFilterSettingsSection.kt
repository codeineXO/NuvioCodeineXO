package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.streams.StreamFilterSettingsRepository
import com.nuvio.app.features.streams.StreamKeywordFields
import com.nuvio.app.features.streams.StreamSizeFields
import com.nuvio.app.features.streams.StreamSortMode
import com.nuvio.app.features.streams.labelRes
import com.nuvio.app.features.streams.streamFilterSections
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Same filter and sort as the stream page's dropdowns, plus the autoplay switch and a reset. */
@Composable
internal fun StreamFilterSettingsSection(isTablet: Boolean) {
    val filterSettings by StreamFilterSettingsRepository.settings.collectAsStateWithLifecycle()
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(Res.string.stream_filter_settings_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.stream_filter_settings_sort),
                description = buildString {
                    append(stringResource(filterSettings.sort.labelRes()))
                    if (filterSettings.cachedFirst) {
                        append(" • ")
                        append(stringResource(Res.string.stream_sort_cached_first))
                    }
                },
                isTablet = isTablet,
                onClick = { showSortDialog = true },
            )
            SettingsGroupDivider(isTablet = isTablet)
            val savedFilterCount = filterSettings.copy(query = "").activeFilterCount
            SettingsNavigationRow(
                title = stringResource(Res.string.stream_filter_settings_filters),
                description = if (savedFilterCount > 0) {
                    stringResource(Res.string.stream_filter_settings_filters_active, savedFilterCount)
                } else {
                    stringResource(Res.string.stream_filter_settings_filters_none)
                },
                isTablet = isTablet,
                onClick = { showFilterDialog = true },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.stream_filter_autoplay_respects),
                description = stringResource(Res.string.stream_filter_autoplay_respects_description),
                checked = filterSettings.autoPlayRespectsFilters,
                isTablet = isTablet,
                onCheckedChange = { enabled ->
                    StreamFilterSettingsRepository.update { it.copy(autoPlayRespectsFilters = enabled) }
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.stream_filter_reset_all),
                description = null,
                enabled = filterSettings.changesResults,
                isTablet = isTablet,
                onClick = { showResetDialog = true },
            )
        }
    }

    if (showSortDialog) {
        StreamSortSettingsDialog(onDismiss = { showSortDialog = false })
    }
    if (showFilterDialog) {
        StreamFilterSettingsDialog(onDismiss = { showFilterDialog = false })
    }
    if (showResetDialog) {
        DialogSurface(
            onDismissRequest = { showResetDialog = false },
            title = stringResource(Res.string.stream_filter_reset_confirm_title),
            message = stringResource(Res.string.stream_filter_reset_confirm_message),
        ) {
            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.action_cancel),
                    onClick = { showResetDialog = false },
                )
                DialogButton(
                    text = stringResource(Res.string.stream_filter_reset),
                    style = DialogButtonStyle.Primary,
                    onClick = {
                        StreamFilterSettingsRepository.reset()
                        showResetDialog = false
                    },
                )
            }
        }
    }
}

@Composable
private fun StreamSortSettingsDialog(onDismiss: () -> Unit) {
    val filterSettings by StreamFilterSettingsRepository.settings.collectAsStateWithLifecycle()
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.stream_filter_settings_sort),
    ) {
        StreamSortMode.entries.forEach { mode ->
            DialogOption(
                text = stringResource(mode.labelRes()),
                selected = filterSettings.sort == mode,
                onClick = { StreamFilterSettingsRepository.update { it.copy(sort = mode) } },
            )
        }
        DialogOption(
            text = stringResource(Res.string.stream_sort_cached_first),
            selected = filterSettings.cachedFirst,
            role = Role.Checkbox,
            onClick = { StreamFilterSettingsRepository.update { it.copy(cachedFirst = !it.cachedFirst) } },
        )
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                style = DialogButtonStyle.Primary,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun StreamFilterSettingsDialog(onDismiss: () -> Unit) {
    val filterSettings by StreamFilterSettingsRepository.settings.collectAsStateWithLifecycle()
    val sections = streamFilterSections(filterSettings, options = null)
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.stream_filter_settings_filters),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "keywords") {
                StreamKeywordFields(settings = filterSettings, modifier = Modifier.fillMaxWidth())
                StreamSizeFields(settings = filterSettings, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            sections.forEach { section ->
                item(key = "title:${section.title}") {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                section.items.forEach { filterItem ->
                    item(key = filterItem.key) {
                        DialogOption(
                            text = filterItem.label,
                            selected = filterItem.selected,
                            role = Role.Checkbox,
                            onClick = { StreamFilterSettingsRepository.update(filterItem.toggle) },
                        )
                    }
                }
            }
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.stream_filter_reset),
                enabled = filterSettings.copy(query = "").hasActiveFilters,
                onClick = {
                    StreamFilterSettingsRepository.update {
                        it.reset().copy(sort = it.sort, cachedFirst = it.cachedFirst)
                    }
                },
            )
            DialogButton(
                text = stringResource(Res.string.action_done),
                style = DialogButtonStyle.Primary,
                onClick = onDismiss,
            )
        }
    }
}
