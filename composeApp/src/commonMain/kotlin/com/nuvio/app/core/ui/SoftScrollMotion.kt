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
import androidx.compose.ui.unit.Velocity
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

/**
 * Frame delta in seconds. Desktop frame timestamps jitter around vsync; feeding raw deltas makes
 * per-frame steps uneven, so they are averaged while real stalls are followed at once.
 */
internal class SmoothedFrameClock {
    private var previous: Long? = null
    // Start from the last measured period so a new motion's first frame matches the display.
    private var frameSeconds = lastFrameSeconds

    /** Smoothed seconds since the previous tick; the first tick returns one frame period. */
    fun tick(frameNanos: Long): Float {
        val raw = previous?.let { (frameNanos - it) / 1_000_000_000f } ?: frameSeconds
        previous = frameNanos
        // A stall is followed at once but kept out of the average, so later frames stay steady.
        if (raw > frameSeconds * 1.8f) return raw.coerceAtMost(0.1f)
        frameSeconds += (raw.coerceAtLeast(0f) - frameSeconds) * 0.15f
        lastFrameSeconds = frameSeconds
        return frameSeconds
    }

    val periodNanos: Long get() = (frameSeconds * 1_000_000_000f).toLong()

    private companion object {
        var lastFrameSeconds = 1f / 60
    }
}

/**
 * Frame times locked to the measured frame period. Desktop frame callbacks run a few ms early or
 * late around vsync; motion sampled at these steadied times advances evenly on every frame.
 */
internal class SteadyFrameTimeline {
    private val clock = SmoothedFrameClock()
    private var time = 0L
    private var primed = false

    val periodNanos: Long get() = clock.periodNanos

    fun advance(nowNanos: Long): Long {
        val seconds = clock.tick(nowNanos)
        if (!primed) {
            primed = true
            time = nowNanos
            return time
        }
        val predicted = time + (seconds * 1_000_000_000f).toLong()
        val error = nowNanos - predicted
        time = if (abs(error) > SteadyFrameResyncNanos) nowNanos else predicted + error / 8
        return time
    }
}

private const val SteadyFrameResyncNanos = 20_000_000L

/**
 * Fixed ease-in-out glide toward a re-measured target. Each frame moves its share of what is
 * left, so late layout measurement is spread over the rest of the glide instead of a jump.
 */
internal class EasedGlide {
    /** Current speed in px/s; carried into the next segment while heading the same way. */
    var velocity = 0f
    private var segmentDistance = 0f
    private var segmentVelocity = 0f
    private var duration = 0f
    private var elapsed = 0f
    private var lastProgress = 0f
    val finished get() = elapsed >= duration

    fun restart(distance: Float, seconds: Float) {
        segmentVelocity = if (velocity * distance > 0f) velocity else 0f
        segmentDistance = distance
        duration = seconds
        elapsed = 0f
        lastProgress = 0f
    }

    /** Movement for this frame, given the [distance] still left to the target. */
    fun step(distance: Float, seconds: Float): Float {
        elapsed += seconds
        if (elapsed >= duration) {
            velocity = 0f
            return distance
        }
        val (position, speed) = arrowGlideAt(segmentDistance, segmentVelocity, duration, elapsed)
        velocity = speed
        val progress = if (abs(segmentDistance) > 0.5f) position / segmentDistance else 1f
        val share = if (lastProgress < 1f) ((progress - lastProgress) / (1f - lastProgress)).coerceIn(0f, 1f) else 1f
        lastProgress = progress
        return distance * share
    }
}

internal fun dragScrollDirection(orientation: Orientation, reverseScrolling: Boolean, layoutDirection: LayoutDirection): Float =
    if (reverseScrolling xor (orientation == Orientation.Horizontal && layoutDirection == LayoutDirection.Rtl)) 1f else -1f

/** The same physical deltas reach parent chrome during wheel, drag, and drag-release motion. */
internal fun ScrollScope.scrollWithNestedParents(
    delta: Float,
    dispatcher: NestedScrollDispatcher?,
    orientation: Orientation = Orientation.Vertical,
    direction: Float = -1f,
    source: NestedScrollSource = NestedScrollSource.UserInput,
): Float {
    if (dispatcher == null) return scrollBy(delta)
    fun offset(value: Float) = if (orientation == Orientation.Vertical) Offset(0f, value * direction) else Offset(value * direction, 0f)
    fun amount(value: Offset) = (if (orientation == Orientation.Vertical) value.y else value.x) * direction
    val input = offset(delta)
    val pre = dispatcher.dispatchPreScroll(input, source)
    val consumed = scrollBy(delta - amount(pre))
    val post = dispatcher.dispatchPostScroll(offset(consumed), input - pre - offset(consumed), source)
    return amount(pre) + consumed + amount(post)
}

/** One wheel animation per container; successive notches retarget the motion in flight. */
internal class SoftWheelScroll(
    private val state: ScrollableState,
    private val scope: CoroutineScope,
    private val nestedScrollDispatcher: NestedScrollDispatcher? = null,
    private val reverseScrolling: Boolean = false,
    private val rowAnchors: (() -> List<Int>)? = null,
    private val canOverscroll: (Float) -> Boolean = { false },
) {
    private class Target(
        var remaining: Float,
        var velocity: Float = 0f,
        var revealPreviousRow: Boolean = false,
        /** Snap mode: the next frame starts a fresh eased segment toward [remaining]. */
        var restart: Boolean = true,
    )
    private var job: Job? = null
    private var target: Target? = null

    fun cancel() {
        job?.cancel()
    }

    fun canScrollBy(delta: Float): Boolean = delta.isFinite() && delta != 0f &&
        (if (delta > 0f) state.canScrollForward || canOverscroll(delta)
        else state.canScrollBackward || canOverscroll(delta))

    fun scrollBy(delta: Float) {
        if (!canScrollBy(delta)) return
        val current = target
        if (job?.isActive == true && current != null) {
            if (rowAnchors != null) {
                if (current.remaining * delta < 0f) {
                    // Reversal: a fresh eased glide to the nearest row the other way.
                    val rowOffset = verticalRowSnapOffset(rowAnchors(), delta)
                    current.remaining = rowOffset ?: delta
                    current.revealPreviousRow = rowOffset == null && delta < 0f
                    current.velocity = 0f
                    current.restart = true
                }
                // Same-direction notches during a row glide are ignored on purpose: one glide
                // lands on exactly one row, so a fast spin can never skip a row.
                return
            }
            if (current.remaining * delta < 0f) {
                // Brake rather than stop dead: a little carried speed reads as a soft turn.
                current.remaining = delta
                current.velocity *= 0.25f
            } else {
                current.remaining += delta
            }
            return
        }
        val rowOffset = rowAnchors?.invoke()?.let { verticalRowSnapOffset(it, delta) }
        val next = Target(rowOffset ?: delta, revealPreviousRow = rowAnchors != null && rowOffset == null && delta < 0f)
        target = next
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // A new arrow/navigation action may interrupt the glide; direct dragging
                // retains the higher UserInput priority.
                state.scroll(MutatePriority.Default) {
                    val direction = if (reverseScrolling) 1f else -1f
                    val clock = SmoothedFrameClock()
                    val glide = EasedGlide()
                    while (true) {
                        val seconds = clock.tick(withFrameNanos { it })
                        if (next.revealPreviousRow) {
                            val offset = verticalRowSnapOffset(rowAnchors?.invoke().orEmpty(), -1f)
                            if (offset != null) {
                                next.remaining = offset
                                next.revealPreviousRow = false
                                next.restart = true
                            }
                        }
                        if (rowAnchors != null && !next.revealPreviousRow) {
                            // Rows that change height mid-glide move the landing edge with them.
                            // Skipped while parent chrome (hero stretch) absorbs the motion,
                            // since the rows themselves are not moving then.
                            if (!canOverscroll(next.remaining)) rowAnchors().minByOrNull { abs(it - next.remaining) }
                                ?.takeIf { abs(it - next.remaining) <= 48f && it * next.remaining > 0f }
                                ?.let { next.remaining = it.toFloat() }
                            if (abs(next.remaining) < 0.5f) {
                                scrollWithNestedParents(next.remaining, nestedScrollDispatcher, direction = direction)
                                break
                            }
                            if (next.restart) {
                                next.restart = false
                                glide.velocity = next.velocity
                                glide.restart(next.remaining, NavigationMotionSettings.profile.snapGlideSeconds)
                            }
                            val movement = glide.step(next.remaining, seconds)
                            next.velocity = glide.velocity
                            val consumed = scrollWithNestedParents(movement, nestedScrollDispatcher, direction = direction)
                            next.remaining -= consumed
                            if (glide.finished || abs(movement - consumed) > 0.5f) break
                            continue
                        }
                        if (abs(next.remaining) < 0.5f && abs(next.velocity) < 5f) {
                            scrollWithNestedParents(next.remaining, nestedScrollDispatcher, direction = direction)
                            break
                        }
                        val step = navigationScrollStep(next.remaining, next.velocity, seconds)
                        next.velocity = step.velocity
                        val consumed = scrollWithNestedParents(step.delta, nestedScrollDispatcher, direction = direction)
                        next.remaining -= consumed
                        if (abs(step.delta - consumed) > 0.5f) break
                    }
                }
            } finally {
                if (target === next) {
                    target = null
                    // Wheel bursts have no pointer-up event. Complete their lifecycle
                    // without adding a fling, including interruption and cancellation.
                    nestedScrollDispatcher?.dispatchPreFling(Velocity.Zero)
                    nestedScrollDispatcher?.dispatchPostFling(Velocity.Zero, Velocity.Zero)
                }
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
