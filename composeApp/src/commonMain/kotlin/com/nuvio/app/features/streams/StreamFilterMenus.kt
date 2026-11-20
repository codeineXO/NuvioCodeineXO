package com.nuvio.app.features.streams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.nuvio.app.core.ui.remoteNavigationTarget
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.Menu
import com.nuvio.app.core.ui.MenuItem
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.debrid.DebridStreamAudioChannel
import com.nuvio.app.features.debrid.DebridStreamAudioTag
import com.nuvio.app.features.debrid.DebridStreamEncode
import com.nuvio.app.features.debrid.DebridStreamLanguage
import com.nuvio.app.features.debrid.DebridStreamQuality
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal data class StreamFilterItem(
    val key: String,
    val label: String,
    val selected: Boolean,
    val count: Int?,
    val toggle: (StreamFilterSettings) -> StreamFilterSettings,
)

internal data class StreamFilterSection(
    val title: String,
    val items: List<StreamFilterItem>,
)

/**
 * Builds the filter sections. With [options] (the stream page), each section lists only values
 * present in the results, with counts; without (settings), it lists every value.
 */
@Composable
internal fun streamFilterSections(
    settings: StreamFilterSettings,
    options: StreamFilterOptions?,
): List<StreamFilterSection> {
    val unknown = stringResource(Res.string.stream_filter_unknown)

    fun <T> enumItems(
        prefix: String,
        values: List<T>,
        counts: Map<T, Int>?,
        get: (StreamFilterSettings) -> Set<T>,
        label: (T) -> String,
        update: (StreamFilterSettings, Set<T>) -> StreamFilterSettings,
    ): List<StreamFilterItem> = values
        .filter { value -> counts == null || (counts[value] ?: 0) > 0 || value in get(settings) }
        .map { value ->
            StreamFilterItem(
                key = "$prefix:$value",
                label = label(value),
                selected = value in get(settings),
                count = counts?.get(value) ?: if (counts != null) 0 else null,
                toggle = { current ->
                    val currentSet = get(current)
                    update(current, if (value in currentSet) currentSet - value else currentSet + value)
                },
            )
        }

    val general = buildList {
        if (options == null || options.cached > 0 || settings.cachedOnly) {
            add(
                StreamFilterItem(
                    key = "cached",
                    label = stringResource(Res.string.stream_filter_cached_only),
                    selected = settings.cachedOnly,
                    count = options?.cached,
                    toggle = { it.copy(cachedOnly = !it.cachedOnly) },
                ),
            )
        }
        if (options == null || options.unknownSize > 0 || settings.hideUnknownSize) {
            add(
                StreamFilterItem(
                    key = "unknownSize",
                    label = stringResource(Res.string.stream_filter_hide_unknown_size),
                    selected = settings.hideUnknownSize,
                    count = options?.unknownSize,
                    toggle = { it.copy(hideUnknownSize = !it.hideUnknownSize) },
                ),
            )
        }
        if (options == null || options.lowQuality > 0 || settings.hideLowQuality) {
            add(
                StreamFilterItem(
                    key = "lowQuality",
                    label = stringResource(Res.string.stream_filter_hide_low_quality),
                    selected = settings.hideLowQuality,
                    count = options?.lowQuality,
                    toggle = { it.copy(hideLowQuality = !it.hideLowQuality) },
                ),
            )
        }
    }

    val resolutionLabels = StreamFilterResolution.entries.associateWith { resolutionLabel(it, unknown) }
    val sourceLabels = StreamFilterSource.entries.associateWith { stringResource(it.labelRes()) }
    val hdrLabels = StreamFilterHdr.entries.associateWith { hdrLabel(it, unknown) }
    val formatLabels = StreamFilterFormat.entries.associateWith { formatLabel(it, unknown) }

    return listOf(
        StreamFilterSection(stringResource(Res.string.stream_filter_section_general), general),
        StreamFilterSection(
            stringResource(Res.string.stream_filter_section_resolution),
            enumItems("res", StreamFilterResolution.entries, options?.resolutions, { it.resolutions },
                { resolutionLabels.getValue(it) }) { s, v -> s.copy(resolutions = v) },
        ),
        StreamFilterSection(
            stringResource(Res.string.stream_filter_section_quality),
            enumItems("quality", DebridStreamQuality.defaultOrder, options?.qualities, { it.qualities },
                { if (it == DebridStreamQuality.UNKNOWN) unknown else it.label }) { s, v -> s.copy(qualities = v) },
        ),
        StreamFilterSection(
            stringResource(Res.string.stream_filter_section_source),
            enumItems("source", StreamFilterSource.entries, options?.sources, { it.sources },
                { sourceLabels.getValue(it) }) { s, v -> s.copy(sources = v) },
        ),
        StreamFilterSection(
            stringResource(Res.string.stream_filter_section_hdr),
            enumItems("hdr", StreamFilterHdr.entries, options?.hdr, { it.hdr },
                { hdrLabels.getValue(it) }) { s, v -> s.copy(hdr = v) },
        ),
        StreamFilterSection(
            stringResource(Res.string.stream_filter_section_codec),
            enumItems("codec", DebridStreamEncode.defaultOrder, options?.encodes, { it.encodes },
                { if (it == DebridStreamEncode.UNKNOWN) unknown else it.label }) { s, v -> s.copy(encodes = v) },
        ),
        StreamFilterSection(
            stringResource(Res.string.stream_filter_section_audio),
            enumItems("audio", DebridStreamAudioTag.defaultOrder, options?.audio, { it.audio },
                { if (it == DebridStreamAudioTag.UNKNOWN) unknown else it.label }) { s, v -> s.copy(audio = v) },
        ),
        StreamFilterSection(
            stringResource(Res.string.stream_filter_section_channels),
            enumItems("channels", DebridStreamAudioChannel.defaultOrder, options?.channels, { it.channels },
                { if (it == DebridStreamAudioChannel.UNKNOWN) unknown else it.label }) { s, v -> s.copy(channels = v) },
        ),
        StreamFilterSection(
            stringResource(Res.string.stream_filter_section_language),
            enumItems("lang", DebridStreamLanguage.entries.toList(), options?.languages, { it.languages },
                { if (it == DebridStreamLanguage.UNKNOWN) unknown else it.label }) { s, v -> s.copy(languages = v) },
        ),
        StreamFilterSection(
            stringResource(Res.string.stream_filter_section_format),
            enumItems("format", StreamFilterFormat.entries, options?.formats, { it.formats },
                { formatLabels.getValue(it) }) { s, v -> s.copy(formats = v) },
        ),
        StreamFilterSection(
            stringResource(Res.string.stream_filter_section_provider),
            enumItems("provider", namedValues(options?.providers, settings.providers), options?.providers, { it.providers },
                { it }) { s, v -> s.copy(providers = v) },
        ),
        StreamFilterSection(
            stringResource(Res.string.stream_filter_section_release_group),
            enumItems("group", namedValues(options?.releaseGroups, settings.releaseGroups), options?.releaseGroups, { it.releaseGroups },
                { it.ifEmpty { unknown } }) { s, v -> s.copy(releaseGroups = v) },
        ),
    ).filter { it.items.isNotEmpty() }
}

/** Named values (providers, release groups): most common first, then any selected but absent. */
private fun namedValues(counts: Map<String, Int>?, selected: Set<String>): List<String> =
    (counts.orEmpty().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
        .map { it.key } + selected.sorted()).distinct()

@Composable
private fun formatLabel(value: StreamFilterFormat, unknown: String): String = when (value) {
    StreamFilterFormat.HLS -> stringResource(Res.string.stream_filter_format_hls)
    StreamFilterFormat.DASH -> stringResource(Res.string.stream_filter_format_dash)
    StreamFilterFormat.MKV -> stringResource(Res.string.stream_filter_format_mkv)
    StreamFilterFormat.MP4 -> stringResource(Res.string.stream_filter_format_mp4)
    StreamFilterFormat.OTHER -> stringResource(Res.string.stream_filter_format_other)
    StreamFilterFormat.YOUTUBE -> stringResource(Res.string.stream_filter_format_youtube)
    StreamFilterFormat.UNKNOWN -> unknown
}

/** Min / max size in GB; an empty field means no limit. */
@Composable
internal fun StreamSizeFields(settings: StreamFilterSettings, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StreamSizeField(
            label = stringResource(Res.string.stream_filter_min_size_gb),
            value = settings.minSizeGb,
            onValueChange = { gb -> StreamFilterSettingsRepository.update { it.copy(minSizeGb = gb) } },
            modifier = Modifier.weight(1f),
        )
        StreamSizeField(
            label = stringResource(Res.string.stream_filter_max_size_gb),
            value = settings.maxSizeGb,
            onValueChange = { gb -> StreamFilterSettingsRepository.update { it.copy(maxSizeGb = gb) } },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StreamSizeField(label: String, value: Int, onValueChange: (Int) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = if (value > 0) value.toString() else "",
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(4).toIntOrNull() ?: 0) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.remoteNavigationTarget(textField = true),
    )
}

/** Saved include / exclude keyword fields, shared by the stream page menu and settings. */
@Composable
internal fun StreamKeywordFields(settings: StreamFilterSettings, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        OutlinedTextField(
            value = settings.includeKeywords,
            onValueChange = { text -> StreamFilterSettingsRepository.update { it.copy(includeKeywords = text) } },
            label = { Text(stringResource(Res.string.stream_filter_include_keywords)) },
            placeholder = { Text(stringResource(Res.string.stream_filter_keywords_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().remoteNavigationTarget(textField = true),
        )
        OutlinedTextField(
            value = settings.excludeKeywords,
            onValueChange = { text -> StreamFilterSettingsRepository.update { it.copy(excludeKeywords = text) } },
            label = { Text(stringResource(Res.string.stream_filter_exclude_keywords)) },
            placeholder = { Text(stringResource(Res.string.stream_filter_keywords_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).remoteNavigationTarget(textField = true),
        )
    }
}

internal fun StreamSortMode.labelRes(): StringResource = when (this) {
    StreamSortMode.DEFAULT -> Res.string.stream_sort_default
    StreamSortMode.BEST_QUALITY -> Res.string.stream_sort_best_quality
    StreamSortMode.SIZE_DESC -> Res.string.stream_sort_size_desc
    StreamSortMode.SIZE_ASC -> Res.string.stream_sort_size_asc
    StreamSortMode.BITRATE_DESC -> Res.string.stream_sort_bitrate_desc
    StreamSortMode.SEEDERS_DESC -> Res.string.stream_sort_seeders_desc
}

private fun StreamFilterSource.labelRes(): StringResource = when (this) {
    StreamFilterSource.DIRECT -> Res.string.stream_filter_source_direct
    StreamFilterSource.DEBRID -> Res.string.stream_filter_source_debrid
    StreamFilterSource.TORRENT -> Res.string.stream_filter_source_torrent
    StreamFilterSource.EXTERNAL -> Res.string.stream_filter_source_external
}

@Composable
private fun resolutionLabel(value: StreamFilterResolution, unknown: String): String = when (value) {
    StreamFilterResolution.UHD_4K -> stringResource(Res.string.stream_filter_resolution_4k)
    StreamFilterResolution.QHD_1440 -> stringResource(Res.string.stream_filter_resolution_1440)
    StreamFilterResolution.FHD_1080 -> stringResource(Res.string.stream_filter_resolution_1080)
    StreamFilterResolution.HD_720 -> stringResource(Res.string.stream_filter_resolution_720)
    StreamFilterResolution.SD -> stringResource(Res.string.stream_filter_resolution_sd)
    StreamFilterResolution.UNKNOWN -> unknown
}

@Composable
private fun hdrLabel(value: StreamFilterHdr, unknown: String): String = when (value) {
    StreamFilterHdr.DOLBY_VISION -> stringResource(Res.string.stream_filter_hdr_dolby_vision)
    StreamFilterHdr.HDR10_PLUS -> stringResource(Res.string.stream_filter_hdr_hdr10_plus)
    StreamFilterHdr.HDR -> stringResource(Res.string.stream_filter_hdr_hdr)
    StreamFilterHdr.SDR -> stringResource(Res.string.stream_filter_hdr_sdr)
    StreamFilterHdr.UNKNOWN -> unknown
}

/** Sort and Filter dropdowns for the stream page; they edit [StreamFilterSettingsRepository]. */
@Composable
internal fun StreamSortFilterMenus(
    settings: StreamFilterSettings,
    streams: List<StreamItem>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreamSortMenu(settings)
        StreamFilterMenu(settings, streams)
    }
}

@Composable
private fun StreamSortMenu(settings: StreamFilterSettings) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        StreamFilterChip(
            label = if (settings.sort == StreamSortMode.DEFAULT) {
                stringResource(Res.string.stream_sort_button)
            } else {
                stringResource(settings.sort.labelRes())
            },
            isSelected = settings.hasActiveSort,
            onClick = { expanded = true },
        )
        Menu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StreamSortMode.entries.forEach { mode ->
                MenuItem(
                    text = stringResource(mode.labelRes()),
                    selected = settings.sort == mode,
                    onClick = {
                        StreamFilterSettingsRepository.update { it.copy(sort = mode) }
                        expanded = false
                    },
                )
            }
            MenuSectionTitle(stringResource(Res.string.stream_filter_section_general))
            MenuItem(
                text = stringResource(Res.string.stream_sort_cached_first),
                selected = settings.cachedFirst,
                onClick = { StreamFilterSettingsRepository.update { it.copy(cachedFirst = !it.cachedFirst) } },
            )
        }
    }
}

@Composable
private fun StreamFilterMenu(settings: StreamFilterSettings, streams: List<StreamItem>) {
    var expanded by remember { mutableStateOf(false) }
    val activeCount = settings.activeFilterCount
    Box {
        StreamFilterChip(
            label = if (activeCount > 0) {
                stringResource(Res.string.stream_filter_button_count, activeCount)
            } else {
                stringResource(Res.string.stream_filter_button)
            },
            isSelected = activeCount > 0,
            onClick = { expanded = true },
        )
        Menu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Options are only worked out while the menu is open.
            val options = remember(streams) { StreamFilterEngine.options(streams) }
            if (settings.hasActiveFilters) {
                MenuItem(
                    text = stringResource(Res.string.stream_filter_reset),
                    onClick = {
                        StreamFilterSettingsRepository.update {
                            it.reset().copy(sort = it.sort, cachedFirst = it.cachedFirst)
                        }
                        expanded = false
                    },
                )
            }
            OutlinedTextField(
                value = settings.query,
                onValueChange = { query -> StreamFilterSettingsRepository.update { it.copy(query = query) } },
                placeholder = { Text(stringResource(Res.string.stream_filter_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .remoteNavigationTarget(textField = true),
            )
            StreamKeywordFields(
                settings = settings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            MenuSectionTitle(stringResource(Res.string.stream_filter_section_size))
            StreamSizeFields(
                settings = settings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            streamFilterSections(settings, options).forEach { section ->
                MenuSectionTitle(section.title)
                section.items.forEach { item ->
                    MenuItem(
                        text = item.count?.let { "${item.label} ($it)" } ?: item.label,
                        selected = item.selected,
                        onClick = { StreamFilterSettingsRepository.update(item.toggle) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuSectionTitle(title: String) {
    if (title.isEmpty()) {
        Box(Modifier.padding(vertical = 4.dp))
        return
    }
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.nuvio.colors.textSecondary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}
