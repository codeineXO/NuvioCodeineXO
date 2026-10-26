package com.nuvio.app.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.flow.drop

internal enum class RemoteFocusRestoration { Restored, Pending, Unavailable }
internal enum class RemoteFocusTransferReason { Navigation, ViewportEntry }

internal class RemoteContentViewport(val route: Any, val manager: FocusManager, val state: LazyListState) {
    var bounds: Rect? = null
}

internal fun RemoteNavigationSession.manualScroll(route: Any) {
    keyboard = false
    focusTransferReason = RemoteFocusTransferReason.ViewportEntry
    pendingViewportRoutes += route
    resetHoldState()
    posterPages.values.filter { it.route == route && it.active }.forEach {
        it.cancelRestoration(discardBookmark = true)
        it.lane = null
    }
}

/** Input takes ownership immediately; a delayed return must never steal it back. */
internal fun RemoteNavigationSession.interruptPosterRestoration(route: Any, manager: FocusManager): Boolean {
    if (!posterRestorationPending(route) || modalDepth > 0 || RemoteTextEditing.isTextInputFocused) return false
    posterPages.values.filter { it.route == route && it.active }.forEach {
        it.cancelRestoration(discardBookmark = true)
        it.lane = null
    }
    pendingViewportRoutes += route
    keyboard = true
    if (!enterVisibleViewport(route, manager)) focusAvailableContent(route)
    return true
}

/** A successful request and an unavailable layout both consume the entry press. */
internal fun RemoteNavigationSession.enterVisibleViewport(route: Any, manager: FocusManager): Boolean {
    if (route !in pendingViewportRoutes || modalDepth > 0 || RemoteTextEditing.isTextInputFocused || posterRestorationPending(route)) return false
    val viewports = contentViewports.filter { it.route == route && it.manager === manager }
    if (viewports.isEmpty()) {
        pendingViewportRoutes -= route
        return false
    }
    focusTransferReason = RemoteFocusTransferReason.ViewportEntry
    fun visibleArea(target: RemoteFocusTarget): Float {
        val raw = target.bounds ?: return 0f
        val clipped = target.visibleBounds ?: return 0f
        val poster = target.poster
        if (poster != null) {
            val page = posterPages[poster.page] ?: return 0f
            if (!page.active || page.route != route) return 0f
            val row = page.rows.firstOrNull { it.key == poster.row } ?: return 0f
            if (page.state?.layoutInfo?.visibleItemsInfo?.none { it.index == row.lazyIndex } == true) return 0f
            if (poster.role == PosterTargetRole.Poster &&
                page.shelves[poster.row]?.layoutInfo?.visibleItemsInfo?.none { it.index == poster.index } == true) return 0f
        }
        return viewports.maxOf { viewport ->
            val viewportBounds = viewport.bounds ?: return@maxOf 0f
            var intersection = raw.intersect(clipped).intersect(viewportBounds)
            hostBounds?.let { intersection = intersection.intersect(it) }
            intersection.width.coerceAtLeast(0f) * intersection.height.coerceAtLeast(0f)
        }
    }
    fun fullyVisible(target: RemoteFocusTarget): Boolean {
        val bounds = target.bounds ?: return false
        return visibleArea(target) > 0 && visibleArea(target) >= (bounds.width - 1).coerceAtLeast(0f) * (bounds.height - 1).coerceAtLeast(0f)
    }
    val candidates = targets.filter { it.route == route && it.manager === manager &&
        it.role != RemoteNavigationRole.Sidebar && it.role != RemoteNavigationRole.Back && visibleArea(it) > 0 }
    val readingOrder = compareBy<RemoteFocusTarget> { it.bounds!!.top }.thenBy { it.bounds!!.left }
    val posters = candidates.filter { it.poster?.role == PosterTargetRole.Poster }
    val previous = focusedTarget?.takeIf { it in candidates && fullyVisible(it) }
    val ordered = listOfNotNull(previous) + posters.filter(::fullyVisible).sortedWith(readingOrder) +
        posters.sortedWith(compareByDescending<RemoteFocusTarget> { visibleArea(it) }.then(readingOrder)) +
        candidates.sortedWith(readingOrder) + targets.filter {
            it.route == route && it.manager === manager && it.sidebarEntry
        }
    if (ordered.distinct().any { it.requester.requestFocus() }) {
        pendingViewportRoutes -= route
        keyboard = true
    }
    return true
}

internal fun RemoteNavigationSession.focusAvailableContent(route: Any): Boolean =
    targets.filter { it.route == route && it.bounds != null }
        .sortedBy { when (it.role) {
            RemoteNavigationRole.Sidebar -> if (it.sidebarEntry) 2 else 4
            RemoteNavigationRole.Back -> 3
            else -> if (it.poster == null) 0 else 1
        } }.any { it.requester.requestFocus() }

/** Observe list movement as well as wheel events so scrollbar drags use the same entry policy. */
internal fun Modifier.remoteNavigationViewport(state: LazyListState): Modifier = composed {
    val session = LocalRemoteNavigation.current ?: return@composed this
    val active = LocalScreenActive.current
    val route = LocalRemoteRoute.current
    val manager = LocalFocusManager.current
    val viewport = remember(route, manager, state) { RemoteContentViewport(route, manager, state) }
    DisposableEffect(session, viewport, active) {
        if (active) session.contentViewports += viewport
        onDispose { session.contentViewports -= viewport }
    }
    LaunchedEffect(session, state, active, route) {
        if (active) snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .drop(1).collect {
                if (!session.keyboard && !session.explicitFocusTransfer && !session.posterRestorationPending(route)) {
                    session.manualScroll(route)
                }
            }
    }
    this.onGloballyPositioned { viewport.bounds = it.boundsInRoot() }
}

/** Suppress native focus reveal too, including partially visible entry targets. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RemoteNavigationRevealPolicy(content: @Composable () -> Unit) {
    val session = LocalRemoteNavigation.current
    val defaultReveal = LocalBringIntoViewSpec.current
    val reveal = remember(session, defaultReveal) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (session?.focusTransferReason == RemoteFocusTransferReason.ViewportEntry ||
                    session?.explicitFocusTransfer == true) 0f
                else defaultReveal.calculateScrollDistance(offset, size, containerSize)
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides reveal, content = content)
}
