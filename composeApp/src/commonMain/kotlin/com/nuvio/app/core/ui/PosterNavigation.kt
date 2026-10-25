package com.nuvio.app.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

internal enum class PosterTargetRole { Poster, Header, EndAction }
internal enum class PosterRowReveal { PreservePosition, Centered }
internal enum class PosterRestorationState { Idle, Pending, Running }
internal data class PosterMoveCommand(val source: RemoteFocusTarget, val direction: FocusDirection, val repeated: Boolean)
internal data class PosterNavigationItem(
    val page: String, val row: Any, val content: Any?, val index: Int,
    val role: PosterTargetRole = PosterTargetRole.Poster,
)
internal data class PosterNavigationRow(val key: Any, val items: List<Any>, val lazyIndex: Int, val grid: Boolean = false)
internal data class PosterOrigin(
    val context: Any, val row: Any, val content: Any?, val index: Int, val rowIndex: Int,
    val pageIndex: Int, val pageOffset: Int, val shelfIndex: Int, val shelfOffset: Int,
    val neighbors: List<Any>,
)
internal class PosterNavigationPage {
    var route: Any = Unit
    var context: Any = Unit
    var rows: List<PosterNavigationRow> = emptyList()
    var state: LazyListState? = null
    var active = false
    var origin: PosterOrigin? = null
    var lane: Float? = null
    var restorationState = PosterRestorationState.Idle
    var restorationGeneration = 0
    var restorationJob: Job? = null
    fun cancelRestoration(discardBookmark: Boolean = false) {
        restorationGeneration++
        restorationState = PosterRestorationState.Idle
        restorationJob?.cancel()
        restorationJob = null
        if (discardBookmark) origin = null
    }
    val shelves = mutableMapOf<Any, LazyListState>()
}
internal val LocalPosterNavigationItem = staticCompositionLocalOf<PosterNavigationItem?> { null }
internal val LocalPosterNavigationRow = staticCompositionLocalOf<Pair<String, Any>?> { null }

@Composable
internal fun RemotePosterRow(page: String, row: Any, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPosterNavigationRow provides (page to row), content = content)
}

@Composable
internal fun RemotePosterItem(id: Any?, index: Int, role: PosterTargetRole = PosterTargetRole.Poster, content: @Composable () -> Unit) {
    val row = LocalPosterNavigationRow.current
    val session = LocalRemoteNavigation.current
    val position = if (role == PosterTargetRole.EndAction) session?.posterPages?.get(row?.first)?.rows
        ?.firstOrNull { it.key == row?.second }?.items?.size ?: index else index
    CompositionLocalProvider(
        LocalPosterNavigationItem provides row?.let { PosterNavigationItem(it.first, it.second, id, position, role) },
        content = content,
    )
}

@Composable
internal fun BindPosterShelf(state: LazyListState) {
    val session = LocalRemoteNavigation.current ?: return
    val row = LocalPosterNavigationRow.current ?: return
    val route = LocalRemoteRoute.current
    val active = LocalScreenActive.current
    DisposableEffect(session, row, state) {
        val page = session.posterPages.getOrPut(row.first) { PosterNavigationPage() }
        page.shelves[row.second] = state
        onDispose { if (page.shelves[row.second] === state) page.shelves.remove(row.second) }
    }
    LaunchedEffect(session, row, state, active) {
        if (active) snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .drop(1).collect {
                if (!session.keyboard && !session.explicitFocusTransfer && !session.posterRestorationPending(route)) {
                    session.manualScroll(route)
                }
            }
    }
}

/** Registered outside lazy items so navigation can reveal rows that are not composed. */
@Composable
internal fun BindPosterNavigationPage(pageId: String, context: Any, rows: List<PosterNavigationRow>, state: LazyListState, loading: Boolean) {
    val session = LocalRemoteNavigation.current ?: return
    val route = LocalRemoteRoute.current
    val active = LocalScreenActive.current
    val page = remember(session, pageId) { session.posterPages.getOrPut(pageId) { PosterNavigationPage() } }
    val currentLoading by rememberUpdatedState(loading)
    SideEffect {
        if (page.context != context) {
            session.cancelPosterNavigation(pageId)
            page.cancelRestoration()
            page.origin = null
            page.lane = null
            page.context = context
            session.rememberedFocus.remove(route)
            session.rememberedBounds.remove(route)
        }
        page.rows = rows
        page.state = state
        page.route = route
        if (active && !page.active && page.origin != null) {
            page.restorationState = PosterRestorationState.Pending
        }
        page.active = active
        if (!active) {
            page.cancelRestoration()
            session.cancelPosterNavigation(pageId)
        }
    }
    DisposableEffect(page) { onDispose { page.active = false; page.cancelRestoration(); session.cancelPosterNavigation(pageId) } }
    LaunchedEffect(active, context) {
        if (!active || page.origin == null) return@LaunchedEffect
        val origin = page.origin ?: return@LaunchedEffect
        val generation = ++page.restorationGeneration
        page.restorationState = PosterRestorationState.Running
        page.restorationJob = currentCoroutineContext()[Job]
        var completed = false
        var restored = false
        try {
            restored = withTimeoutOrNull(5_000) {
                withFrameNanos { }
                snapshotFlow { currentLoading }.first { !it }
                if (origin.context != context || !page.active) return@withTimeoutOrNull false
                val row = page.rows.firstOrNull { it.grid && origin.content in it.items }
                    ?: page.rows.firstOrNull { it.key == origin.row && it.items.isNotEmpty() }
                    ?: page.rows.filter { it.items.isNotEmpty() }.minByOrNull { abs(page.rows.indexOf(it) - origin.rowIndex) }
                if (row != null) {
                    val neighbor = origin.neighbors.withIndex().filter { it.value in row.items }
                        .minByOrNull { abs(it.index - origin.index) }?.value
                    val index = row.items.indexOf(origin.content).takeIf { it >= 0 }
                        ?: row.items.indexOf(neighbor).takeIf { it >= 0 }
                        ?: origin.index.coerceIn(0, row.items.lastIndex)
                    if (state.layoutInfo.totalItemsCount > origin.pageIndex) state.scrollToItem(origin.pageIndex, origin.pageOffset)
                    session.revealPosterRow(pageId, row)
                    page.shelves[row.key]?.let { shelf ->
                        shelf.scrollToItem(origin.shelfIndex.coerceAtMost(row.items.lastIndex), origin.shelfOffset)
                    }
                    session.focusPoster(pageId, row, index)
                } else {
                    // Empty pages retain a useful input, retry or content action.
                    withFrameNanos { }
                    session.focusAvailableContent(route)
                }
            } == true
            completed = true
            if (!restored) {
                // Commit fallback focus on the scene's frame, never on the timeout timer.
                withFrameNanos { }
                if (page.restorationGeneration == generation && page.active) session.focusAvailableContent(route)
            }
        } finally {
            if (page.restorationGeneration == generation) {
                page.restorationState = PosterRestorationState.Idle
                page.restorationJob = null
                if (completed && page.origin === origin) page.origin = null
            }
        }
    }
}

internal fun RemoteNavigationSession.capturePosterOrigin(item: PosterNavigationItem?) {
    if (item == null || item.role != PosterTargetRole.Poster) return
    cancelPosterNavigation(item.page)
    val page = posterPages[item.page] ?: return
    val state = page.state ?: return
    pendingViewportRoutes -= page.route
    page.cancelRestoration()
    focusTransferReason = RemoteFocusTransferReason.Navigation
    val shelf = page.shelves[item.row]
    page.origin = PosterOrigin(page.context, item.row, item.content, item.index,
        page.rows.indexOfFirst { it.key == item.row }, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset,
        shelf?.firstVisibleItemIndex ?: 0, shelf?.firstVisibleItemScrollOffset ?: 0,
        page.rows.firstOrNull { it.key == item.row }?.items.orEmpty())
}

internal fun RemoteNavigationSession.posterRestorationPending(route: Any): Boolean =
    posterPages.values.any { it.route == route && it.active && it.restorationState != PosterRestorationState.Idle }

internal fun RemoteNavigationSession.handlesPosterDirection(item: PosterNavigationItem?, direction: FocusDirection): Boolean {
    if (item == null || item.role == PosterTargetRole.Header) return false
    if (posterPages[item.page]?.rows?.none { it.key == item.row } != false) return false
    if (direction == FocusDirection.Left || direction == FocusDirection.Right) return true
    val rows = posterPages[item.page]?.rows.orEmpty().filter { it.items.isNotEmpty() }
    val index = rows.indexOfFirst { it.key == item.row }
    return index >= 0 && ((direction == FocusDirection.Up && index > 0) || (direction == FocusDirection.Down && index < rows.lastIndex))
}

internal fun RemoteNavigationSession.cancelPosterNavigation(pageId: String? = null) {
    if (pageId != null && posterMovePage != pageId) return
    posterMoveGeneration++
    pendingPosterMove = null
    posterPressedDirection = null
    posterRepeatingDirection = null
    posterMoveDirection = null
    posterMovePage = null
    posterMoveJob?.cancel()
    posterMoveJob = null
}

internal fun RemoteNavigationSession.releasePosterDirection(direction: FocusDirection) {
    if (posterPressedDirection == direction) posterPressedDirection = null
    if (posterRepeatingDirection == direction) posterRepeatingDirection = null
    if (pendingPosterMove?.let { it.repeated && it.direction == direction } == true) pendingPosterMove = null
}

/** Match the immediate horizontal focus transfers used by unregistered collection shelves. */
internal fun RemoteNavigationSession.movePosterHorizontally(source: RemoteFocusTarget, direction: FocusDirection) {
    val item = source.poster ?: return
    val page = posterPages[item.page] ?: return
    val row = page.rows.firstOrNull { it.key == item.row } ?: return
    if (posterMoveJob?.isActive == true) cancelPosterNavigation()
    val next = item.index + if (direction == FocusDirection.Right) 1 else -1
    if (item.role != PosterTargetRole.Poster || next !in row.items.indices) {
        enqueuePosterMove(source, direction)
        return
    }
    posterRepeatingDirection = direction.takeIf { posterPressedDirection == direction }
    posterPressedDirection = direction
    page.lane = null
    val target = targets.firstOrNull { it.poster?.let { p ->
        p.page == item.page && p.row == item.row && p.index == next && p.role == PosterTargetRole.Poster
    } == true }
    val moved = target?.requester?.requestFocus() == true || source.manager.moveFocus(direction)
    val destination = focusedTarget?.poster
    if (!moved || destination == null || destination.page != item.page || destination.row != item.row || destination.index != next) {
        source.requester.requestFocus()
        enqueuePosterMove(source, direction)
    } else if (direction == FocusDirection.Left && leftHeld) leftMovedDuringHold = true
}

internal suspend fun RemoteNavigationSession.revealHorizontallyFocusedPoster(item: PosterNavigationItem) {
    // Native focus changes stay immediate; the reveal follows the latest selection
    // and is cancelled by its focus effect when another poster is selected.
    withFrameNanos { }
    posterPages[item.page]?.shelves?.get(item.row)?.revealPosterEdge(
        item.index, animated = true, fast = { posterRepeatingDirection != null }, followingIndex = null,
    )
}

internal fun RemoteNavigationSession.enqueuePosterMove(source: RemoteFocusTarget, direction: FocusDirection) {
    val pageId = source.poster?.page ?: return
    val scope = navigationScope ?: return
    val command = PosterMoveCommand(source, direction, posterPressedDirection == direction)
    posterPressedDirection = direction
    posterRepeatingDirection = direction.takeIf { command.repeated }
    if (posterMoveJob?.isActive == true) {
        val reversing = when (posterMoveDirection) {
            FocusDirection.Left -> direction == FocusDirection.Right
            FocusDirection.Right -> direction == FocusDirection.Left
            FocusDirection.Up -> direction == FocusDirection.Down
            FocusDirection.Down -> direction == FocusDirection.Up
            else -> false
        }
        if (!reversing) { pendingPosterMove = command; return }
        cancelPosterNavigation()
        posterPressedDirection = direction
    }
    val generation = ++posterMoveGeneration
    posterMovePage = pageId
    posterMoveJob = scope.launch {
        var next: PosterMoveCommand? = command
        try {
            while (next != null && keyboard && posterPages[pageId]?.active == true) {
                val move = next
                posterMoveDirection = move.direction
                val current = focusedTarget?.takeIf { it.poster?.page == pageId }
                    ?: move.source
                if (handlesPosterDirection(current.poster, move.direction)) movePoster(current, move.direction)
                else if (current in targets) moveFocus(current.route, move.direction, current.manager)
                else {
                    // A reversal may arrive after lazy scrolling disposed the logical source.
                    val item = current.poster ?: break
                    val row = posterPages[pageId]?.rows?.firstOrNull { it.key == item.row && it.items.isNotEmpty() } ?: break
                    explicitFocusTransfers++
                    try {
                        revealPosterRow(pageId, row, PosterRowReveal.Centered)
                        focusPoster(pageId, row, row.items.indexOf(item.content).takeIf { it >= 0 }
                            ?: item.index.coerceIn(0, row.items.lastIndex), animated = true)
                    } finally { explicitFocusTransfers-- }
                }
                next = pendingPosterMove
                pendingPosterMove = null
            }
        } finally {
            if (generation == posterMoveGeneration) {
                pendingPosterMove = null
                posterMoveDirection = null
                posterMovePage = null
                posterMoveJob = null
            }
        }
    }
}

internal suspend fun RemoteNavigationSession.movePoster(source: RemoteFocusTarget, direction: FocusDirection) {
    explicitFocusTransfers++
    try {
        movePosterInPage(source, direction)
        // Keep automatic focus reveal suppressed until the new focus has settled.
        withFrameNanos { }
        withFrameNanos { }
    } finally { explicitFocusTransfers-- }
}

private suspend fun RemoteNavigationSession.movePosterInPage(source: RemoteFocusTarget, direction: FocusDirection) {
    val item = source.poster ?: return
    val page = posterPages[item.page] ?: return
    val rows = page.rows.filter { it.items.isNotEmpty() }
    val row = rows.firstOrNull { it.key == item.row } ?: return
    if (direction == FocusDirection.Left || direction == FocusDirection.Right) {
        page.lane = null
        if (item.role == PosterTargetRole.EndAction && direction == FocusDirection.Right) return
        val next = if (item.role == PosterTargetRole.EndAction) row.items.lastIndex
            else item.index + if (direction == FocusDirection.Right) 1 else -1
        if (next in row.items.indices) {
            val nativeHorizontal = item.page == "home" || item.page == "collection"
            if (focusPoster(item.page, row, next, animated = !nativeHorizontal,
                    rightLookAhead = !nativeHorizontal && direction == FocusDirection.Right,
                    anchorPrecedingPoster = !row.grid) &&
                direction == FocusDirection.Left && leftHeld) leftMovedDuringHold = true
        } else if (direction == FocusDirection.Right && item.role == PosterTargetRole.Poster) {
            targets.firstOrNull { it.poster?.let { p -> p.page == item.page && p.row == item.row && p.role == PosterTargetRole.EndAction } == true }
                ?.requester?.requestFocus()
        } else if (direction == FocusDirection.Left && canEnterSidebar()) {
            targets.firstOrNull { it.route == source.route && it.role == RemoteNavigationRole.Sidebar && it.sidebarEntry }
                ?.requester?.requestFocus()
        }
        page.lane = focusedTarget?.bounds?.center?.x ?: source.bounds?.center?.x
        return
    }
    val lane = page.lane ?: source.bounds?.center?.x ?: return
    page.lane = lane
    val next = rows.getOrNull(rows.indexOf(row) + if (direction == FocusDirection.Down) 1 else -1) ?: return
    fun destination(): RemoteFocusTarget? {
        val candidates = targets.filter { it.poster?.let { p -> p.page == item.page && p.row == next.key && p.role == PosterTargetRole.Poster } == true }
        val visible = page.shelves[next.key]?.layoutInfo?.let { layout ->
            layout.visibleItemsInfo.filter {
                it.offset >= layout.viewportStartOffset + layout.beforeContentPadding &&
                    it.offset + it.size <= layout.viewportEndOffset - layout.afterContentPadding
            }.map { it.index }.toSet().ifEmpty { setOfNotNull(firstVisibleNavigationIndex(layout)) }
        }
        return candidates.filter { visible == null || it.poster!!.index in visible }.ifEmpty { candidates }
            .minByOrNull { abs((it.bounds?.center?.x ?: Float.MAX_VALUE) - lane) }
    }
    var selected: RemoteFocusTarget? = null
    revealPosterRow(item.page, next, PosterRowReveal.Centered, onPositioned = {
        // Show the outline as soon as the destination fits, while centering continues.
        if (!next.grid && selected == null) {
            val layout = page.state?.layoutInfo
            val measured = layout?.visibleItemsInfo?.firstOrNull { it.index == next.lazyIndex }
            if (layout != null && measured != null && measured.offset >= layout.viewportStartOffset &&
                measured.offset + measured.size <= layout.viewportEndOffset) {
                destination()?.let { if (it.requester.requestFocus()) selected = it }
            }
        }
    })
    (selected ?: destination())?.poster?.index?.let { focusPoster(item.page, next, it, animated = true) }
}

internal suspend fun RemoteNavigationSession.revealPosterRow(
    pageId: String,
    row: PosterNavigationRow,
    policy: PosterRowReveal = PosterRowReveal.PreservePosition,
    onPositioned: () -> Unit = {},
) {
    val page = posterPages[pageId] ?: return
    val state = page.state ?: return
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo.firstOrNull { it.index == row.lazyIndex }
    fun viewportStart(layout: LazyListLayoutInfo): Int =
        layout.visibleItemsInfo.firstOrNull { it.index == 0 && it.offset <= layout.viewportStartOffset }
            ?.takeIf { pageId == "search" }?.let { it.offset + it.size } ?: layout.viewportStartOffset
    val headerBottom = viewportStart(info)
    if (policy == PosterRowReveal.Centered) {
        state.animatePosterScroll(fast = { fastPosterScroll }, onPositioned = onPositioned) { layout ->
            val measured = layout.visibleItemsInfo.firstOrNull { it.index == row.lazyIndex }
            val nearest = measured ?: layout.visibleItemsInfo.filter { pageId != "search" || it.index != 0 }
                .minByOrNull { abs(it.index - row.lazyIndex) }
            if (nearest == null) 0f else {
                val offset = nearest.offset.toFloat() + (row.lazyIndex - nearest.index).toFloat() * (nearest.size + layout.mainAxisItemSpacing)
                offset + nearest.size / 2f - (viewportStart(layout) + layout.viewportEndOffset) / 2f
            }
        }
    } else if (visible == null) {
        state.scrollToItem(row.lazyIndex, -headerBottom)
    } else {
        val delta = when {
            visible.offset < headerBottom -> visible.offset - headerBottom
            visible.offset + visible.size > info.viewportEndOffset -> minOf(visible.offset - headerBottom, visible.offset + visible.size - info.viewportEndOffset)
            else -> 0
        }
        if (delta != 0) state.scrollBy(delta.toFloat())
    }
    onPositioned()
    withFrameNanos { }
    withTimeoutOrNull(1500) {
        snapshotFlow { targets.any { it.poster?.let { p -> p.page == pageId && p.row == row.key } == true && it.bounds != null } }.first { it }
    }
}

internal suspend fun RemoteNavigationSession.focusPoster(
    pageId: String,
    row: PosterNavigationRow,
    index: Int,
    animated: Boolean = false,
    rightLookAhead: Boolean = false,
    anchorPrecedingPoster: Boolean = false,
): Boolean {
    explicitFocusTransfers++
    return try {
        val shelf = posterPages[pageId]?.shelves?.get(row.key)
        val followingIndex = (index + 1).takeIf { rightLookAhead && it in row.items.indices }
        if (shelf != null && !shelf.revealPosterEdge(index, animated, fast = { fastPosterScroll },
                followingIndex = followingIndex, anchorPrecedingPoster = anchorPrecedingPoster)) return false
        val target = withTimeoutOrNull(1500) {
            snapshotFlow { targets.firstOrNull { it.poster?.let { p -> p.page == pageId && p.row == row.key && p.index == index && p.role == PosterTargetRole.Poster } == true && it.bounds != null } }.first { it != null }
        }
        currentCoroutineContext().ensureActive()
        val focused = posterPages[pageId]?.active == true && target != null &&
            (focusedTarget === target || target.requester.requestFocus())
        withFrameNanos { }
        withFrameNanos { }
        focused
    } finally { explicitFocusTransfers-- }
}

/** Right navigation leaves room for the next poster without hiding the focused card. */
private suspend fun LazyListState.revealPosterEdge(
    index: Int,
    animated: Boolean,
    fast: () -> Boolean,
    followingIndex: Int?,
    anchorPrecedingPoster: Boolean = false,
): Boolean = withTimeoutOrNull(1500) {
    if (anchorPrecedingPoster) {
        if (animated) {
            animatePosterScroll(fast) { it.posterShelfAnchorDistance(index, precedingPosterVisible = true) }
        } else {
            while (true) {
                val delta = layoutInfo.posterShelfAnchorDistance(index, precedingPosterVisible = true)
                if (abs(delta) < 0.5f || abs(scrollBy(delta)) < 0.5f) break
                withFrameNanos { }
            }
        }
        return@withTimeoutOrNull layoutInfo.visibleItemsInfo.any { it.index == index }
    }
    if (animated) {
        animatePosterScroll(fast) { layout ->
            val start = layout.viewportStartOffset + layout.beforeContentPadding
            val end = layout.viewportEndOffset - layout.afterContentPadding
            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
            val nearest = item ?: layout.visibleItemsInfo.minByOrNull { abs(it.index - index) }
            if (nearest == null) 0f else {
                val offset = nearest.offset.toFloat() + (index - nearest.index).toFloat() * (nearest.size + layout.mainAxisItemSpacing)
                val revealFocused = when {
                    offset >= start && offset + nearest.size <= end -> 0f
                    offset <= start && offset + nearest.size >= end -> 0f
                    offset < start -> offset - start
                    else -> offset + nearest.size - end
                }
                if (followingIndex == null || nearest.size > end - start) revealFocused
                else {
                    val following = layout.visibleItemsInfo.firstOrNull { it.index == followingIndex }
                    val followingRight = following?.let { (it.offset + it.size).toFloat() }
                        ?: (offset + nearest.size + layout.mainAxisItemSpacing + nearest.size)
                    val revealFollowing = (followingRight - end).coerceAtLeast(0f)
                    // Narrow viewports prioritize the focused poster when both cannot fit.
                    val keepFocusedVisible = (offset - start).coerceAtLeast(0f)
                    maxOf(revealFocused, minOf(revealFollowing, keepFocusedVisible))
                }
            }
        }
        return@withTimeoutOrNull layoutInfo.visibleItemsInfo.any { it.index == index }
    }
    while (true) {
        val layout = layoutInfo
        val start = layout.viewportStartOffset + layout.beforeContentPadding
        val end = layout.viewportEndOffset - layout.afterContentPadding
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val delta = if (item != null) {
            when {
                item.offset >= start && item.offset + item.size <= end -> return@withTimeoutOrNull true
                item.offset <= start && item.offset + item.size >= end -> return@withTimeoutOrNull true
                item.offset < start -> (item.offset - start).toFloat()
                else -> (item.offset + item.size - end).toFloat()
            }
        } else {
            val nearest = layout.visibleItemsInfo.minByOrNull { abs(it.index - index) }
                ?: return@withTimeoutOrNull false
            val viewport = (end - start).coerceAtLeast(1).toFloat()
            when {
                index > nearest.index + 1 -> viewport
                index < nearest.index - 1 -> -viewport
                index > nearest.index -> (nearest.offset + nearest.size + layout.mainAxisItemSpacing - end + 1).toFloat().coerceIn(1f, viewport)
                else -> (nearest.offset - layout.mainAxisItemSpacing - start - 1).toFloat().coerceIn(-viewport, -1f)
            }
        }
        if (abs(scrollBy(delta)) < 0.5f) return@withTimeoutOrNull item != null
        withFrameNanos { }
    }
    @Suppress("UNREACHABLE_CODE")
    false
} ?: false

/** Re-measure the remaining distance each frame, so lazy measurement refines the same motion. */
private suspend fun LazyListState.animatePosterScroll(
    fast: () -> Boolean,
    onPositioned: () -> Unit = {},
    distance: (LazyListLayoutInfo) -> Float,
) {
    fun canMove(delta: Float) = (delta < -0.5f && canScrollBackward) || (delta > 0.5f && canScrollForward)
    onPositioned()
    if (!canMove(distance(layoutInfo))) return
    suspend fun move(durationMillis: Int, heldMillis: Int) {
        scroll {
            var lastFrame = withFrameNanos { it }
            var progress = 0f
            var previous = 0f
            while (progress < 1f) withFrameNanos { frame ->
                val duration = if (fast()) heldMillis else durationMillis
                progress = (progress + (frame - lastFrame).toFloat() / (duration * 1_000_000f)).coerceAtMost(1f)
                lastFrame = frame
                val eased = NuvioTokens.Motion.standard.transform(progress)
                val fraction = if (eased >= 1f) 1f else ((eased - previous) / (1f - previous)).coerceIn(0f, 1f)
                scrollBy(distance(layoutInfo) * fraction)
                onPositioned()
                previous = eased
            }
        }
    }
    // Repeats accelerate the motion already in flight as well as subsequent moves.
    move(NuvioTokens.Motion.fastMillis, NuvioTokens.Motion.fastMillis / 2)
    withFrameNanos { }
    // Usually just subpixel rounding; late lazy measurements get a short eased tail, never a snap.
    if (canMove(distance(layoutInfo))) move(NuvioTokens.Motion.fastMillis / 2, NuvioTokens.Motion.fastMillis / 4)
}

internal fun firstVisibleNavigationIndex(layout: androidx.compose.foundation.lazy.LazyListLayoutInfo): Int? =
    layout.visibleItemsInfo.firstOrNull { it.offset >= layout.viewportStartOffset && it.offset + it.size <= layout.viewportEndOffset }?.index
        ?: layout.visibleItemsInfo.maxByOrNull {
            (minOf(it.offset + it.size, layout.viewportEndOffset) - maxOf(it.offset, layout.viewportStartOffset)).coerceAtLeast(0)
        }?.index
