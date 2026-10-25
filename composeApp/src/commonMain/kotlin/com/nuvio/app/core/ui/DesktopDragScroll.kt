package com.nuvio.app.core.ui

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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

/** Adds mouse-compatible dragging; native touch and child gestures keep first refusal. */
internal fun Modifier.nuvioDesktopDragScroll(
    state: ScrollableState,
    orientation: Orientation,
    enabled: Boolean = true,
    reverseDirection: Boolean = false,
    flingBehavior: FlingBehavior? = null,
): Modifier {
    if (!isDesktop || !enabled) return this
    return composed {
        val active = LocalScreenActive.current
        val fling = flingBehavior ?: ScrollableDefaults.flingBehavior()
        if (!active) return@composed this
        val motion = remember(state, orientation, reverseDirection, fling) { DesktopDragMotion() }
        // Some app wheel handlers use dispatchRawDelta, bypassing ScrollableState's mutex.
        // Observe the wheel without consuming it so those handlers also interrupt our fling.
        pointerInput(motion) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Scroll && event.changes.any { it.scrollDelta != Offset.Zero }) {
                        motion.job?.cancel()
                    }
                }
            }
        }.pointerInput(state, orientation, reverseDirection, fling) {
            coroutineScope {
                val scope = this
                val direction = if (reverseDirection) 1f else -1f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                    motion.job?.cancel()
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { state.stopScroll() }
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var total = Offset.Zero
                    var deltas: Channel<Float>? = null
                    var released = false
                    var velocity = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) {
                                released = !change.isConsumed && event.changes.none { it.pressed }
                                val measured = tracker.calculateVelocity()
                                velocity = direction * if (orientation == Orientation.Vertical) measured.y else measured.x
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
                                val channel = Channel<Float>(Channel.UNLIMITED)
                                deltas = channel
                                motion.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    state.scroll(MutatePriority.UserInput) {
                                        for (value in channel) scrollBy(value)
                                    }
                                    if (released) {
                                        state.scroll {
                                            with(fling) { performFling(velocity) }
                                        }
                                    }
                                }
                            }
                            if (motion.job?.isActive != true) break
                            deltas.trySend(amount)
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
