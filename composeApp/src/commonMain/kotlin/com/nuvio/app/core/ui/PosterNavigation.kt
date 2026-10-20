package com.nuvio.app.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

internal enum class PosterTargetRole { Poster, Header, EndAction }
internal enum class PosterRowReveal { PreservePosition, Centered }
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
    var restoring = false
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
    DisposableEffect(session, row, state) {
        val page = session.posterPages.getOrPut(row.first) { PosterNavigationPage() }
        page.shelves[row.second] = state
        onDispose { if (page.shelves[row.second] === state) page.shelves.remove(row.second) }
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
            page.origin = null
            page.lane = null
            page.context = context
            session.rememberedFocus.remove(route)
            session.rememberedBounds.remove(route)
        }
        page.rows = rows
        page.state = state
        page.route = route
        page.active = active
        if (!active) session.cancelPosterNavigation(pageId)
    }
    DisposableEffect(page) { onDispose { page.active = false; session.cancelPosterNavigation(pageId) } }
    LaunchedEffect(active, context) {
        if (!active || page.origin == null) return@LaunchedEffect
        page.restoring = true
        try {
            withFrameNanos { }
            snapshotFlow { currentLoading }.first { !it }
            val origin = page.origin ?: return@LaunchedEffect
            if (origin.context != context) return@LaunchedEffect
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
                if (!session.focusPoster(pageId, row, index)) return@LaunchedEffect
            } else {
                // Empty pages retain a useful input, retry or content action.
                withFrameNanos { }
                session.targets.firstOrNull { it.route == route && it.role != RemoteNavigationRole.Sidebar && it.bounds != null }
                    ?.requester?.requestFocus()
            }
            page.origin = null
        } finally { page.restoring = false }
    }
}

internal fun RemoteNavigationSession.capturePosterOrigin(item: PosterNavigationItem?) {
    if (item == null || item.role != PosterTargetRole.Poster) return
    cancelPosterNavigation(item.page)
    val page = posterPages[item.page] ?: return
    val state = page.state ?: return
    val shelf = page.shelves[item.row]
    page.origin = PosterOrigin(page.context, item.row, item.content, item.index,
        page.rows.indexOfFirst { it.key == item.row }, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset,
        shelf?.firstVisibleItemIndex ?: 0, shelf?.firstVisibleItemScrollOffset ?: 0,
        page.rows.firstOrNull { it.key == item.row }?.items.orEmpty())
}

internal fun RemoteNavigationSession.posterRestorationPending(route: Any): Boolean =
    posterPages.values.any { it.route == route && it.active && (it.origin != null || it.restoring) }

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
            if (focusPoster(item.page, row, next, animated = true, rightLookAhead = direction == FocusDirection.Right) &&
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
    revealPosterRow(item.page, next, PosterRowReveal.Centered)
    val candidates = targets.filter { it.poster?.let { p -> p.page == item.page && p.row == next.key && p.role == PosterTargetRole.Poster } == true }
    val shelf = page.shelves[next.key]
    val visible = shelf?.layoutInfo?.let { layout ->
        layout.visibleItemsInfo.filter { it.offset >= layout.viewportStartOffset && it.offset + it.size <= layout.viewportEndOffset }
            .map { it.index }.toSet().ifEmpty { setOfNotNull(firstVisibleNavigationIndex(layout)) }
    }
    val destination = candidates.filter { visible == null || it.poster!!.index in visible }.ifEmpty { candidates }
        .minByOrNull { abs((it.bounds?.center?.x ?: Float.MAX_VALUE) - lane) }
    destination?.poster?.index?.let { focusPoster(item.page, next, it, animated = true) }
}

internal suspend fun RemoteNavigationSession.revealPosterRow(
    pageId: String,
    row: PosterNavigationRow,
    policy: PosterRowReveal = PosterRowReveal.PreservePosition,
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
        state.animatePosterScroll(fast = { fastPosterScroll }) { layout ->
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
): Boolean {
    explicitFocusTransfers++
    return try {
        val shelf = posterPages[pageId]?.shelves?.get(row.key)
        val followingIndex = (index + 1).takeIf { rightLookAhead && it in row.items.indices }
        if (shelf != null && !shelf.revealPosterEdge(index, animated, fast = { fastPosterScroll }, followingIndex = followingIndex)) return false
        val target = withTimeoutOrNull(1500) {
            snapshotFlow { targets.firstOrNull { it.poster?.let { p -> p.page == pageId && p.row == row.key && p.index == index && p.role == PosterTargetRole.Poster } == true && it.bounds != null } }.first { it != null }
        }
        withFrameNanos { }
        val focused = target?.requester?.requestFocus() == true
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
): Boolean = withTimeoutOrNull(1500) {
    if (animated) {
        animatePosterScroll(fast) { layout ->
            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
            val nearest = item ?: layout.visibleItemsInfo.minByOrNull { abs(it.index - index) }
            if (nearest == null) 0f else {
                val offset = nearest.offset.toFloat() + (index - nearest.index).toFloat() * (nearest.size + layout.mainAxisItemSpacing)
                val revealFocused = when {
                    offset >= layout.viewportStartOffset && offset + nearest.size <= layout.viewportEndOffset -> 0f
                    offset <= layout.viewportStartOffset && offset + nearest.size >= layout.viewportEndOffset -> 0f
                    offset < layout.viewportStartOffset -> offset - layout.viewportStartOffset
                    else -> offset + nearest.size - layout.viewportEndOffset
                }
                if (followingIndex == null || nearest.size > layout.viewportEndOffset - layout.viewportStartOffset) revealFocused
                else {
                    val following = layout.visibleItemsInfo.firstOrNull { it.index == followingIndex }
                    val followingRight = following?.let { (it.offset + it.size).toFloat() }
                        ?: (offset + nearest.size + layout.mainAxisItemSpacing + nearest.size)
                    val revealFollowing = (followingRight - layout.viewportEndOffset).coerceAtLeast(0f)
                    // Narrow viewports prioritize the focused poster when both cannot fit.
                    val keepFocusedVisible = (offset - layout.viewportStartOffset).coerceAtLeast(0f)
                    maxOf(revealFocused, minOf(revealFollowing, keepFocusedVisible))
                }
            }
        }
        return@withTimeoutOrNull layoutInfo.visibleItemsInfo.any { it.index == index }
    }
    while (true) {
        val layout = layoutInfo
        val start = layout.viewportStartOffset
        val end = layout.viewportEndOffset
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
private suspend fun LazyListState.animatePosterScroll(fast: () -> Boolean, distance: (LazyListLayoutInfo) -> Float) {
    fun canMove(delta: Float) = (delta < -0.5f && canScrollBackward) || (delta > 0.5f && canScrollForward)
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
                previous = eased
            }
        }
    }
    // Repeats accelerate the motion already in flight as well as subsequent moves.
    move(NuvioTokens.Motion.normalMillis, NuvioTokens.Motion.fastMillis / 2)
    withFrameNanos { }
    // Usually just subpixel rounding; late lazy measurements get a short eased tail, never a snap.
    if (canMove(distance(layoutInfo))) move(NuvioTokens.Motion.fastMillis / 2, NuvioTokens.Motion.fastMillis / 4)
}

internal fun firstVisibleNavigationIndex(layout: androidx.compose.foundation.lazy.LazyListLayoutInfo): Int? =
    layout.visibleItemsInfo.firstOrNull { it.offset >= layout.viewportStartOffset && it.offset + it.size <= layout.viewportEndOffset }?.index
        ?: layout.visibleItemsInfo.maxByOrNull {
            (minOf(it.offset + it.size, layout.viewportEndOffset) - maxOf(it.offset, layout.viewportStartOffset)).coerceAtLeast(0)
        }?.index
