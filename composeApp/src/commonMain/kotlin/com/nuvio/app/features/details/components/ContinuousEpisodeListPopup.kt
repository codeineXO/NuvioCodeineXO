package com.nuvio.app.features.details.components

import androidx.compose.foundation.border
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import com.nuvio.app.core.ui.NuvioLazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.nuvio.app.core.ui.AppPopup
import com.nuvio.app.core.ui.LocalRemoteNavigation
import com.nuvio.app.core.ui.NuvioDesktopVerticalScrollbar
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.navigationGlass
import com.nuvio.app.core.ui.navigationGlassFeedback
import com.nuvio.app.core.ui.collectIsRemoteHoveredAsState
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioDesktopDragScroll
import com.nuvio.app.core.ui.remoteNavigationModal
import com.nuvio.app.core.ui.remoteNavigationTarget
import com.nuvio.app.features.details.EpisodeRatingsVisibility
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlin.math.abs
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal class ContinuousEpisodeListState(initialHeaderIndex: Int, initialEpisodeIndex: Int) {
    val scroll = LazyListState(firstVisibleItemIndex = initialHeaderIndex)
    var focusedIndex by mutableIntStateOf(initialEpisodeIndex)
}

@Composable
internal fun rememberContinuousEpisodeListState(
    metaId: String,
    selectedSeason: Int,
    seasons: List<Int>,
    episodes: Map<Int, List<MetaVideo>>,
): ContinuousEpisodeListState = remember(metaId, selectedSeason) {
    val precedingCount = seasons.takeWhile { it != selectedSeason }.sumOf { 1 + episodes[it].orEmpty().size }
    ContinuousEpisodeListState(precedingCount, precedingCount + if (episodes[selectedSeason].isNullOrEmpty()) 0 else 1)
}

private data class EpisodeListEntry(val season: Int, val episode: MetaVideo?, val key: String)

// Popup alignment normally uses the episode section's anchor, rather than the app window.
private object CenteredEpisodePopupPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset((windowSize.width - popupContentSize.width) / 2, (windowSize.height - popupContentSize.height) / 2)
}

private fun KeyEvent.isIgnoredEpisodePopupKey(): Boolean =
    isAltPressed || isCtrlPressed || isMetaPressed || isShiftPressed || key in listOf(
        Key.Tab, Key.AltLeft, Key.AltRight, Key.CtrlLeft, Key.CtrlRight,
        Key.MetaLeft, Key.MetaRight, Key.ShiftLeft, Key.ShiftRight,
    )

@Composable
internal fun ContinuousEpisodeListPopup(
    meta: MetaDetails,
    seasons: List<Int>,
    groupedEpisodes: Map<Int, List<MetaVideo>>,
    state: ContinuousEpisodeListState,
    progressByVideoId: Map<String, WatchProgressEntry>,
    watchedKeys: Set<String>,
    episodeRatings: Map<Pair<Int, Int>, Double>,
    episodeRatingsVisibility: EpisodeRatingsVisibility,
    blurUnwatchedEpisodes: Boolean,
    onDismiss: () -> Unit,
    onEpisodeClick: (MetaVideo) -> Unit,
    onEpisodeLongPress: ((MetaVideo) -> Unit)?,
) {
    val entries = remember(seasons, groupedEpisodes) {
        buildList {
            seasons.forEach { season ->
                add(EpisodeListEntry(season, null, "season:$season"))
                groupedEpisodes[season].orEmpty().forEachIndexed { index, episode ->
                    add(EpisodeListEntry(season, episode, "episode:$season:$index:${episode.id}"))
                }
            }
        }
    }
    val episodeIndices = remember(entries) { entries.indices.filter { entries[it].episode != null } }
    val requesters = remember(entries) { entries.map { FocusRequester() } }
    val closeFocus = remember { FocusRequester() }
    var closeFocused by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tokens = MaterialTheme.nuvio
    val navigation = LocalRemoteNavigation.current
    val railState = rememberLazyListState()
    val railRequesters = remember(seasons) { seasons.map { FocusRequester() } }
    var railFocusedIndex by remember { mutableStateOf<Int?>(null) }
    var railHoveredIndex by remember { mutableStateOf<Int?>(null) }
    var railScrubbing by remember { mutableStateOf(false) }
    var scrubbedSeason by remember { mutableStateOf<Int?>(null) }
    var seasonScroll by remember { mutableStateOf<Job?>(null) }
    val activeSeason by remember(entries, state) {
        derivedStateOf {
            val first = state.scroll.layoutInfo.visibleItemsInfo.firstOrNull {
                it.offset + it.size > state.scroll.layoutInfo.viewportStartOffset
            }?.index ?: state.scroll.firstVisibleItemIndex
            entries.getOrNull(first)?.season ?: seasons.firstOrNull()
        }
    }

    fun focusSeason(index: Int) {
        if (index !in seasons.indices || moving) return
        moving = true
        scope.launch {
            try {
                if (railState.layoutInfo.visibleItemsInfo.none { it.index == index }) railState.scrollToItem(index)
                withFrameNanos { }
                railRequesters[index].requestFocus()
            } finally { moving = false }
        }
    }

    fun jumpToSeason(season: Int) {
        if (moving) return
        val header = entries.indexOfFirst { it.season == season && it.episode == null }
        if (header < 0) return
        moving = true
        scope.launch {
            try {
                state.scroll.scrollToItem(header)
                withFrameNanos { }
                val firstEpisode = episodeIndices.firstOrNull { entries[it].season == season }
                if (firstEpisode != null) requesters[firstEpisode].requestFocus()
            } finally { moving = false }
        }
    }

    val onSeasonWheel by rememberUpdatedState<(Int) -> Unit> { step ->
        val index = seasons.indexOf(activeSeason).coerceAtLeast(0)
        seasons.getOrNull(index + step)?.let { jumpToSeason(it) }
    }

    LaunchedEffect(activeSeason, railFocusedIndex, railScrubbing) {
        val index = seasons.indexOf(activeSeason)
        if (!railScrubbing && railFocusedIndex == null && index >= 0 && railState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            railState.scrollToItem(index)
        }
    }

    fun focusEpisode(index: Int) {
        if (moving || index !in episodeIndices) return
        moving = true
        scope.launch {
            try {
                val visible = state.scroll.layoutInfo.visibleItemsInfo
                if (visible.none { it.index == index }) state.scroll.scrollToItem(index)
                withFrameNanos { }
                requesters[index].requestFocus()
            } finally {
                moving = false
            }
        }
    }

    PlatformBackHandler(enabled = true, onBack = onDismiss)
    AppPopup(
        popupPositionProvider = CenteredEpisodePopupPosition,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        BoxWithConstraints {
            Row(
                modifier = Modifier
                    .width(minOf(960.dp, (maxWidth - 32.dp).coerceAtLeast(0.dp)))
                    .height(maxHeight * 0.85f)
                    .navigationGlass(shape = tokens.shapes.dialog, panel = true)
                    .border(tokens.borders.thin, tokens.colors.textPrimary.copy(alpha = 0.12f), tokens.shapes.dialog)
                    .padding(start = 8.dp)
                    // Consume locally before the modal or a child can react. Native OS shortcuts
                    // are dispatched by the window system before Compose receives these events.
                    .onPreviewKeyEvent { it.isIgnoredEpisodePopupKey() }
                    .remoteNavigationModal()
                    .onPreviewKeyEvent { event ->
                        val actionKey = event.key in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft,
                            Key.DirectionRight, Key.MoveHome, Key.MoveEnd, Key.PageUp, Key.PageDown,
                            Key.Enter, Key.NumPadEnter, Key.Escape, Key.Back)
                        if (!actionKey) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyDown) navigation?.keyboard = true
                        if (event.key == Key.Escape || event.key == Key.Back) {
                            if (event.type == KeyEventType.KeyDown) onDismiss()
                            return@onPreviewKeyEvent true
                        }
                        val railIndex = railFocusedIndex
                        if (railIndex != null) {
                            when (event.key) {
                                Key.DirectionUp, Key.DirectionDown, Key.MoveHome, Key.MoveEnd -> {
                                    if (event.type == KeyEventType.KeyDown) focusSeason(when (event.key) {
                                        Key.MoveHome -> 0
                                        Key.MoveEnd -> seasons.lastIndex
                                        Key.DirectionUp -> (railIndex - 1).coerceAtLeast(0)
                                        else -> (railIndex + 1).coerceAtMost(seasons.lastIndex)
                                    })
                                    return@onPreviewKeyEvent true
                                }
                                Key.DirectionRight -> {
                                    if (event.type == KeyEventType.KeyDown) {
                                        val target = state.focusedIndex.takeIf { it in episodeIndices }
                                            ?: episodeIndices.firstOrNull()
                                        if (target != null) focusEpisode(target) else closeFocus.requestFocus()
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                Key.DirectionLeft -> return@onPreviewKeyEvent true
                                else -> return@onPreviewKeyEvent false
                            }
                        }
                        if (event.key == Key.DirectionLeft) {
                            if (event.type == KeyEventType.KeyDown) focusSeason(seasons.indexOf(
                                entries.getOrNull(state.focusedIndex)?.season ?: activeSeason).coerceAtLeast(0))
                            return@onPreviewKeyEvent true
                        }
                        if (event.key == Key.DirectionRight) return@onPreviewKeyEvent true
                        if (episodeIndices.isEmpty()) return@onPreviewKeyEvent false
                        val ordinal = episodeIndices.indexOf(state.focusedIndex).coerceAtLeast(0)
                        val pageSize = state.scroll.layoutInfo.visibleItemsInfo.count { it.index in episodeIndices }.coerceAtLeast(1)
                        val target = when (event.key) {
                            Key.DirectionDown -> if (closeFocused) episodeIndices.getOrNull(ordinal)
                                else episodeIndices.getOrNull(ordinal + 1)
                            Key.DirectionUp -> if (closeFocused) null else episodeIndices.getOrNull(ordinal - 1)
                            Key.MoveHome -> episodeIndices.firstOrNull()
                            Key.MoveEnd -> episodeIndices.lastOrNull()
                            Key.PageDown -> episodeIndices.getOrNull((ordinal + pageSize).coerceAtMost(episodeIndices.lastIndex))
                            Key.PageUp -> episodeIndices.getOrNull((ordinal - pageSize).coerceAtLeast(0))
                            else -> return@onPreviewKeyEvent false
                        }
                        if (event.type == KeyEventType.KeyDown) {
                            if (target != null) focusEpisode(target)
                            else if (event.key == Key.DirectionUp && !closeFocused) closeFocus.requestFocus()
                        }
                        true
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val railItemHeight = when {
                    seasons.size >= 30 -> 12.dp
                    seasons.size >= 20 -> 14.dp
                    seasons.size >= 12 -> 16.dp
                    else -> 18.dp
                }
                val scrubbedIndex = if (railScrubbing) scrubbedSeason?.let { seasons.indexOf(it) }?.takeIf { it >= 0 } else null

                NuvioLazyColumn(
                    state = railState,
                    desktopDragEnabled = false,
                    modifier = Modifier.width(48.dp).fillMaxHeight().padding(vertical = 16.dp)
                        .seasonPickerDrag(
                            railState = railState,
                            onStart = {
                                railScrubbing = true
                                navigation?.keyboard = false
                            },
                            onSelect = { index ->
                                val season = seasons.getOrNull(index)
                                if (season != null && season != scrubbedSeason) {
                                    scrubbedSeason = season
                                    val header = entries.indexOfFirst { it.season == season && it.episode == null }
                                    val episodeIndex = episodeIndices.firstOrNull { entries[it].season == season }
                                    if (episodeIndex != null) state.focusedIndex = episodeIndex
                                    seasonScroll?.cancel()
                                    // Scrubbing should track the selected season without measuring every intervening episode.
                                    if (header >= 0) seasonScroll = scope.launch { state.scroll.scrollToItem(header) }
                                }
                            },
                            onEnd = {
                                val season = scrubbedSeason
                                val scroll = seasonScroll
                                scrubbedSeason = null
                                railScrubbing = false
                                if (season != null) scope.launch {
                                    // Wait for the selected season to be laid out before restoring focus.
                                    scroll?.join()
                                    withFrameNanos { }
                                    val firstEpisode = episodeIndices.firstOrNull { entries[it].season == season }
                                    if (!railScrubbing && firstEpisode != null && firstEpisode == state.focusedIndex &&
                                        state.scroll.layoutInfo.visibleItemsInfo.any { it.index == firstEpisode }) {
                                        requesters[firstEpisode].requestFocus()
                                    }
                                }
                            },
                        )
                        .pointerInput(seasons) {
                            var lastStepTime = 0L
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Exit) {
                                        railHoveredIndex = null
                                    }
                                    if (event.type != PointerEventType.Scroll) continue
                                    val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }
                                    if (delta == 0.0) continue
                                    val time = event.changes.first().uptimeMillis
                                    if (time - lastStepTime >= 180L) {
                                        lastStepTime = time
                                        onSeasonWheel(if (delta > 0) 1 else -1)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        },
                    verticalArrangement = Arrangement.Center,
                ) {
                    itemsIndexed(seasons, key = { _, season -> season }) { index, season ->
                        val isAdjacent = (railFocusedIndex != null && abs(index - railFocusedIndex!!) == 1) ||
                            (railHoveredIndex != null && abs(index - railHoveredIndex!!) == 1) ||
                            (scrubbedIndex != null && abs(index - scrubbedIndex) == 1)
                        SeasonRailMark(
                            label = if (season <= 0) stringResource(Res.string.episodes_specials)
                                else stringResource(Res.string.episodes_season, season),
                            active = season == (scrubbedSeason ?: activeSeason),
                            isAdjacent = isAdjacent,
                            itemHeight = railItemHeight,
                            onClick = { jumpToSeason(season) },
                            onHoverChanged = { isHovered ->
                                if (isHovered) {
                                    railHoveredIndex = index
                                } else if (railHoveredIndex == index) {
                                    railHoveredIndex = null
                                }
                            },
                            modifier = Modifier.focusRequester(railRequesters[index]).onFocusChanged {
                                if (it.hasFocus) railFocusedIndex = index
                                else if (railFocusedIndex == index) railFocusedIndex = null
                            },
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .padding(top = tokens.spacing.dialogPadding, bottom = tokens.spacing.dialogPadding,
                            end = tokens.spacing.dialogPadding, start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = meta.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
                            color = tokens.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.focusRequester(closeFocus)
                                .onFocusChanged { closeFocused = it.isFocused }
                                .remoteNavigationTarget(shape = tokens.shapes.button),
                        ) {
                            Icon(Icons.Rounded.Close, stringResource(Res.string.action_close), tint = tokens.colors.textPrimary)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        NuvioLazyColumn(
                            state = state.scroll,
                            modifier = Modifier.fillMaxSize().padding(end = 16.dp),
                            contentPadding = PaddingValues(4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(entries, key = { _, entry -> entry.key }, contentType = { _, entry -> entry.episode == null }) { index, entry ->
                                val episode = entry.episode
                                if (episode == null) {
                                    Text(
                                        text = if (entry.season <= 0) stringResource(Res.string.episodes_specials)
                                            else stringResource(Res.string.episodes_season, entry.season),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tokens.colors.textPrimary,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                } else {
                                    DetailSeriesListEpisode(
                                        meta = meta,
                                        episode = episode,
                                        progressByVideoId = progressByVideoId,
                                        watchedKeys = watchedKeys,
                                        episodeRatings = episodeRatings,
                                        episodeRatingsVisibility = episodeRatingsVisibility,
                                        blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                                        onEpisodeClick = onEpisodeClick,
                                        onEpisodeLongPress = onEpisodeLongPress,
                                        popupPresentation = true,
                                        modifier = Modifier.focusRequester(requesters[index])
                                            .onFocusChanged { if (it.hasFocus) state.focusedIndex = index },
                                    )
                                }
                            }
                        }
                        NuvioDesktopVerticalScrollbar(state.scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
                }
            }
        }
        LaunchedEffect(entries) {
            withFrameNanos { }
            if (episodeIndices.isEmpty()) closeFocus.requestFocus()
            else focusEpisode(state.focusedIndex.takeIf { it in episodeIndices } ?: episodeIndices.first())
        }
    }
}

/** Claim movement after touch slop so a drag never activates a season mark's click. */
@Composable
internal fun Modifier.seasonPickerDrag(
    railState: LazyListState,
    onStart: () -> Unit,
    onSelect: (Int) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    val currentStart by rememberUpdatedState(onStart)
    val currentSelect by rememberUpdatedState(onSelect)
    val currentEnd by rememberUpdatedState(onEnd)
    return pointerInput(railState) {
        coroutineScope {
            val scope = this
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var dragging = false
                var pointerY = down.position.y
                var lastSelected: Int? = null
                var motion: Job? = null
                fun selectNearest() {
                    val nearest = railState.layoutInfo.visibleItemsInfo.minByOrNull {
                        abs(it.offset + it.size / 2f - pointerY)
                    }?.index ?: return
                    if (nearest != lastSelected) {
                        lastSelected = nearest
                        currentSelect(nearest)
                    }
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        pointerY = change.position.y
                        if (!change.pressed) {
                            if (dragging) {
                                selectNearest()
                                change.consume()
                            }
                            break
                        }
                        if (event.changes.count { it.pressed } != 1) break
                        if (!dragging) {
                            val distance = change.position - down.position
                            if (abs(distance.y) <= viewConfiguration.touchSlop) continue
                            if (abs(distance.x) > abs(distance.y)) break
                            dragging = true
                            currentStart()
                            selectNearest()
                            motion = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                railState.scroll(MutatePriority.UserInput) {
                                    var previous: Long? = null
                                    while (true) {
                                        val frame = withFrameNanos { it }
                                        val seconds = previous?.let { ((frame - it) / 1_000_000_000f).coerceIn(0f, 0.1f) } ?: (1f / 60)
                                        previous = frame
                                        val info = railState.layoutInfo
                                        val speed = seasonRailEdgeVelocity(
                                            pointerY, info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat(),
                                            16.dp.toPx(), 240.dp.toPx(),
                                        )
                                        if (speed != 0f) scrollBy(speed * seconds)
                                        selectNearest()
                                    }
                                }
                            }
                        }
                        change.consume()
                    }
                } finally {
                    motion?.cancel()
                    if (dragging) currentEnd()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonRailMark(
    label: String,
    active: Boolean,
    isAdjacent: Boolean,
    itemHeight: Dp,
    onClick: () -> Unit,
    onHoverChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tooltip = rememberTooltipState()
    var focused by remember { mutableStateOf(false) }
    val tokens = MaterialTheme.nuvio
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsRemoteHoveredAsState()

    LaunchedEffect(hovered) {
        onHoverChanged(hovered)
    }

    val isDirectlyExpanded = active || focused || hovered
    val markWidth by animateDpAsState(
        when {
            isDirectlyExpanded -> 28.dp
            isAdjacent -> 22.dp
            else -> 16.dp
        }
    )
    val barThickness by animateDpAsState(
        when {
            isDirectlyExpanded -> 4.5.dp
            isAdjacent -> 4.dp
            else -> 3.5.dp
        }
    )
    val barAlpha by animateFloatAsState(
        when {
            isDirectlyExpanded -> 1f
            isAdjacent -> 0.85f
            else -> 0.6f
        }
    )
    LaunchedEffect(focused) {
        if (focused) tooltip.show() else tooltip.dismiss()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltip,
        focusable = false,
    ) {
        Box(
            modifier = modifier.size(width = 48.dp, height = itemHeight)
                .onFocusChanged { focused = it.hasFocus }
                .navigationGlassFeedback(interactionSource, tokens.shapes.button, selected = active)
                .hoverable(interactionSource)
                .remoteNavigationTarget(shape = tokens.shapes.button, onSelect = onClick)
                .semantics { contentDescription = label; selected = active }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(28.dp).height(5.dp), contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier
                        .width(markWidth)
                        .height(barThickness)
                        .background(tokens.colors.textPrimary.copy(alpha = barAlpha), CircleShape)
                )
            }
        }
    }
}
