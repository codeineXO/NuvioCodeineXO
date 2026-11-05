package com.nuvio.app.core.ui

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal data class ArrowPageItem(val index: Int, val offset: Float, val size: Float)
internal data class ArrowPageBounds(val start: Float, val end: Float) {
    val width get() = end - start
}
internal data class ArrowPageTarget(val index: Int, val alignEnd: Boolean, val edgeIndex: Int)

/** Intersections include clipped artwork, but exclude items only touching an edge. */
internal fun arrowPageTarget(
    items: List<ArrowPageItem>, bounds: ArrowPageBounds, totalItems: Int, forward: Boolean, spacing: Int,
): ArrowPageTarget? {
    if (bounds.width <= 0f || items.isEmpty()) return null
    val visible = items.filter { it.offset < bounds.end && it.offset + it.size > bounds.start }
    if (visible.size == totalItems && visible.all { it.offset >= bounds.start && it.offset + it.size <= bounds.end }) return null
    val edge = (if (forward) visible.maxByOrNull { it.index } else visible.minByOrNull { it.index }) ?: return null
    val neighbour = items.firstOrNull { it.index == edge.index + if (forward) -1 else 1 }
    fun distance(item: ArrowPageItem) = if (forward) item.offset - bounds.start
        else item.offset + item.size - bounds.end
    fun advances(distance: Float) = if (forward) distance > 0.5f else distance < -0.5f
    val pairWidth = neighbour?.let {
        if (forward) edge.offset + edge.size - it.offset else it.offset + it.size - edge.offset
    }
    if (neighbour != null && pairWidth != null && pairWidth <= bounds.width && advances(distance(neighbour))) {
        return ArrowPageTarget(neighbour.index, !forward, edge.index)
    }
    if (advances(distance(edge))) return ArrowPageTarget(edge.index, !forward, edge.index)
    val nextIndex = edge.index + if (forward) 1 else -1
    if (nextIndex !in 0 until totalItems) return null
    val next = items.firstOrNull { it.index == nextIndex } ?: ArrowPageItem(
        nextIndex,
        if (forward) edge.offset + edge.size + spacing else edge.offset - edge.size - spacing,
        edge.size,
    )
    return if (advances(distance(next))) ArrowPageTarget(nextIndex, !forward, nextIndex) else null
}

internal fun arrowPageDistance(
    target: ArrowPageTarget, items: List<ArrowPageItem>, bounds: ArrowPageBounds, spacing: Int,
): Float {
    fun item(index: Int): ArrowPageItem {
        val nearest = items.minByOrNull { abs(it.index - index) }!!
        return items.firstOrNull { it.index == index } ?: ArrowPageItem(
            index, nearest.offset + (index - nearest.index) * (nearest.size + spacing), nearest.size,
        )
    }
    val anchor = item(target.index)
    val edge = item(target.edgeIndex)
    // Resizing may make the original pair too wide; prioritize the edge item.
    val pairWidth = if (target.alignEnd) anchor.offset + anchor.size - edge.offset
        else edge.offset + edge.size - anchor.offset
    val aligned = if (pairWidth > bounds.width) edge else anchor
    return if (target.alignEnd) aligned.offset + aligned.size - bounds.end else aligned.offset - bounds.start
}

/** Extend an active glide immediately, resolving the next page at its intended destination. */
internal fun nextArrowPageTarget(
    target: ArrowPageTarget, items: List<ArrowPageItem>, bounds: ArrowPageBounds,
    totalItems: Int, forward: Boolean, spacing: Int,
): ArrowPageTarget? {
    if (items.isEmpty() || bounds.width <= 0f) return null
    val distance = arrowPageDistance(target, items, bounds, spacing)
    val projected = items.sortedBy { it.index }.map { it.copy(offset = it.offset - distance) }.toMutableList()
    // LazyRow has not measured the future page yet. Estimate only its missing edge items;
    // the animation replaces these estimates with measured geometry as they appear.
    while (projected.first().index > 0 && projected.first().offset > bounds.start) {
        val first = projected.first()
        projected.add(0, ArrowPageItem(first.index - 1, first.offset - first.size - spacing, first.size))
    }
    while (projected.last().index < totalItems - 1 && projected.last().offset + projected.last().size < bounds.end) {
        val last = projected.last()
        projected.add(ArrowPageItem(last.index + 1, last.offset + last.size + spacing, last.size))
    }
    return arrowPageTarget(projected, bounds, totalItems, forward, spacing)
}

internal fun softArrowScrollStep(distance: Float, velocity: Float, seconds: Float): SoftScrollStep =
    softScrollStep(distance, velocity, seconds, responseRate = NavigationMotionSettings.profile.arrowResponseRate)

/** Repeated clicks extend one active glide immediately, preserving its velocity. */
internal class SoftArrowScroll(private val state: LazyListState, private val scope: CoroutineScope) {
    private class Request(var forward: Boolean, var target: ArrowPageTarget, var velocity: Float = 0f)
    private var request: Request? = null
    private var job: Job? = null
    private var startInset = 0f
    private var endInset = 0f

    fun updateViewportInsets(start: Float, end: Float) {
        startInset = start
        endInset = end
    }

    private fun bounds(layout: LazyListLayoutInfo) = ArrowPageBounds(
        layout.viewportStartOffset + startInset, layout.viewportEndOffset - endInset,
    )

    private fun items(layout: LazyListLayoutInfo) = layout.visibleItemsInfo.map {
        ArrowPageItem(it.index, it.offset.toFloat(), it.size.toFloat())
    }

    private fun page(forward: Boolean): ArrowPageTarget? {
        val layout = state.layoutInfo
        return arrowPageTarget(items(layout), bounds(layout), layout.totalItemsCount, forward, layout.mainAxisItemSpacing)
    }

    fun cancel() {
        job?.cancel()
        request = null
    }

    private fun canMove(forward: Boolean) = if (forward) state.canScrollForward else state.canScrollBackward

    fun scrollPage(forward: Boolean) {
        val current = request?.takeIf { job?.isActive == true }
        if (current != null && current.forward == forward) {
            val layout = state.layoutInfo
            nextArrowPageTarget(
                current.target, items(layout), bounds(layout), layout.totalItemsCount, forward, layout.mainAxisItemSpacing,
            )?.let { current.target = it }
            return
        }
        // Reversal starts at the visible page, discarding the old destination.
        if (!canMove(forward)) {
            if (current != null) cancel()
            return
        }
        val destination = page(forward) ?: run {
            if (current != null) cancel()
            return
        }
        if (current != null) {
            current.forward = forward
            current.target = destination
            current.velocity = 0f
            return
        }
        val next = Request(forward, destination)
        request = next
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // Manual wheel/drag mutations interrupt this owner and discard its destination.
                state.scroll(MutatePriority.Default) {
                    var previous: Long? = null
                    while (true) {
                        val frame = withFrameNanos { it }
                        val seconds = previous?.let { (frame - it) / 1_000_000_000f } ?: (1f / 60)
                        previous = frame
                        val layout = state.layoutInfo
                        val measured = items(layout)
                        if (measured.isEmpty() || next.target.index >= layout.totalItemsCount) break
                        val distance = arrowPageDistance(next.target, measured, bounds(layout), layout.mainAxisItemSpacing)
                        if (abs(distance) <= 0.5f && abs(next.velocity) < 5f) {
                            scrollBy(distance)
                            break
                        }
                        if (!canMove(distance > 0f)) break
                        val step = softArrowScrollStep(distance, next.velocity, seconds)
                        next.velocity = step.velocity
                        val movement = step.delta.coerceIn(minOf(0f, distance), maxOf(0f, distance))
                        val consumed = scrollBy(movement)
                        if (abs(movement - consumed) > 0.5f) break
                    }
                }
            } finally {
                if (request === next) request = null
            }
        }
    }
}

/** Physical insets inside the measured LazyRow, including any overdraw and bleed. */
@Composable
internal fun BindArrowScrollViewport(scroll: SoftArrowScroll, start: Dp, end: Dp = start) {
    val density = LocalDensity.current
    val startPx = with(density) { start.toPx() }
    val endPx = with(density) { end.toPx() }
    SideEffect { scroll.updateViewportInsets(startPx, endPx) }
}

@Composable
internal fun rememberSoftArrowScroll(state: LazyListState): SoftArrowScroll {
    val scope = rememberCoroutineScope()
    val scroll = remember(state, scope) { SoftArrowScroll(state, scope) }
    val active = LocalScreenActive.current
    DisposableEffect(scroll, active) {
        if (!active) scroll.cancel()
        onDispose { scroll.cancel() }
    }
    return scroll
}
