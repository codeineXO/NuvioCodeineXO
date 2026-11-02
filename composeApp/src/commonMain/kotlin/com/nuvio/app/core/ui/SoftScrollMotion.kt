package com.nuvio.app.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.expm1

internal data class SoftScrollStep(val delta: Float, val velocity: Float)

/** Exact critically damped motion, independent of refresh rate and without overshoot at rest. */
internal fun softScrollStep(distance: Float, velocity: Float, seconds: Float, responseRate: Double = 20.0): SoftScrollStep {
    val elapsed = seconds.coerceIn(0f, 0.1f).toDouble()
    val omega = responseRate
    val phase = omega * elapsed
    val decay = exp(-phase)
    // Avoid subtracting nearly equal Float values for short frames and tiny distances.
    val response = -expm1(-phase) - phase * decay
    return SoftScrollStep(
        (distance * response + velocity * elapsed * decay).toFloat(),
        ((velocity * (1.0 - phase) + omega * distance * phase) * decay).toFloat(),
    )
}

internal fun dragScrollDirection(orientation: Orientation, reverseScrolling: Boolean, layoutDirection: LayoutDirection): Float =
    if (reverseScrolling xor (orientation == Orientation.Horizontal && layoutDirection == LayoutDirection.Rtl)) 1f else -1f

/** The same physical deltas reach parent chrome during wheel, drag, and drag-release motion. */
internal fun ScrollScope.scrollWithNestedParents(
    delta: Float,
    dispatcher: NestedScrollDispatcher?,
    orientation: Orientation = Orientation.Vertical,
    direction: Float = -1f,
): Float {
    if (dispatcher == null) return scrollBy(delta)
    fun offset(value: Float) = if (orientation == Orientation.Vertical) Offset(0f, value * direction) else Offset(value * direction, 0f)
    fun amount(value: Offset) = (if (orientation == Orientation.Vertical) value.y else value.x) * direction
    val input = offset(delta)
    val pre = dispatcher.dispatchPreScroll(input, NestedScrollSource.UserInput)
    val consumed = scrollBy(delta - amount(pre))
    val post = dispatcher.dispatchPostScroll(offset(consumed), input - pre - offset(consumed), NestedScrollSource.UserInput)
    return amount(pre) + consumed + amount(post)
}

/** One wheel animation per container; successive notches retarget the motion in flight. */
internal class SoftWheelScroll(
    private val state: ScrollableState,
    private val scope: CoroutineScope,
    private val nestedScrollDispatcher: NestedScrollDispatcher? = null,
    private val reverseScrolling: Boolean = false,
) {
    private class Target(var remaining: Float, var velocity: Float = 0f)
    private var job: Job? = null
    private var target: Target? = null

    fun cancel() {
        job?.cancel()
        target = null
    }

    fun scrollBy(delta: Float) {
        if (delta == 0f || !delta.isFinite()) return
        if ((delta > 0f && !state.canScrollForward) || (delta < 0f && !state.canScrollBackward)) return
        val current = target
        if (job?.isActive == true && current != null) {
            if (current.remaining * delta < 0f) {
                current.remaining = delta
                current.velocity = 0f
            } else {
                current.remaining += delta
            }
            return
        }
        val next = Target(delta)
        target = next
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // A new arrow/navigation action may interrupt the glide; direct dragging
                // retains the higher UserInput priority.
                state.scroll(MutatePriority.Default) {
                    val direction = if (reverseScrolling) 1f else -1f
                    var previous: Long? = null
                    while (true) {
                        val frame = withFrameNanos { it }
                        val seconds = previous?.let { (frame - it) / 1_000_000_000f } ?: (1f / 60)
                        val step = navigationScrollStep(next.remaining, next.velocity, seconds)
                        previous = frame
                        if (abs(next.remaining) < 0.5f && abs(next.velocity) < 5f) {
                            scrollWithNestedParents(next.remaining, nestedScrollDispatcher, direction = direction)
                            break
                        }
                        next.velocity = step.velocity
                        val consumed = scrollWithNestedParents(step.delta, nestedScrollDispatcher, direction = direction)
                        next.remaining -= consumed
                        if (abs(step.delta - consumed) > 0.5f) break
                    }
                }
            } finally {
                if (target === next) target = null
            }
        }
    }
}

internal fun softScrollAnimationSpec() =
    spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = NavigationMotionSettings.profile.scrollStiffness)

internal suspend fun ScrollableState.softScrollBy(distance: Float): Float =
    animateScrollBy(distance, softScrollAnimationSpec())

/** Refine the destination as lazy items are measured instead of snapping to an estimated offset. */
internal suspend fun LazyListState.softScrollToItem(index: Int, scrollOffset: Int = 0) {
    require(index >= 0) { "Index must be non-negative" }
    scroll {
        var previous = withFrameNanos { it }
        var velocity = 0f
        while (true) {
            val layout = layoutInfo
            val target = index.coerceAtMost(layout.totalItemsCount - 1)
            val nearest = layout.visibleItemsInfo.minByOrNull { abs(it.index - target) } ?: break
            val distance = nearest.offset.toFloat() +
                (target - nearest.index) * (nearest.size + layout.mainAxisItemSpacing).toFloat() + scrollOffset
            if (abs(distance) < 0.5f && abs(velocity) < 5f) {
                scrollBy(distance)
                break
            }
            if ((distance > 0f && !canScrollForward) || (distance < 0f && !canScrollBackward)) break
            val frame = withFrameNanos { it }
            val seconds = ((frame - previous) / 1_000_000_000f).coerceIn(0f, 0.1f)
            val step = navigationScrollStep(distance, velocity, seconds)
            previous = frame
            // An unmeasured target may be estimated from a much wider card or header.
            // Bound travel until measurement catches up, then avoid crossing the exact target.
            val maxSpeed = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1) * 4f
            velocity = step.velocity.coerceIn(-maxSpeed, maxSpeed)
            val bounded = step.delta.coerceIn(-maxSpeed * seconds, maxSpeed * seconds)
            val movement = if (nearest.index == target) {
                bounded.coerceIn(minOf(0f, distance), maxOf(0f, distance))
            } else bounded
            val consumed = scrollBy(movement)
            if (abs(movement - consumed) > 0.5f) break
        }
    }
}
