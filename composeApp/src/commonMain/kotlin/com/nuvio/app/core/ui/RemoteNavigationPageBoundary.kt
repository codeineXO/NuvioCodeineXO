package com.nuvio.app.core.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A details page has finite traversal, independent of the surrounding screen and Back control. */
internal class RemoteNavigationPageBoundary(
    val state: LazyListState,
    val onUpAtStart: suspend () -> Unit,
) {
    var viewportBounds: Rect? = null
    var moveJob: Job? = null
}

internal val LocalRemoteNavigationPageBoundary = staticCompositionLocalOf<RemoteNavigationPageBoundary?> { null }

@Composable
internal fun rememberRemoteNavigationPageBoundary(
    state: LazyListState,
    onUpAtStart: suspend () -> Unit = {},
): RemoteNavigationPageBoundary {
    val upAtStart by rememberUpdatedState(onUpAtStart)
    val boundary = remember(state) { RemoteNavigationPageBoundary(state) { upAtStart() } }
    DisposableEffect(boundary) {
        onDispose { boundary.moveJob?.cancel() }
    }
    return boundary
}

private fun RemoteNavigationSession.pageCandidates(source: RemoteFocusTarget): List<RemoteFocusTarget> =
    targets.filter {
        it !== source && it.route == source.route && it.pageBoundary === source.pageBoundary &&
            it.role != RemoteNavigationRole.Back && it.role != RemoteNavigationRole.Sidebar &&
            it.bounds?.let { bounds -> bounds.width > 0 && bounds.height > 0 } == true
    }

internal fun RemoteNavigationSession.movePageFocus(source: RemoteFocusTarget, direction: FocusDirection): Boolean {
    val origin = source.bounds ?: return false
    val boundary = source.pageBoundary ?: return false
    val viewport = boundary.viewportBounds
    val candidates = pageCandidates(source).filter { candidate ->
        val bounds = candidate.bounds!!
        val visible = viewport == null || (bounds.bottom > viewport.top && bounds.top < viewport.bottom)
        visible && !(candidate.role == RemoteNavigationRole.PageSwitch && boundary.state.canScrollForward) && when (direction) {
            FocusDirection.Up -> bounds.center.y < origin.center.y
            FocusDirection.Down -> bounds.center.y > origin.center.y
            FocusDirection.Left -> bounds.center.x < origin.center.x && bounds.bottom > origin.top && bounds.top < origin.bottom
            FocusDirection.Right -> bounds.center.x > origin.center.x && bounds.bottom > origin.top && bounds.top < origin.bottom
            else -> false
        }
    }
    return candidates.sortedBy {
        val bounds = it.bounds!!
        if (direction == FocusDirection.Up || direction == FocusDirection.Down) {
            abs(bounds.center.y - origin.center.y) * 1000f + abs(bounds.center.x - origin.center.x)
        } else abs(bounds.center.x - origin.center.x)
    }.any { it.requester.requestFocus() }
}

internal fun RemoteNavigationSession.movePageTabFocus(source: RemoteFocusTarget, backwards: Boolean): Boolean {
    val boundary = source.pageBoundary ?: return false
    val ordered = (pageCandidates(source) + source)
        .filter { it.role != RemoteNavigationRole.PageSwitch || !boundary.state.canScrollForward || it === source }
        .sortedWith(compareBy<RemoteFocusTarget> { it.role == RemoteNavigationRole.PageSwitch }
            .thenBy { it.bounds?.top ?: 0f }.thenBy { it.bounds?.left ?: 0f })
    val index = ordered.indexOf(source) + if (backwards) -1 else 1
    return ordered.getOrNull(index)?.requester?.requestFocus() == true
}

/** Use the host scope: scrolling a lazy page may dispose the control that requested the move. */
internal fun RemoteNavigationSession.revealPageFocus(source: RemoteFocusTarget, direction: FocusDirection) {
    val boundary = source.pageBoundary ?: return
    if (boundary.moveJob?.isActive == true) return
    boundary.moveJob = navigationScope?.launch {
        val state = boundary.state
        while (true) {
            val canScroll = if (direction == FocusDirection.Up) state.canScrollBackward else state.canScrollForward
            if (!canScroll) {
                if (direction == FocusDirection.Up) boundary.onUpAtStart()
                return@launch
            }
            val distance = (state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset) / 3f
            val consumed = state.animateScrollBy(if (direction == FocusDirection.Up) -distance else distance, tween(100))
            withFrameNanos { }
            if (moveFocus(source.route, direction, source.manager, sourceOverride = source)) return@launch
            if (consumed == 0f) return@launch
        }
    }
}
