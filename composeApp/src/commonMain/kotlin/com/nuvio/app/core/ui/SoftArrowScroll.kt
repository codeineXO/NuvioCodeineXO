package com.nuvio.app.core.ui

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** A slightly longer response gives page-sized arrow moves a gradual, bounce-free brake. */
internal fun softArrowScrollStep(distance: Float, velocity: Float, seconds: Float): SoftScrollStep =
    softScrollStep(distance, velocity, seconds, responseRate = NavigationMotionSettings.profile.arrowResponseRate)

/** Both arrows retarget one critically damped glide, preserving velocity between clicks. */
internal class SoftArrowScroll(private val state: LazyListState, private val scope: CoroutineScope) {
    private class Target(
        var remaining: Float,
        var index: Int?,
        var offset: Int,
        var direction: Float,
        var velocity: Float = 0f,
    )

    private var target: Target? = null
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        target = null
    }

    fun scrollBy(distance: Float) {
        if (!distance.isFinite() || distance == 0f || !canMove(distance)) return
        val current = target?.takeIf { job?.isActive == true }
        val remaining = if (current != null && current.index == null && current.direction == sign(distance)) {
            current.remaining + distance
        } else distance
        retarget(remaining, null, 0, sign(distance))
    }

    /** Retain queued page clicks while lazy layout catches up; reversal starts at the visible page. */
    fun scrollPage(forward: Boolean, scrollOffset: Int = 0) {
        val direction = if (forward) 1f else -1f
        if (!canMove(direction)) return
        val layout = state.layoutInfo
        if (layout.totalItemsCount == 0 || layout.visibleItemsInfo.isEmpty()) return
        val current = target?.takeIf { job?.isActive == true && it.direction == direction }
        val start = current?.index ?: state.firstVisibleItemIndex
        val count = (layout.visibleItemsInfo.size - 1).coerceAtLeast(1)
        val index = (start.toLong() + if (forward) count else -count)
            .coerceIn(0L, (layout.totalItemsCount - 1).toLong()).toInt()
        retarget(0f, index, if (index == 0) 0 else scrollOffset, direction)
    }

    private fun canMove(distance: Float) =
        if (distance > 0f) state.canScrollForward else state.canScrollBackward

    private fun retarget(remaining: Float, index: Int?, offset: Int, direction: Float) {
        val current = target?.takeIf { job?.isActive == true }
        if (current != null) {
            if (current.direction != direction) current.velocity = 0f
            current.remaining = remaining
            current.index = index
            current.offset = offset
            current.direction = direction
            return
        }
        val next = Target(remaining, index, offset, direction)
        target = next
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                state.scroll(MutatePriority.Default) {
                    var previous: Long? = null
                    while (true) {
                        val frame = withFrameNanos { it }
                        val seconds = previous?.let { (frame - it) / 1_000_000_000f } ?: (1f / 60)
                        previous = frame
                        val layout = state.layoutInfo
                        val index = next.index?.coerceAtMost(layout.totalItemsCount - 1)
                        val nearest = if (index != null) {
                            layout.visibleItemsInfo.minByOrNull { abs(it.index - index) } ?: break
                        } else null
                        val distance = if (nearest != null && index != null) {
                            nearest.offset.toFloat() + (index - nearest.index) *
                                (nearest.size + layout.mainAxisItemSpacing).toFloat() + next.offset
                        } else next.remaining
                        if (abs(distance) < 0.5f && abs(next.velocity) < 5f) {
                            scrollBy(distance)
                            break
                        }
                        if (!canMove(distance)) break
                        val step = softArrowScrollStep(distance, next.velocity, seconds)
                        next.velocity = step.velocity
                        // Bound estimates from unmeasured variable-width cards until they appear.
                        val movement = if (nearest != null && nearest.index != index) {
                            val maxSpeed = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1) * 4f
                            next.velocity = next.velocity.coerceIn(-maxSpeed, maxSpeed)
                            step.delta.coerceIn(-maxSpeed * seconds.coerceIn(0f, 0.1f), maxSpeed * seconds.coerceIn(0f, 0.1f))
                        } else step.delta.coerceIn(minOf(0f, distance), maxOf(0f, distance))
                        val consumed = scrollBy(movement)
                        if (index == null) next.remaining -= consumed
                        if (abs(movement - consumed) > 0.5f) break
                    }
                }
            } finally {
                if (target === next) target = null
            }
        }
    }
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
