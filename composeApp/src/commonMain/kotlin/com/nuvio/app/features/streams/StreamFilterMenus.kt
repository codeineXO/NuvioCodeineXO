package com.nuvio.app.features.streams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.nuvioDesktopDragScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import com.nuvio.app.core.ui.navigationGlass
import com.nuvio.app.core.ui.navigationGlassFeedback
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
    val hdrLabels = StreamFilterHdr.entries.associateWith { hdrLabel(it) }
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
            valueGb = settings.minSizeGb,
            onValueChange = { gb -> StreamFilterSettingsRepository.update { it.copy(minSizeGb = gb) } },
            label = stringResource(Res.string.stream_filter_min_size_gb),
            placeholder = "0",
            modifier = Modifier.weight(1f),
        )
        StreamSizeField(
            valueGb = settings.maxSizeGb,
            onValueChange = { gb -> StreamFilterSettingsRepository.update { it.copy(maxSizeGb = gb) } },
            label = stringResource(Res.string.stream_filter_max_size_gb),
            placeholder = "∞",
            modifier = Modifier.weight(1f),
        )
    }
}

/** Keeps the raw text locally so partial decimals like "." or "0." survive while typing. */
@Composable
private fun StreamSizeField(
    valueGb: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(valueGb.toSizeText()) }
    if (text.toSizeGb() != valueGb) text = valueGb.toSizeText()
    StreamFilterTextField(
        value = text,
        onValueChange = { raw ->
            val cleaned = raw.sanitizeSizeText()
            text = cleaned
            onValueChange(cleaned.toSizeGb())
        },
        label = label,
        placeholder = placeholder,
        numeric = true,
        modifier = modifier,
    )
}

private fun String.sanitizeSizeText(): String {
    val normalized = replace(',', '.').filter { it.isDigit() || it == '.' }
    val dot = normalized.indexOf('.')
    if (dot < 0) return normalized.take(4)
    val whole = normalized.substring(0, dot).take(4)
    val fraction = normalized.substring(dot + 1).filter(Char::isDigit).take(2)
    return "$whole.$fraction"
}

private fun String.toSizeGb(): Double = toDoubleOrNull()?.takeIf { it > 0 } ?: 0.0

private fun Double.toSizeText(): String = when {
    this <= 0 -> ""
    this % 1.0 == 0.0 -> toLong().toString()
    else -> toString()
}

/** Saved include / exclude keyword fields, shared by the stream page menu and settings. */
@Composable
internal fun StreamKeywordFields(settings: StreamFilterSettings, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StreamFilterTextField(
            value = settings.includeKeywords,
            onValueChange = { text -> StreamFilterSettingsRepository.update { it.copy(includeKeywords = text) } },
            label = stringResource(Res.string.stream_filter_include_keywords),
            placeholder = stringResource(Res.string.stream_filter_keywords_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        StreamFilterTextField(
            value = settings.excludeKeywords,
            onValueChange = { text -> StreamFilterSettingsRepository.update { it.copy(excludeKeywords = text) } },
            label = stringResource(Res.string.stream_filter_exclude_keywords),
            placeholder = stringResource(Res.string.stream_filter_keywords_hint),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Compact inset field; follows the surrounding glass panel style and accepts typing on focus. */
@Composable
private fun StreamFilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    numeric: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    val shape = tokens.shapes.compactCard
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val textStyle = MaterialTheme.typography.bodyMedium
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle.copy(color = tokens.colors.textPrimary),
            cursorBrush = SolidColor(tokens.colors.accent),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .navigationGlass(shape = shape, backgroundOnly = true)
                .navigationGlassFeedback(interactionSource, shape, showFocus = false)
                .border(tokens.borders.thin, if (focused) tokens.colors.borderFocus else tokens.colors.borderDefault, shape)
                .remoteNavigationTarget(textField = true, shape = shape, typeToEdit = true),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = tokens.colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
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
private fun hdrLabel(value: StreamFilterHdr): String = when (value) {
    StreamFilterHdr.DOLBY_VISION -> stringResource(Res.string.stream_filter_hdr_dolby_vision)
    StreamFilterHdr.HDR10_PLUS -> stringResource(Res.string.stream_filter_hdr_hdr10_plus)
    StreamFilterHdr.HDR -> stringResource(Res.string.stream_filter_hdr_hdr)
    StreamFilterHdr.SDR -> stringResource(Res.string.stream_filter_hdr_sdr)
    StreamFilterHdr.UNKNOWN -> stringResource(Res.string.stream_filter_hdr_none)
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
        Menu(expanded = expanded, onDismissRequest = { expanded = false }, useGlass = true) {
            MenuInsetGroup {
                StreamSortMode.entries.forEach { mode ->
                    MenuItem(
                        text = stringResource(mode.labelRes()),
                        selected = settings.sort == mode,
                        useGlass = true,
                        onClick = {
                            StreamFilterSettingsRepository.update { it.copy(sort = mode) }
                            expanded = false
                        },
                    )
                }
            }
            MenuSectionTitle(stringResource(Res.string.stream_filter_section_general))
            MenuInsetGroup {
                MenuItem(
                    text = stringResource(Res.string.stream_sort_cached_first),
                    selected = settings.cachedFirst,
                    useGlass = true,
                    onClick = { StreamFilterSettingsRepository.update { it.copy(cachedFirst = !it.cachedFirst) } },
                )
            }
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
        Menu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(FilterMenuWidth),
            useGlass = true,
        ) {
            FilterMenuViewport {
                // Options are only worked out while the menu is open.
                val options = remember(streams) { StreamFilterEngine.options(streams) }
                // Always shown so the menu keeps its size; disabled while nothing is filtered.
                MenuAccentPill(
                    text = stringResource(Res.string.stream_filter_reset),
                    enabled = settings.hasActiveFilters,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
                    onClick = {
                        StreamFilterSettingsRepository.update {
                            it.reset().copy(sort = it.sort, cachedFirst = it.cachedFirst)
                        }
                    },
                )
                StreamFilterTextField(
                    value = settings.query,
                    onValueChange = { query -> StreamFilterSettingsRepository.update { it.copy(query = query) } },
                    placeholder = stringResource(Res.string.stream_filter_search_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    MenuInsetGroup {
                        section.items.forEach { item ->
                            MenuItem(
                                text = item.count?.let { "${item.label} ($it)" } ?: item.label,
                                selected = item.selected,
                                useGlass = true,
                                onClick = { StreamFilterSettingsRepository.update(item.toggle) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val FilterMenuWidth = 320.dp
private val FilterMenuMaxHeight = 560.dp

/** Scrolls the filter menu itself so a scrollbar can sit beside the options. */
@Composable
private fun FilterMenuViewport(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    // Leave room for the anchor chip and the popup margins.
    val maxHeight = (windowHeight - 160.dp).coerceIn(200.dp, FilterMenuMaxHeight)
    val scrollState = rememberScrollState()
    Box(Modifier.then(FilterMenuViewportLayout(FilterMenuWidth, maxHeight)).heightIn(max = maxHeight)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .nuvioDesktopDragScroll(scrollState, Orientation.Vertical)
                .padding(end = 6.dp),
        ) { content() }
        if (scrollState.maxValue > 0) {
            NuvioDesktopVerticalScrollbar(
                scrollState,
                Modifier.align(Alignment.CenterEnd).height(maxHeight).padding(vertical = 4.dp),
            )
        }
    }
}

/** DropdownMenu sizes by intrinsics; report the viewport, never the whole scrolling list. */
private class FilterMenuViewportLayout(private val width: Dp, private val height: Dp) : LayoutModifier {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = width.roundToPx()
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = width.roundToPx()
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = height.roundToPx()
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = height.roundToPx()
}

@Composable
private fun MenuSectionTitle(title: String) {
    val tokens = MaterialTheme.nuvio
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp)
            .height(tokens.borders.thin)
            .background(tokens.colors.borderDefault),
    )
    if (title.isEmpty()) return
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.nuvio.colors.textMuted,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
    )
}

/** Rows grouped on the same inset scrim as the stream result cards. */
@Composable
private fun MenuInsetGroup(content: @Composable () -> Unit) {
    val shape = MaterialTheme.nuvio.shapes.compactCard
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(shape)
            .streamResultsSurface()
            .streamResultsBorder(shape)
            .padding(vertical = 6.dp),
    ) { content() }
}

@Composable
private fun MenuAccentPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = modifier
            .then(if (enabled) Modifier.remoteNavigationTarget(shape = CircleShape) else Modifier)
            .clip(CircleShape)
            .background(if (enabled) tokens.colors.accent else tokens.colors.textMuted.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) tokens.colors.onAccent else tokens.colors.textMuted,
        )
    }
}
