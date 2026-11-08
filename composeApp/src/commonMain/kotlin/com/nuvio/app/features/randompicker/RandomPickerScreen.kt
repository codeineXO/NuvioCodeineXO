package com.nuvio.app.features.randompicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.SingletonImageLoader
import coil3.Extras
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import com.nuvio.app.core.ui.*
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.details.components.DetailHeroMetaBadge
import com.nuvio.app.features.details.components.ImdbRatingSourceLabel
import com.nuvio.app.features.details.components.ImdbYellow
import com.nuvio.app.features.details.components.RatingsRow
import com.nuvio.app.features.details.components.desktopYearLabel
import com.nuvio.app.features.details.components.desktopSeasonCountLabel
import com.nuvio.app.features.details.components.formatOneDecimal
import com.nuvio.app.features.details.formatRuntimeForDisplay
import com.nuvio.app.features.mdblist.MdbListSettingsRepository
import com.nuvio.app.core.i18n.localizedMediaStatusLabel
import com.nuvio.app.core.poster.CustomPosterUrlRepository
import com.nuvio.app.core.poster.CustomPosterUrlResolver
import com.nuvio.app.core.poster.CustomPosterScreen
import com.nuvio.app.core.poster.CustomPosterFallbackInterceptor
import com.nuvio.app.features.player.MediaClassifier
import com.nuvio.app.features.settings.SurfaceStyle
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RandomPickerScreen(active: Boolean, onDetails: (MetaPreview) -> Unit, onBack: () -> Unit, model: RandomPickerViewModel) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val ratingSettings by remember {
        MdbListSettingsRepository.ensureLoaded()
        MdbListSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    LaunchedEffect(model, active, state.resultPopupOpen, state.result?.presentationId, ratingSettings) {
        if (active) model.loadResultRatings(ratingSettings)
    }
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
    // Shuffle is a root destination. Child overlays register later and can still
    // consume Back/Escape to dismiss themselves (desktop Backspace maps to Escape).
    PlatformBackHandler(enabled = active, onBack = {})
    val filters = state.filters
    val canPick = !state.picking && state.phase != PickerPhase.LoadingSources &&
        state.sources.any { it.selectedBy(filters) } && filters.isValid()
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val popupWidth = (maxWidth - 48.dp).coerceIn(1.dp, 1060.dp)
        val popupTop = 90.dp + with(density) { headerHeight.toDp() } + 12.dp
        val availableHeight = (maxHeight - popupTop - 24.dp).coerceAtLeast(1.dp)
        val popupHeight = with(density) { panelHeight.toDp() }.coerceIn(1.dp, availableHeight)
        val popupOffset = with(density) { IntOffset(((maxWidth - popupWidth) / 2).roundToPx(), popupTop.roundToPx()) }
        CompositionLocalProvider(LocalScreenActive provides (active && !state.resultPopupOpen)) {
            NuvioLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
                    .focusProperties { canFocus = !state.resultPopupOpen }
                    .testTag("random_picker_list"),
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
                    RemoteNavigationControls(page = "shuffle_filters", row = 0, rememberPosition = true, allowUpToNavigation = true) {
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
                                    .then(if (enabled) Modifier.remoteNavigationTarget(shape = shape) else Modifier)
                                    .testTag("shuffle_status_$value")
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
                                }, "shuffle_catalog", expandLabel = true) { update(filters.copy(catalogKey = it)) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PickerDropdown(pickerText(Res.string.picker_genres), filters.genre,
                                listOf(NuvioDropdownOption("", pickerText(Res.string.picker_any_genre))) + state.genres.map { NuvioDropdownOption(it, it) },
                                "shuffle_genre") { update(filters.copy(genre = it)) }
                            PickerDropdown(pickerText(Res.string.picker_rating), filters.minimumRating?.toString(),
                                listOf(NuvioDropdownOption("", pickerText(Res.string.picker_any_rating))) + (1..9).map { NuvioDropdownOption("$it", "$it+") },
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
                            Box(Modifier.remoteNavigationTarget(onSelect = { if (canPick) pick() })
                                .then(if (LocalRemoteNavigation.current != null) Modifier.focusable() else Modifier)
                                .semantics { if (!canPick) disabled() }
                                .testTag("shuffle_pick")) {
                                Button(onClick = { if (canPick) pick() }, modifier = Modifier.excludeFromRemoteNavigation(),
                                    enabled = canPick) {
                                    Icon(Icons.Default.Casino, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                                    Text(pickerText(if (state.result == null) Res.string.picker_pick else Res.string.picker_again))
                                }
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
        }
        }
        if (active && state.resultPopupOpen && state.result != null && headerHeight > 0 && panelHeight > 0 &&
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            PickerResultOverlay(state, popupWidth, popupHeight, popupOffset, pick, {
                onDetails(state.result.preview)
            }, onDismissResult)
        }
    }
}

@Composable
private fun PickerDropdown(title: String, selected: String?, options: List<NuvioDropdownOption>, tag: String,
    expandLabel: Boolean = false, onSelected: (String?) -> Unit) {
    val width = if (expandLabel) Modifier.widthIn(min = 240.dp).width(IntrinsicSize.Max) else Modifier.width(240.dp)
    Column(width.testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        NuvioDropdownChip(title, options.firstOrNull { it.key == selected.orEmpty() }?.label ?: options.first().label,
            selected.orEmpty(), options, onSelected = { onSelected(it.key.takeIf(String::isNotEmpty)) },
            modifier = Modifier.fillMaxWidth(), wrapFilterLabels = true, wrapSelectedLabel = !expandLabel,
            rememberKeyboardSelection = true,
            useGlass = LocalSurfaceStyle.current != SurfaceStyle.Flat)
    }
}

@Composable
private fun PickerFeedback(state: PickerState, modifier: Modifier = Modifier, inline: Boolean = false) {
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
    if (inline) {
        val messages = listOfNotNull(message,
            Res.string.picker_partial.takeIf { state.failedSources > 0 },
            Res.string.picker_metadata_missing.takeIf { state.metadataFailures > 0 },
            Res.string.picker_persistence_failed.takeIf { state.persistenceFailed })
            .map { pickerText(it) }.joinToString(" · ")
        // Reserve the feedback slot even when idle so loading never shifts the actions.
        Row(modifier.height(40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                if (state.picking || state.phase == PickerPhase.LoadingSources) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            Text(messages, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.persistenceFailed) MaterialTheme.colorScheme.error else MaterialTheme.nuvio.colors.textPrimary)
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.picking || state.phase == PickerPhase.LoadingSources) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message != null) Text(pickerText(message), color = MaterialTheme.nuvio.colors.textPrimary)
        if (state.failedSources > 0) Text(pickerText(Res.string.picker_partial))
        if (state.metadataFailures > 0) Text(pickerText(Res.string.picker_metadata_missing))
        if (state.persistenceFailed) Text(pickerText(Res.string.picker_persistence_failed), color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PickerResultOverlay(state: PickerState, width: Dp, maxHeight: Dp, offset: IntOffset, pick: () -> Unit, details: () -> Unit, dismiss: () -> Unit) {
    val result = state.result ?: return
    val posterStyle = rememberPosterCardStyleUiState()
    val shape = shuffleResultShape(posterStyle.cornerRadiusDp)
    val modalFocus = remember { FocusRequester() }
    val againFocus = remember { FocusRequester() }
    val context = LocalPlatformContext.current
    val posterPattern by remember {
        CustomPosterUrlRepository.ensureLoaded()
        CustomPosterUrlRepository.pattern
    }.collectAsStateWithLifecycle()
    val posterScreens by CustomPosterUrlRepository.enabledScreens.collectAsStateWithLifecycle()
    val posterModel = remember(context, result.meta, result.artwork?.poster, posterPattern, posterScreens) {
        val meta = result.meta
        val ids = CustomPosterUrlResolver.extractIds(meta.id, meta.imdbId).let {
            it.copy(tmdbId = it.tmdbId ?: meta.provenance.knownIds["TMDB"])
        }
        val customUrl = if (CustomPosterScreen.DETAILS in posterScreens) {
            CustomPosterUrlResolver.resolve(posterPattern, ids, if (meta.type.equals("movie", true)) "movie" else "series")
        } else null
        if (customUrl != null) {
            ImageRequest.Builder(context).data(customUrl).size(500, 750).apply {
                (result.preview.rawPosterUrl ?: meta.poster)?.let {
                    memoryCacheKeyExtras(mapOf(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to it))
                }
            }.build()
        } else result.artwork?.poster ?: meta.poster
    }
    PlatformBackHandler(enabled = true, onBack = dismiss)
    Box(Modifier.offset { offset }) {
        AppOverlayInput {
        SettingsOverlaySurfaceLayer {
        LaunchedEffect(state.picking) {
            if (state.picking) {
                withFrameNanos { }
                againFocus.requestFocus()
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            if (state.picking) againFocus.requestFocus() else modalFocus.requestFocus()
        }
        RemoteNavigationControls(page = "shuffle_result", row = 0, rememberPosition = true, allowUpToNavigation = true) {
        SettingsAwareSurface(
            modifier = Modifier.width(width).height(maxHeight).remoteNavigationModal(trapFocus = false)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Escape || event.key == Key.Back)) {
                        dismiss(); true
                    } else false
                }.testTag("shuffle_result"),
            shape = shape, color = MaterialTheme.nuvio.colors.surfaceDialog, panel = true,
            contentColor = MaterialTheme.nuvio.colors.textPrimary,
        ) {
            Box(Modifier.fillMaxSize().clip(shape)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                key(result.presentationId, result.preview.type, result.preview.id) {
                BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = (maxHeight - 120.dp).coerceAtLeast(360.dp))
                    .excludeFromRemoteNavigation()
                    .clickable(enabled = !state.picking, onClickLabel = pickerText(Res.string.picker_details), onClick = details)
                    .testTag("shuffle_artwork")) {
                    val wide = maxWidth >= 680.dp
                    ShuffleArtworkImage(result.artwork?.poster ?: result.meta.poster, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    if (result.meta.background != null) ShuffleArtworkImage(result.artwork?.background ?: result.meta.background, null,
                        Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .90f), Color.Black.copy(alpha = .35f)))))
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f)))))
                    val poster: @Composable () -> Unit = {
                        ShuffleArtworkImage(posterModel, result.meta.name, Modifier.width(if (wide) 156.dp else 84.dp).aspectRatio(2f / 3f).clip(shape), contentScale = ContentScale.Crop)
                    }
                    if (wide) Row(Modifier.align(Alignment.BottomStart).padding(start = 32.dp, end = 32.dp, top = 60.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        poster(); Box(Modifier.weight(1f)) { PickerHeroInfo(result) }
                    } else Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, top = 60.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        poster(); PickerHeroInfo(result)
                    }
                }
                }
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                        // The focus stop survives while the actual button is disabled.
                        Box(Modifier.focusRequester(modalFocus)
                            .remoteNavigationTarget(initialFocus = true, onSelect = { if (!state.picking) details() })
                            .then(if (LocalRemoteNavigation.current != null) Modifier.focusable() else Modifier)
                            .semantics { if (state.picking) disabled() }
                            .testTag("shuffle_details")) {
                            Button(onClick = { if (!state.picking) details() }, enabled = !state.picking,
                                modifier = Modifier.excludeFromRemoteNavigation()) {
                                Text(pickerText(Res.string.picker_details))
                            }
                        }
                        // The focus stop survives while the actual button is disabled.
                        Box(Modifier.focusRequester(againFocus)
                            .remoteNavigationTarget(onSelect = { if (!state.picking) pick() })
                            .then(if (LocalRemoteNavigation.current != null) Modifier.focusable() else Modifier)
                            .semantics { if (state.picking) disabled() }
                            .testTag("shuffle_again")) {
                            OutlinedButton(onClick = { if (!state.picking) pick() }, enabled = !state.picking,
                                modifier = Modifier.excludeFromRemoteNavigation()) {
                                Text(pickerText(Res.string.picker_again))
                            }
                        }
                    PickerFeedback(state, modifier = Modifier.weight(1f), inline = true)
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
    }
}

internal fun shuffleResultShape(radiusDp: Int) = RoundedCornerShape(radiusDp.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerHeroInfo(result: PickerResult) {
    val meta = result.meta
    val ratingSettings by remember {
        MdbListSettingsRepository.ensureLoaded()
        MdbListSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val metaSettings by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val externalRatings = meta.externalRatings.takeIf { ratingSettings.isActive }.orEmpty()
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            val summary = listOfNotNull(desktopYearLabel(meta),
                desktopSeasonCountLabel(meta).takeUnless { meta.type.equals("movie", true) },
                formatRuntimeForDisplay(meta.runtime), pickerText(kind),
                meta.language?.trim()?.takeIf { it.isNotEmpty() }?.uppercase())
            summary.forEach { Text(it, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .85f)) }
            val status = meta.status?.takeIf { it.isNotBlank() }?.let {
                localizedMediaStatusLabel(it, !meta.type.equals("movie", true)).uppercase()
            }
            val badges = listOfNotNull(meta.ageRating?.takeIf { it.isNotBlank() }, status)
            if (badges.isNotEmpty()) DetailHeroMetaBadge(badges.first(), secondaryText = badges.getOrNull(1), contentColor = Color.White)
        }
        if (externalRatings.isNotEmpty()) {
            RatingsRow(externalRatings, wrap = true, compact = true)
        } else if (metaSettings.showOverallRatings) {
            meta.pickerRating()?.takeIf { it > 0 }?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ImdbRatingSourceLabel(storeTextStyle = MaterialTheme.typography.labelLarge)
                    Text(formatOneDecimal(it), fontWeight = FontWeight.Bold, color = ImdbYellow)
                }
            }
        }
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
