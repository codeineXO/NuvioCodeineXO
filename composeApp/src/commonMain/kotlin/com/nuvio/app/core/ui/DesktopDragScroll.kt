package com.nuvio.app.core.ui

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Velocity
import com.nuvio.app.isDesktop
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private class DesktopDragMotion {
    var job: Job? = null
}

/** Finish a claimed drag even without momentum so parent stretch/chrome can settle. */
internal suspend fun ScrollScope.flingWithNestedParents(
    velocity: Float,
    dispatcher: NestedScrollDispatcher,
    orientation: Orientation,
    direction: Float,
    fling: FlingBehavior,
    momentumEnabled: Boolean,
    settleWithoutMomentum: Boolean = false,
) {
    fun vector(value: Float) = when {
        value == 0f -> Velocity.Zero
        orientation == Orientation.Vertical -> Velocity(0f, value * direction)
        else -> Velocity(value * direction, 0f)
    }
    fun amount(value: Velocity) = (if (orientation == Orientation.Vertical) value.y else value.x) * direction

    val initial = if (momentumEnabled) velocity else 0f
    val preConsumed = dispatcher.dispatchPreFling(vector(initial))
    val available = initial - amount(preConsumed)
    var remaining = available
    var completed = false
    try {
        if ((momentumEnabled && available != 0f) || settleWithoutMomentum) {
            val direct = this
            val nested = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float = direct.scrollWithNestedParents(
                    pixels, dispatcher, orientation, direction, NestedScrollSource.SideEffect,
                )
            }
            remaining = with(fling) { nested.performFling(available) }
        }
        completed = true
    } finally {
        // Interrupted motion must settle parents without handing off stale velocity.
        dispatcher.dispatchPostFling(
            consumed = if (completed) vector(available - remaining) else Velocity.Zero,
            available = if (completed) vector(remaining) else Velocity.Zero,
        )
    }
}

/** Adds mouse-compatible dragging; native touch and child gestures keep first refusal. */
internal fun Modifier.nuvioDesktopDragScroll(
    state: ScrollableState,
    orientation: Orientation,
    enabled: Boolean = true,
    reverseDirection: Boolean = false,
    flingBehavior: FlingBehavior? = null,
    momentumEnabled: Boolean = true,
    settleWithoutMomentum: Boolean = false,
): Modifier {
    if (!isDesktop || !enabled) return this
    return composed {
        val active = LocalScreenActive.current
        val fling = flingBehavior ?: rememberNuvioFlingBehavior()
        if (!active) return@composed this
        val direction = dragScrollDirection(orientation, reverseDirection, LocalLayoutDirection.current)
        val dispatcher = remember { NestedScrollDispatcher() }
        val connection = remember { object : NestedScrollConnection {} }
        val motion = remember(state, orientation, reverseDirection, fling) { DesktopDragMotion() }
        // Wheel input interrupts a drag fling without consuming the wheel event.
        nestedScroll(connection, dispatcher).pointerInput(motion) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Scroll && event.changes.any { it.scrollDelta != Offset.Zero }) {
                        motion.job?.cancel()
                    }
                }
            }
        }.pointerInput(state, orientation, direction, fling, momentumEnabled, settleWithoutMomentum) {
            coroutineScope {
                val scope = this
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    motion.job?.cancel()
                    if (down.type != PointerType.Mouse) return@awaitEachGesture
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { state.stopScroll() }
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)
                    var total = Offset.Zero
                    var deltas: Channel<Unit>? = null
                    var pendingDelta = 0f
                    var released = false
                    var velocity = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // Match native touch tracking: preserve historical movement,
                            // ignore stationary up samples, and reset after a release pause.
                            tracker.addPointerInputChange(change)
                            if (!change.pressed) {
                                released = !change.isConsumed && event.changes.none { it.pressed }
                                if (released && deltas != null) {
                                    val lastDelta = change.position - change.previousPosition
                                    pendingDelta += direction * if (orientation == Orientation.Vertical) lastDelta.y else lastDelta.x
                                    deltas.trySend(Unit)
                                }
                                val measured = tracker.calculateVelocity()
                                val maximum = viewConfiguration.maximumFlingVelocity
                                velocity = (direction * if (orientation == Orientation.Vertical) measured.y else measured.x)
                                    .coerceIn(-maximum, maximum)
                                break
                            }
                            // A native scrollable, slider, selection, or reorder handler owns this gesture.
                            if (change.isConsumed || event.changes.count { it.pressed } != 1) break
                            val delta = change.position - change.previousPosition
                            total += delta
                            val primary = if (orientation == Orientation.Vertical) total.y else total.x
                            val cross = if (orientation == Orientation.Vertical) total.x else total.y
                            var amount = direction * if (orientation == Orientation.Vertical) delta.y else delta.x
                            if (deltas == null) {
                                if (abs(cross) > viewConfiguration.touchSlop && abs(cross) > abs(primary)) break
                                if (abs(primary) <= viewConfiguration.touchSlop || abs(primary) <= abs(cross)) continue
                                amount = direction * (primary - sign(primary) * viewConfiguration.touchSlop)
                                if ((amount > 0f && !state.canScrollForward) || (amount < 0f && !state.canScrollBackward)) break
                                val channel = Channel<Unit>(Channel.CONFLATED)
                                deltas = channel
                                motion.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    var releaseStarted = false
                                    try {
                                        state.scroll(MutatePriority.UserInput) {
                                            for (signal in channel) {
                                                val value = pendingDelta
                                                pendingDelta = 0f
                                                // These signals describe movement already included in value.
                                                while (channel.tryReceive().isSuccess) Unit
                                                if (value != 0f) scrollWithNestedParents(value, dispatcher, orientation, direction)
                                            }
                                        }
                                        if (released) {
                                            state.scroll {
                                                releaseStarted = true
                                                flingWithNestedParents(
                                                    velocity, dispatcher, orientation, direction, fling,
                                                    momentumEnabled && NavigationMotionSettings.profile.dragMomentum,
                                                    settleWithoutMomentum,
                                                )
                                            }
                                        }
                                    } finally {
                                        if (!releaseStarted) dispatcher.dispatchPostFling(Velocity.Zero, Velocity.Zero)
                                    }
                                }
                            }
                            if (motion.job?.isActive != true) break
                            pendingDelta += amount
                            deltas.trySend(Unit)
                            change.consume()
                        }
                    } finally {
                        deltas?.close()
                        if (!released) motion.job?.cancel()
                    }
                }
            }
        }
    }
}
