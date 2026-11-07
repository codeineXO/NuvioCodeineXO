package com.nuvio.app.features.randompicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.SingletonImageLoader
import coil3.Extras
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import com.nuvio.app.core.ui.*
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.player.MediaClassifier
import com.nuvio.app.features.settings.SurfaceStyle
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
internal fun RandomPickerScreen(active: Boolean, onDetails: (MetaPreview) -> Unit, onBack: () -> Unit, model: RandomPickerViewModel) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val context = LocalPlatformContext.current
    val prepare: suspend (PickerResult, PickerRequests) -> PickerResult = remember(context) {
        { result, requests ->
            preparePickerArtwork(result, requests, context, SingletonImageLoader.get(context))
        }
    }
    DisposableEffect(model, active, prepare) {
        model.setActive(active, prepare)
        onDispose { model.setActive(false, prepare) }
    }
    RandomPickerContent(state, model::update, model::pick, model::reset, onDetails, onBack, model::dismissResult, model::stopSearch, active)
}

private val LocalPickerLabels = staticCompositionLocalOf<Map<StringResource, String>> { emptyMap() }
private val pickerStringResources = listOf(
    Res.string.picker_title,
    Res.string.picker_subtitle,
    Res.string.picker_types,
    Res.string.picker_style,
    Res.string.picker_format,
    Res.string.picker_live_action,
    Res.string.picker_exclude_watched,
    Res.string.picker_all,
    Res.string.picker_movies,
    Res.string.picker_series,
    Res.string.picker_anime,
    Res.string.picker_addon,
    Res.string.picker_catalog,
    Res.string.picker_all_sources,
    Res.string.picker_all_catalogs,
    Res.string.picker_no_sources,
    Res.string.picker_genres,
    Res.string.picker_any_genre,
    Res.string.picker_rating,
    Res.string.picker_any_rating,
    Res.string.picker_year,
    Res.string.picker_any_year,
    Res.string.picker_before_1900,
    Res.string.picker_status,
    Res.string.picker_ongoing,
    Res.string.picker_completed,
    Res.string.picker_exclude,
    Res.string.picker_exclude_hint,
    Res.string.picker_pick,
    Res.string.picker_again,
    Res.string.picker_reset,
    Res.string.picker_details,
    Res.string.picker_back,
    Res.string.picker_close,
    Res.string.picker_loading,
    Res.string.picker_sampling,
    Res.string.picker_enriching,
    Res.string.picker_preparing,
    Res.string.picker_timeout,
    Res.string.picker_empty,
    Res.string.picker_failed,
    Res.string.picker_partial,
    Res.string.picker_metadata_missing,
    Res.string.picker_persistence_failed,
    Res.string.picker_sampling_hint,
    Res.string.picker_no_description,
    Res.string.picker_source,
    Res.string.picker_retrying,
    Res.string.picker_cancel,
)

@Composable
private fun pickerText(resource: StringResource): String = LocalPickerLabels.current.getValue(resource)

@Composable
internal fun RandomPickerContent(
    state: PickerState,
    update: (PickerFilters) -> Unit,
    pick: () -> Unit,
    reset: () -> Unit,
    onDetails: (MetaPreview) -> Unit,
    onBack: () -> Unit,
    onDismissResult: () -> Unit,
    onCancelSearch: () -> Unit = {},
    active: Boolean = true,
) {
    // Resolve every label before lazy layout and popup subcomposition to avoid blocking resource loads during measurement.
    val labels = pickerStringResources.associateWith { stringResource(it) }
    CompositionLocalProvider(LocalPickerLabels provides labels) {
        SettingsSurfaceStyle {
            RandomPickerBody(state, update, pick, reset, onDetails, onBack, onDismissResult, onCancelSearch, active)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RandomPickerBody(
    state: PickerState, update: (PickerFilters) -> Unit, pick: () -> Unit, reset: () -> Unit,
    onDetails: (MetaPreview) -> Unit, onBack: () -> Unit, onDismissResult: () -> Unit,
    onCancelSearch: () -> Unit,
    active: Boolean,
) {
    val filters = state.filters
    val tokens = MaterialTheme.nuvio
    val compatible = state.sources.filter { it.compatible(filters) && it.unsupportedRequired.isEmpty() }
    val scoped = compatible.filter { filters.addonUrl == null || it.manifestUrl == filters.addonUrl }
    val currentYear = remember { CurrentDateProvider.todayIsoDate().take(4).toInt() }
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    LaunchedEffect(active, state.resultPopupOpen) {
        if (active && state.resultPopupOpen) listState.scrollToItem(0)
    }
    var headerHeight by rememberSaveable { mutableIntStateOf(0) }
    var panelHeight by rememberSaveable { mutableIntStateOf(0) }
    var retainedPanelHeight by rememberSaveable { mutableIntStateOf(0) }
    var pageOffset by remember { mutableStateOf(IntOffset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned {
        val position = it.positionInWindow()
        pageOffset = IntOffset(position.x.roundToInt(), position.y.roundToInt())
    }) {
        val popupWidth = (maxWidth - 48.dp).coerceIn(1.dp, 1060.dp)
        val popupTop = 90.dp + with(density) { headerHeight.toDp() } + 12.dp
        val availableHeight = (maxHeight - popupTop - 24.dp).coerceAtLeast(1.dp)
        val popupHeight = with(density) { panelHeight.toDp() }.coerceIn(1.dp, availableHeight)
        val popupOffset = pageOffset + with(density) { IntOffset(((maxWidth - popupWidth) / 2).roundToPx(), popupTop.roundToPx()) }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("random_picker_list"),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 90.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 1060.dp).fillMaxWidth().testTag("shuffle_header").onSizeChanged { headerHeight = it.height }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Casino, null, Modifier.size(32.dp), tint = tokens.colors.textPrimary)
                        Text(pickerText(Res.string.picker_title), style = MaterialTheme.typography.headlineLarge, color = tokens.colors.textPrimary)
                    }
                    Text(pickerText(Res.string.picker_subtitle), color = tokens.colors.textSecondary)
                }
            }
            item {
                SettingsAwareSurface(Modifier.widthIn(max = 1060.dp).fillMaxWidth().testTag("shuffle_filter_panel")
                    .heightIn(min = if (state.resultPopupOpen) with(density) { retainedPanelHeight.toDp() } else 0.dp)
                    .onSizeChanged {
                        panelHeight = it.height
                        if (!state.resultPopupOpen || retainedPanelHeight == 0) retainedPanelHeight = it.height
                    }, shape = tokens.shapes.card,
                    color = tokens.colors.surface, contentColor = tokens.colors.textPrimary, panel = true) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(pickerText(Res.string.picker_types), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PickerDropdown(pickerText(Res.string.picker_style), filters.contentStyle,
                                listOf("" to Res.string.picker_all, "live_action" to Res.string.picker_live_action, "anime" to Res.string.picker_anime)
                                    .map { NuvioDropdownOption(it.first, pickerText(it.second)) }, "shuffle_style") {
                                update(filters.copy(contentStyle = it.orEmpty()))
                            }
                            PickerDropdown(pickerText(Res.string.picker_format), filters.format,
                                listOf("" to Res.string.picker_all, "movie" to Res.string.picker_movies, "series" to Res.string.picker_series)
                                    .map { NuvioDropdownOption(it.first, pickerText(it.second)) }, "shuffle_format") {
                                update(filters.copy(format = it.orEmpty(), status = if (it == "movie") "" else filters.status))
                            }
                        }
                        Text(pickerText(Res.string.picker_status), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("" to Res.string.picker_all, "ongoing" to Res.string.picker_ongoing, "completed" to Res.string.picker_completed).forEach { (value, label) ->
                                val enabled = filters.format != "movie"
                                val selected = filters.status == value
                                val shape = tokens.shapes.compactCard
                                Row(Modifier.heightIn(min = 36.dp)
                                    .navigationGlass(shape = shape, backgroundOnly = true)
                                    .background(when {
                                        selected && enabled -> tokens.colors.accent.copy(alpha = tokens.opacity.selected)
                                        LocalSurfaceStyle.current == SurfaceStyle.Flat -> tokens.colors.surface
                                        else -> Color.Transparent
                                    })
                                    .border(tokens.borders.thin, if (selected && enabled) tokens.colors.accent else tokens.colors.borderDefault, shape)
                                    .remoteNavigationTarget(shape = shape).testTag("shuffle_status_$value")
                                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton,
                                        onClick = { update(filters.copy(status = value)) })
                                    .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(pickerText(label), style = MaterialTheme.typography.labelLarge,
                                        color = when {
                                            !enabled -> tokens.colors.textDisabled
                                            selected -> tokens.colors.accent
                                            else -> tokens.colors.textPrimary
                                        })
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PickerDropdown(pickerText(Res.string.picker_addon), filters.addonUrl,
                                listOf(NuvioDropdownOption("", pickerText(Res.string.picker_all_sources))) +
                                    compatible.distinctBy { it.manifestUrl }.map { NuvioDropdownOption(it.manifestUrl, it.addonName) }, "shuffle_addon") {
                                update(filters.copy(addonUrl = it))
                            }
                            PickerDropdown(pickerText(Res.string.picker_catalog), filters.catalogKey,
                                listOf(NuvioDropdownOption("", pickerText(Res.string.picker_all_catalogs))) + scoped.map { source ->
                                    val duplicate = scoped.count { it.catalog.name.equals(source.catalog.name, true) } > 1
                                    NuvioDropdownOption(source.key, if (duplicate) "${source.catalog.name} · ${source.addonName} · ${source.catalog.type}" else source.catalog.name)
                                }, "shuffle_catalog") { update(filters.copy(catalogKey = it)) }
                            PickerDropdown(pickerText(Res.string.picker_genres), filters.genre,
                                listOf(NuvioDropdownOption("", pickerText(Res.string.picker_any_genre))) + state.genres.map { NuvioDropdownOption(it, it) },
                                "shuffle_genre") { update(filters.copy(genre = it)) }
                            PickerDropdown(pickerText(Res.string.picker_rating), filters.minimumRating?.toString(),
                                listOf(NuvioDropdownOption("", pickerText(Res.string.picker_any_rating))) + (1..10).map { NuvioDropdownOption("$it", "$it+") },
                                "shuffle_rating") { update(filters.copy(minimumRating = it?.toInt())) }
                            PickerDropdown(pickerText(Res.string.picker_year), filters.decade?.toString(),
                                listOf(NuvioDropdownOption("", pickerText(Res.string.picker_any_year))) + pickerDecades(currentYear).map {
                                    NuvioDropdownOption("$it", if (it == -1) pickerText(Res.string.picker_before_1900) else "${it}s")
                                }, "shuffle_year") { update(filters.copy(decade = it?.toInt())) }
                        }
                        if (compatible.isEmpty()) Text(pickerText(Res.string.picker_no_sources), color = tokens.colors.textSecondary)
                        Row(Modifier.fillMaxWidth().remoteNavigationTarget().testTag("shuffle_exclude_watched")
                            .toggleable(filters.excludeWatched, role = Role.Checkbox, onValueChange = { update(filters.copy(excludeWatched = it)) }),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(filters.excludeWatched, null)
                            Text(pickerText(Res.string.picker_exclude_watched), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(Modifier.fillMaxWidth().remoteNavigationTarget().toggleable(filters.excludePrevious, role = Role.Checkbox,
                            onValueChange = { update(filters.copy(excludePrevious = it)) }), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(filters.excludePrevious, null)
                            Text(pickerText(Res.string.picker_exclude), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(pickerText(Res.string.picker_exclude_hint), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(pick, modifier = Modifier.remoteNavigationTarget().testTag("shuffle_pick"),
                                enabled = !state.picking && state.phase != PickerPhase.LoadingSources && state.sources.any { it.selectedBy(filters) } && filters.isValid()) {
                                Icon(Icons.Default.Casino, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                                Text(pickerText(if (state.result == null) Res.string.picker_pick else Res.string.picker_again))
                            }
                            OutlinedButton(reset, modifier = Modifier.remoteNavigationTarget()) { Text(pickerText(Res.string.picker_reset)) }
                            if (state.picking) OutlinedButton(onCancelSearch, modifier = Modifier.remoteNavigationTarget().testTag("shuffle_cancel")) {
                                Text(pickerText(Res.string.picker_cancel))
                            }
                        }
                        Text(pickerText(Res.string.picker_sampling_hint), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary)
                        if (!state.resultPopupOpen) PickerFeedback(state)
                    }
                }
            }
        }
        if (active && state.resultPopupOpen && state.result != null && headerHeight > 0 && panelHeight > 0 &&
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            PickerResultModal(state, popupWidth, popupHeight, popupOffset, pick, {
                onDetails(state.result.preview)
            }, onDismissResult)
        }
    }
}

@Composable
private fun PickerDropdown(title: String, selected: String?, options: List<NuvioDropdownOption>, tag: String, onSelected: (String?) -> Unit) {
    Column(Modifier.width(240.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        NuvioDropdownChip(title, options.firstOrNull { it.key == selected.orEmpty() }?.label ?: options.first().label,
            selected.orEmpty(), options, onSelected = { onSelected(it.key.takeIf(String::isNotEmpty)) },
            modifier = Modifier.fillMaxWidth(), wrapFilterLabels = true,
            useGlass = LocalSurfaceStyle.current != SurfaceStyle.Flat)
    }
}

@Composable
private fun PickerFeedback(state: PickerState) {
    val message = when (state.phase) {
        PickerPhase.LoadingSources -> Res.string.picker_loading
        PickerPhase.Sampling -> Res.string.picker_sampling
        PickerPhase.Enriching -> Res.string.picker_enriching
        PickerPhase.Empty -> Res.string.picker_empty
        PickerPhase.Preparing -> Res.string.picker_preparing
        PickerPhase.Retrying -> Res.string.picker_retrying
        PickerPhase.Failed -> if (state.timedOut) Res.string.picker_timeout else Res.string.picker_failed
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.picking || state.phase == PickerPhase.LoadingSources) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message != null) Text(pickerText(message), color = MaterialTheme.nuvio.colors.textPrimary)
        if (state.failedSources > 0) Text(pickerText(Res.string.picker_partial))
        if (state.metadataFailures > 0) Text(pickerText(Res.string.picker_metadata_missing))
        if (state.persistenceFailed) Text(pickerText(Res.string.picker_persistence_failed), color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PickerResultModal(state: PickerState, width: Dp, maxHeight: Dp, offset: IntOffset, pick: () -> Unit, details: () -> Unit, dismiss: () -> Unit) {
    val result = state.result ?: return
    val posterStyle = rememberPosterCardStyleUiState()
    val shape = shuffleResultShape(posterStyle.cornerRadiusDp)
    val modalFocus = remember { FocusRequester() }
    PlatformBackHandler(enabled = true, onBack = dismiss)
    val positionProvider = remember(offset) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
                IntOffset(offset.x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
                    offset.y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
        }
    }
    AppPopup(popupPositionProvider = positionProvider, onDismissRequest = dismiss,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = false)) {
        SettingsOverlaySurfaceLayer {
        LaunchedEffect(Unit) { modalFocus.requestFocus() }
        SettingsAwareSurface(
            modifier = Modifier.width(width).height(maxHeight).remoteNavigationModal()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Back)) {
                        dismiss(); true
                    } else false
                }.focusRequester(modalFocus).focusable().testTag("shuffle_result"),
            shape = shape, color = MaterialTheme.nuvio.colors.surfaceDialog, panel = true,
            contentColor = MaterialTheme.nuvio.colors.textPrimary,
        ) {
            Box(Modifier.fillMaxSize().clip(shape)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                key(result.presentationId, result.preview.type, result.preview.id) {
                BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = (maxHeight - 120.dp).coerceAtLeast(360.dp))
                    .clickable(enabled = !state.picking, onClickLabel = pickerText(Res.string.picker_details), onClick = details)
                    .testTag("shuffle_artwork")) {
                    val wide = maxWidth >= 680.dp
                    ShuffleArtworkImage(result.artwork?.poster ?: result.meta.poster, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    if (result.meta.background != null) ShuffleArtworkImage(result.artwork?.background ?: result.meta.background, null,
                        Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .90f), Color.Black.copy(alpha = .35f)))))
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f)))))
                    val poster: @Composable () -> Unit = {
                        ShuffleArtworkImage(result.artwork?.poster ?: result.meta.poster, result.meta.name, Modifier.width(if (wide) 156.dp else 84.dp).aspectRatio(2f / 3f).clip(shape), contentScale = ContentScale.Crop)
                    }
                    if (wide) Row(Modifier.padding(start = 32.dp, end = 32.dp, top = 60.dp, bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        poster(); Box(Modifier.weight(1f)) { PickerHeroInfo(result) }
                    } else Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 60.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        poster(); PickerHeroInfo(result)
                    }
                }
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickerFeedback(state)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(details, enabled = !state.picking, modifier = Modifier.remoteNavigationTarget(initialFocus = true).testTag("shuffle_details")) { Text(pickerText(Res.string.picker_details)) }
                        OutlinedButton(pick, enabled = !state.picking, modifier = Modifier.remoteNavigationTarget().testTag("shuffle_again")) { Text(pickerText(Res.string.picker_again)) }
                    }
                }
            }
            FilledIconButton(dismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                .remoteNavigationTarget().testTag("shuffle_close_icon"),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .65f), contentColor = Color.White)) {
                Icon(Icons.Default.Close, pickerText(Res.string.picker_close))
            }
            }
        }
    }
    }
}

internal fun shuffleResultShape(radiusDp: Int) = RoundedCornerShape(radiusDp.dp)

@Composable
private fun PickerHeroInfo(result: PickerResult) {
    val meta = result.meta
    var logoFailed by remember(meta.logo) { mutableStateOf(false) }
    var logoLoaded by remember(meta.logo) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (meta.logo != null && !logoFailed) Box(Modifier.widthIn(max = 300.dp).heightIn(min = 72.dp), contentAlignment = Alignment.CenterStart) {
            if (!logoLoaded) Text(meta.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
            ShuffleArtworkImage(result.artwork?.logo ?: meta.logo, meta.name, Modifier.fillMaxWidth().height(72.dp),
                contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                onLoaded = { logoLoaded = true }, onFailed = { logoFailed = true })
        } else Text(meta.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
        val kind = when {
            MediaClassifier.classify(meta).isAnime -> Res.string.picker_anime
            meta.type.equals("movie", true) -> Res.string.picker_movies
            else -> Res.string.picker_series
        }
        Text(listOfNotNull(meta.releaseInfo, pickerText(kind)).joinToString(" · "), color = Color.White.copy(alpha = .85f))
        meta.pickerRating()?.let { Text("${pickerText(Res.string.picker_rating)} ${(it * 10).roundToInt() / 10.0} / 10", fontWeight = FontWeight.Bold, color = Color.White) }
        if (meta.genres.isNotEmpty()) Text(meta.genres.joinToString(" · "), color = Color.White.copy(alpha = .80f))
        Text(meta.description ?: pickerText(Res.string.picker_no_description), color = Color.White.copy(alpha = .90f), maxLines = 6, overflow = TextOverflow.Ellipsis)
        Text("${pickerText(Res.string.picker_source)}: ${result.source.addonName} · ${result.source.catalog.name}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .70f))
    }
}

private val ShuffleArtworkAttempt = Extras.Key<Int>(0)

/** Keep recoverable URLs and restart failed visible loads without bypassing the normal image cache. */
@Composable
private fun ShuffleArtworkImage(
    model: Any?, description: String?, modifier: Modifier, contentScale: ContentScale,
    alignment: Alignment = Alignment.Center, onLoaded: () -> Unit = {}, onFailed: () -> Unit = {},
) {
    if (model == null) return
    val context = LocalPlatformContext.current
    var attempt by remember(model) { mutableIntStateOf(0) }
    var failure by remember(model) { mutableIntStateOf(0) }
    LaunchedEffect(failure) {
        if (failure > attempt && attempt < 3) {
            delay(750L * failure)
            attempt = failure
        }
    }
    val request = remember(context, model, attempt) {
        (if (model is ImageRequest) model.newBuilder() else ImageRequest.Builder(context).data(model))
            .apply { extras[ShuffleArtworkAttempt] = attempt }.build()
    }
    AsyncImage(request, description, modifier, contentScale = contentScale, alignment = alignment,
        onSuccess = { onLoaded() }, onError = { if (attempt < 3) failure = attempt + 1 else onFailed() })
}
