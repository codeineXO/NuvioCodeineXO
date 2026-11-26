package com.nuvio.app.core.ui

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.time.TimeSource

private class DesktopDragMotion {
    var job: Job? = null
    var session: Any? = null
    val carry = DesktopSubPixelCarry()
}

/**
 * Lazy lists and scroll state lay content out on whole pixels. Below ~1 px per frame a fling
 * would step 1, 0, 1, 0 px; instead only whole pixels are scrolled and the fraction still owed
 * is drawn as a draw-time offset, so slow motion glides evenly.
 */
internal class DesktopSubPixelCarry {
    var pending by mutableFloatStateOf(0f)
}

private fun ScrollScope.scrollWholePixels(
    delta: Float,
    carry: DesktopSubPixelCarry,
    dispatcher: NestedScrollDispatcher,
    orientation: Orientation,
    direction: Float,
    source: NestedScrollSource = NestedScrollSource.UserInput,
): Float {
    val total = carry.pending + delta
    val whole = total.roundToInt().toFloat()
    if (whole == 0f) {
        carry.pending = total
        return delta
    }
    val consumed = scrollWithNestedParents(whole, dispatcher, orientation, direction, source)
    if (abs(consumed - whole) > 0.01f) {
        // Hit an edge: nothing further is owed.
        carry.pending = 0f
        return consumed
    }
    carry.pending = total - whole
    return delta
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
    carry: DesktopSubPixelCarry? = null,
    lead: DesktopDragLead? = null,
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
    val direct = this
    val nested = object : ScrollScope {
        override fun scrollBy(pixels: Float): Float = if (carry != null) {
            direct.scrollWholePixels(pixels, carry, dispatcher, orientation, direction, NestedScrollSource.SideEffect)
        } else {
            direct.scrollWithNestedParents(pixels, dispatcher, orientation, direction, NestedScrollSource.SideEffect)
        }
    }
    try {
        if ((momentumEnabled && available != 0f) || settleWithoutMomentum) {
            remaining = if (lead != null && fling is DesktopLeadFling) {
                with(fling) { nested.performFling(available, lead) }
            } else {
                with(fling) { nested.performFling(available) }
            }
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

private val DesktopDragScrollTouchSlop = 8.dp
private val DesktopDragScrollMinFlingVelocity = 50.dp
private val DesktopDragScrollMaxFlingVelocity = 8000.dp

private const val DesktopDragStopThresholdMs = 40L
private const val DesktopDragVelocityWindowMs = 100L
private const val DesktopDragDirectionJitterPx = 4f
private const val DesktopDragSampleCapacity = 32

private class DesktopDragVelocityEstimator(
    private val horizontal: Boolean,
) {
    private val velocityTracker = VelocityTracker()
    private val sampleStartTimes = LongArray(DesktopDragSampleCapacity)
    private val sampleEndTimes = LongArray(DesktopDragSampleCapacity)
    private val sampleAxisDeltas = FloatArray(DesktopDragSampleCapacity)
    private var sampleHead = 0
    private var sampleCount = 0
    private var lastEventTimeMs = 0L
    private var cumulativeAxisPos = 0f
    private var lastSignificantSign = 0f
    private var pendingOppositeDelta = 0f

    private fun toOffset(axisPos: Float): Offset =
        if (horizontal) Offset(axisPos, 0f) else Offset(0f, axisPos)

    private fun Velocity.toAxis(): Float = if (horizontal) x else y

    private fun clearSamples() {
        sampleHead = 0
        sampleCount = 0
    }

    private fun pushSample(startTimeMs: Long, endTimeMs: Long, axisDelta: Float) {
        if (sampleCount > 0) {
            val lastIdx = (sampleHead + sampleCount - 1) % DesktopDragSampleCapacity
            if (sampleEndTimes[lastIdx] == endTimeMs) {
                sampleAxisDeltas[lastIdx] += axisDelta
                return
            }
        }
        if (endTimeMs <= startTimeMs) return
        val insertIdx = if (sampleCount < DesktopDragSampleCapacity) {
            (sampleHead + sampleCount).also { sampleCount++ } % DesktopDragSampleCapacity
        } else {
            sampleHead.also { sampleHead = (sampleHead + 1) % DesktopDragSampleCapacity }
        }
        sampleStartTimes[insertIdx] = startTimeMs
        sampleEndTimes[insertIdx] = endTimeMs
        sampleAxisDeltas[insertIdx] = axisDelta
    }

    fun start(timeMs: Long) {
        velocityTracker.resetTracking()
        clearSamples()
        lastEventTimeMs = timeMs
        cumulativeAxisPos = 0f
        lastSignificantSign = 0f
        pendingOppositeDelta = 0f
        velocityTracker.addPosition(timeMs, Offset.Zero)
    }

    fun addDelta(timeMs: Long, axisDelta: Float) {
        if (axisDelta == 0f) return
        val dt = timeMs - lastEventTimeMs
        if (dt < 0L) return
        if (dt >= DesktopDragStopThresholdMs) {
            start(timeMs)
            lastSignificantSign = sign(axisDelta)
            cumulativeAxisPos = axisDelta
            velocityTracker.addPosition(timeMs, toOffset(cumulativeAxisPos))
            return
        }

        val deltaSign = sign(axisDelta)
        val acceptedDelta: Float
        if (lastSignificantSign != 0f && deltaSign != lastSignificantSign) {
            pendingOppositeDelta += axisDelta
            if (abs(pendingOppositeDelta) < DesktopDragDirectionJitterPx) {
                return
            }
            acceptedDelta = pendingOppositeDelta
            pendingOppositeDelta = 0f
            val prevTimeMs = lastEventTimeMs
            velocityTracker.resetTracking()
            clearSamples()
            lastSignificantSign = sign(acceptedDelta)
            velocityTracker.addPosition(prevTimeMs, Offset.Zero)
            cumulativeAxisPos = acceptedDelta
            velocityTracker.addPosition(timeMs, toOffset(cumulativeAxisPos))
            pushSample(prevTimeMs, timeMs, acceptedDelta)
            lastEventTimeMs = timeMs
            return
        } else {
            pendingOppositeDelta = 0f
            acceptedDelta = axisDelta
            if (abs(acceptedDelta) >= DesktopDragDirectionJitterPx || lastSignificantSign == 0f) {
                lastSignificantSign = deltaSign
            }
        }

        val prevTimeMs = lastEventTimeMs
        cumulativeAxisPos += acceptedDelta
        velocityTracker.addPosition(timeMs, toOffset(cumulativeAxisPos))
        pushSample(prevTimeMs, timeMs, acceptedDelta)
        lastEventTimeMs = timeMs
    }

    fun calculateAxisVelocity(
        releaseTimeMs: Long,
        minVelocityPx: Float,
        maxVelocityPx: Float,
    ): Float {
        if (releaseTimeMs - lastEventTimeMs >= DesktopDragStopThresholdMs) {
            return 0f
        }
        val windowStartCutoffMs = releaseTimeMs - DesktopDragVelocityWindowMs
        var totalDelta = 0f
        var windowFirstStartMs = Long.MAX_VALUE
        var windowLastEndMs = Long.MIN_VALUE
        var consistentDirection = true
        var expectedSign = 0f
        var qualifyingSamples = 0
        for (i in 0 until sampleCount) {
            val idx = (sampleHead + i) % DesktopDragSampleCapacity
            val endMs = sampleEndTimes[idx]
            if (endMs < windowStartCutoffMs) continue
            val delta = sampleAxisDeltas[idx]
            if (delta == 0f) continue
            val s = sign(delta)
            if (expectedSign == 0f) {
                expectedSign = s
            } else if (s != expectedSign) {
                consistentDirection = false
            }
            totalDelta += delta
            if (sampleStartTimes[idx] < windowFirstStartMs) {
                windowFirstStartMs = sampleStartTimes[idx]
            }
            if (endMs > windowLastEndMs) {
                windowLastEndMs = endMs
            }
            qualifyingSamples++
        }

        val lsqVelocity = velocityTracker.calculateVelocity(
            Velocity(maxVelocityPx, maxVelocityPx),
        ).toAxis()

        var rawVelocity = lsqVelocity
        if (qualifyingSamples > 0 && windowLastEndMs > windowFirstStartMs) {
            val windowAvgSpeed = (totalDelta * 1000f / (windowLastEndMs - windowFirstStartMs))
                .coerceIn(-maxVelocityPx, maxVelocityPx)
            if (
                consistentDirection &&
                windowAvgSpeed != 0f &&
                (rawVelocity == 0f || sign(rawVelocity) != sign(windowAvgSpeed))
            ) {
                rawVelocity = windowAvgSpeed
            }
        }
        return if (abs(rawVelocity) >= minVelocityPx) rawVelocity else 0f
    }
}

private val DesktopDragClockOrigin = TimeSource.Monotonic.markNow()

private fun desktopDragClockNanos(): Long = DesktopDragClockOrigin.elapsedNow().inWholeNanoseconds

private const val DragResampleCapacity = 64
private const val DragResamplePauseNanos = 40_000_000L
private const val DragResampleMinLatencyNanos = 6_000_000L
private const val DragResampleMaxLatencyNanos = 20_000_000L
private const val DragResampleMarginNanos = 2_000_000L
private const val DragReleaseVelocityWindowNanos = 24_000_000L

/**
 * Rebuilds pointer travel on the input clock and samples it once per frame, slightly in the
 * past. Applying raw mouse deltas moved the list by however many events landed in each frame
 * (2, 3, 2, 0...), which reads as judder; sampling a continuous path moves it evenly.
 */
private class DesktopDragResampler {
    private val times = LongArray(DragResampleCapacity)
    private val positions = FloatArray(DragResampleCapacity)
    private var head = 0
    private var count = 0
    private var latest = 0f
    private var applied = 0f
    private var lastTime = 0L
    private var sampledTime = Long.MIN_VALUE
    /** Smallest delivery offset seen; maps event timestamps onto the frame clock. */
    private var minOffset = Long.MAX_VALUE
    private var intervalNanos = 8_000_000f
    private var delayNanos = 0f

    val settled: Boolean get() = applied == latest

    /** Covers one input interval plus delivery delay, so the sampled point is already known. */
    val latencyNanos: Long
        get() = (intervalNanos + delayNanos).toLong()
            .plus(DragResampleMarginNanos)
            .coerceIn(DragResampleMinLatencyNanos, DragResampleMaxLatencyNanos)

    private fun push(time: Long, position: Float) {
        val index = if (count < DragResampleCapacity) {
            (head + count).also { count++ } % DragResampleCapacity
        } else {
            head.also { head = (head + 1) % DragResampleCapacity }
        }
        times[index] = time
        positions[index] = position
    }

    /** Pointer event time on the frame clock. */
    fun clockTime(eventTimeMs: Long, receivedNanos: Long): Long {
        val eventNanos = eventTimeMs * 1_000_000L
        val offset = receivedNanos - eventNanos
        if (offset < minOffset) minOffset = offset
        // Event time is whole ms; use the precise arrival time when it falls inside that ms,
        // and the event time when delivery was held back (events bunched behind a frame).
        val base = eventNanos + minOffset
        return receivedNanos.coerceIn(base, base + 1_000_000L)
    }

    fun add(delta: Float, eventTimeMs: Long, receivedNanos: Long) {
        var time = clockTime(eventTimeMs, receivedNanos)
        if (count == 0 || time - lastTime > DragResamplePauseNanos) {
            // Start (or resume after a pause) from rest one interval before this event.
            push(time - intervalNanos.toLong(), latest)
        } else {
            if (time < lastTime) time = lastTime
            val gap = time - lastTime
            if (gap > 0L) intervalNanos += (gap - intervalNanos) * 0.1f
        }
        delayNanos += ((receivedNanos - time).coerceAtLeast(0L) - delayNanos) * 0.1f
        latest += delta
        val lastIndex = (head + count - 1) % DragResampleCapacity
        if (times[lastIndex] == time) positions[lastIndex] = latest else push(time, latest)
        lastTime = time
    }

    private fun positionAt(time: Long): Float {
        if (count == 0 || time >= lastTime) return latest
        var i = count - 1
        while (i > 0) {
            val index = (head + i) % DragResampleCapacity
            val previousIndex = (head + i - 1) % DragResampleCapacity
            val t0 = times[previousIndex]
            if (t0 <= time) {
                val t1 = times[index]
                val p0 = positions[previousIndex]
                val p1 = positions[index]
                if (t1 <= t0) return p1
                return p0 + (p1 - p0) * ((time - t0).toFloat() / (t1 - t0))
            }
            i--
        }
        return positions[head]
    }

    /**
     * Speed of the pointer path over the last [windowNanos] before [releaseTime], in px/s.
     * This is the motion the user saw; a curve fitted over a whole accelerating flick
     * overshoots it, which reads as a lurch when the fling takes over.
     */
    fun releaseVelocity(releaseTime: Long, windowNanos: Long): Float {
        val end = maxOf(releaseTime, lastTime)
        return (positionAt(end) - positionAt(end - windowNanos)) * 1_000_000_000f / windowNanos
    }

    /** Travel since the previous sample; sample times never move backwards. */
    fun sampleTo(time: Long): Float {
        val t = maxOf(time, sampledTime)
        sampledTime = t
        val target = positionAt(t)
        val delta = target - applied
        applied = target
        return delta
    }
}

/** Hand-off from a drag: the fling started [playedNanos] before the current frame [frameNanos]. */
internal class DesktopDragLead(val playedNanos: Long, val frameNanos: Long)

/**
 * A fling that can take over inside the frame where the drag reached its release point, so
 * that frame shows drag up to the release plus fling for the rest of the frame.
 */
internal interface DesktopLeadFling : FlingBehavior {
    suspend fun ScrollScope.performFling(initialVelocity: Float, lead: DesktopDragLead): Float
}

private sealed interface DesktopDragScrollEvent {
    data class Delta(val delta: Float, val eventTimeMs: Long, val receivedNanos: Long) : DesktopDragScrollEvent
    data class Stop(val velocity: Float, val eventTimeMs: Long = 0L, val receivedNanos: Long = 0L) : DesktopDragScrollEvent
}

/**
 * Adds mouse drag scrolling with mobile-parity physics: 8dp slop, jitter-filtered release
 * velocity, catch-and-drag of a running fling, and nested-scroll hand-off to parents.
 */
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
        val horizontal = orientation == Orientation.Horizontal
        val density = LocalDensity.current
        val touchSlopFloorPx = with(density) { DesktopDragScrollTouchSlop.toPx() }
        val minFlingVelocityFloorPx = with(density) { DesktopDragScrollMinFlingVelocity.toPx() }
        val maxFlingVelocityCapPx = with(density) { DesktopDragScrollMaxFlingVelocity.toPx() }
        val dispatcher = remember { NestedScrollDispatcher() }
        val connection = remember { object : NestedScrollConnection {} }
        val motion = remember(state, orientation, reverseDirection, fling) { DesktopDragMotion() }
        // Wheel input interrupts a drag fling without consuming the wheel event.
        // Pointer input sits before the draw shift, so the sub-pixel offset never feeds back into deltas.
        nestedScroll(connection, dispatcher).pointerInput(motion) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Scroll && event.changes.any { it.scrollDelta != Offset.Zero }) {
                        motion.job?.cancel()
                    }
                }
            }
        }.pointerInput(
            state, orientation, direction, fling, momentumEnabled, settleWithoutMomentum,
            touchSlopFloorPx, minFlingVelocityFloorPx, maxFlingVelocityCapPx,
        ) {
            coroutineScope {
                val scope = this

                fun startDragSession(): Channel<DesktopDragScrollEvent> {
                    motion.job?.cancel()
                    val channel = Channel<DesktopDragScrollEvent>(Channel.UNLIMITED)
                    val session = Any()
                    motion.session = session
                    motion.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        var stop: DesktopDragScrollEvent.Stop? = null
                        var lead: DesktopDragLead? = null
                        var releaseVelocity = 0f
                        val momentum = momentumEnabled && NavigationMotionSettings.profile.dragMomentum
                        var releaseStarted = false
                        try {
                            state.scroll(MutatePriority.UserInput) {
                                val resampler = DesktopDragResampler()
                                var timeline = SteadyFrameTimeline()
                                fun absorb(dragEvent: DesktopDragScrollEvent?) {
                                    when (dragEvent) {
                                        is DesktopDragScrollEvent.Delta -> resampler.add(
                                            dragEvent.delta, dragEvent.eventTimeMs, dragEvent.receivedNanos,
                                        )
                                        is DesktopDragScrollEvent.Stop -> if (stop == null) stop = dragEvent
                                        null -> if (stop == null) stop = DesktopDragScrollEvent.Stop(0f)
                                    }
                                }
                                fun handsOff(): Boolean = stop.let { it != null && it.velocity != 0f && momentum }
                                var releaseClock = Long.MIN_VALUE
                                fun releaseClockTime(): Long {
                                    if (releaseClock == Long.MIN_VALUE) {
                                        val released = stop!!
                                        releaseClock = if (released.receivedNanos != 0L) {
                                            resampler.clockTime(released.eventTimeMs, released.receivedNanos)
                                        } else {
                                            desktopDragClockNanos()
                                        }
                                    }
                                    return releaseClock
                                }
                                fun releaseSpeed(releaseTime: Long): Float {
                                    val estimate = stop!!.velocity
                                    val seen = resampler.releaseVelocity(releaseTime, DragReleaseVelocityWindowNanos)
                                    // Keep the estimator's verdict on direction and stops; take the speed from
                                    // what was on screen.
                                    return if (seen * estimate > 0f) seen.coerceIn(-abs(estimate), abs(estimate)) else 0f
                                }
                                fun drain() {
                                    while (true) {
                                        val result = channel.tryReceive()
                                        if (result.isSuccess) {
                                            absorb(result.getOrThrow())
                                        } else {
                                            if (result.isClosed) absorb(null)
                                            break
                                        }
                                    }
                                }
                                // At most one scroll (and so one lazy remeasure) per frame.
                                while (true) {
                                    if (stop == null && resampler.settled) {
                                        // Pointer at rest: wait for input without requesting frames.
                                        absorb(channel.receiveCatching().getOrNull())
                                        timeline = SteadyFrameTimeline()
                                    }
                                    drain()
                                    if (stop != null && !handsOff() && resampler.settled) break
                                    val frameNanos = withFrameNanos { it }
                                    drain()
                                    val time = timeline.advance(desktopDragClockNanos())
                                    val sampleTime = time - resampler.latencyNanos
                                    val releaseTime = if (handsOff()) releaseClockTime() else Long.MAX_VALUE
                                    if (sampleTime >= releaseTime) {
                                        // Release lands inside this frame: show the drag up to the release
                                        // point, then the fling plays the rest of this same frame.
                                        val delta = resampler.sampleTo(Long.MAX_VALUE)
                                        if (delta != 0f) {
                                            scrollWholePixels(direction * delta, motion.carry, dispatcher, orientation, direction)
                                        }
                                        releaseVelocity = releaseSpeed(releaseTime)
                                        lead = DesktopDragLead(
                                            (sampleTime - releaseTime).coerceAtMost(DragResampleMaxLatencyNanos),
                                            frameNanos,
                                        )
                                        break
                                    }
                                    val delta = resampler.sampleTo(sampleTime)
                                    if (delta != 0f) {
                                        scrollWholePixels(direction * delta, motion.carry, dispatcher, orientation, direction)
                                    }
                                }
                            }
                            val release = stop
                            if (release != null) {
                                state.scroll {
                                    releaseStarted = true
                                    flingWithNestedParents(
                                        direction * (if (lead != null) releaseVelocity else release.velocity),
                                        dispatcher, orientation, direction, fling,
                                        momentum,
                                        settleWithoutMomentum,
                                        motion.carry,
                                        lead,
                                    )
                                }
                            }
                        } finally {
                            channel.close()
                            // A newer session owns the carry once it has started.
                            if (motion.session === session) motion.carry.pending = 0f
                            if (!releaseStarted) dispatcher.dispatchPostFling(Velocity.Zero, Velocity.Zero)
                        }
                    }
                    return channel
                }

                awaitEachGesture {
                    val initialDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (
                        initialDown.isConsumed ||
                        initialDown.type != PointerType.Mouse ||
                        !currentEvent.buttons.isPrimaryPressed
                    ) return@awaitEachGesture

                    val slopPx = minOf(viewConfiguration.touchSlop, touchSlopFloorPx)
                    val minFlingVelocityPx = maxOf(viewConfiguration.minimumFlingVelocity, minFlingVelocityFloorPx)
                    val maxFlingVelocityPx = minOf(viewConfiguration.maximumFlingVelocity, maxFlingVelocityCapPx)
                        .coerceAtLeast(1f)

                    // Grabbing a moving list stops it and starts dragging at once, without a click.
                    val caughtActiveScroll = motion.job?.isActive == true || state.isScrollInProgress
                    val down: PointerInputChange
                    var dragging = false
                    var dragChannel: Channel<DesktopDragScrollEvent>? = null

                    if (caughtActiveScroll) {
                        motion.job?.cancel()
                        motion.job = null
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { state.stopScroll(MutatePriority.UserInput) }
                        initialDown.consume()
                        down = initialDown
                        dragging = true
                        val mainDown = awaitPointerEvent(PointerEventPass.Main).changes
                            .firstOrNull { it.id == initialDown.id }
                        if (mainDown == null || !mainDown.pressed) return@awaitEachGesture
                    } else {
                        // Child clickables consume the Main-pass down to claim taps but leave moves
                        // unconsumed, so a consumed down must not reject the drag.
                        val mainDown = awaitPointerEvent(PointerEventPass.Main).changes
                            .firstOrNull { it.id == initialDown.id } ?: return@awaitEachGesture
                        if (!mainDown.pressed) return@awaitEachGesture
                        down = mainDown
                    }

                    val estimator = DesktopDragVelocityEstimator(horizontal).apply { start(down.uptimeMillis) }
                    var totalDx = 0f
                    var totalDy = 0f
                    var stopDispatched = false

                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // A native scrollable, slider, selection, or reorder handler owns this gesture.
                            if (change.isConsumed) break
                            val released = !change.pressed || !event.buttons.isPrimaryPressed
                            if (released) {
                                if (dragging) {
                                    change.consume()
                                    val axisVelocity = estimator.calculateAxisVelocity(
                                        releaseTimeMs = change.uptimeMillis,
                                        minVelocityPx = minFlingVelocityPx,
                                        maxVelocityPx = maxFlingVelocityPx,
                                    )
                                    dragChannel?.let { channel ->
                                        stopDispatched = true
                                        channel.trySend(
                                            DesktopDragScrollEvent.Stop(axisVelocity, change.uptimeMillis, desktopDragClockNanos()),
                                        )
                                    }
                                }
                                break
                            }

                            val delta = change.position - change.previousPosition
                            val axisDelta = if (horizontal) delta.x else delta.y
                            estimator.addDelta(change.uptimeMillis, axisDelta)
                            if (!dragging) {
                                totalDx += delta.x
                                totalDy += delta.y
                                val totalAlong = if (horizontal) totalDx else totalDy
                                val alongAxis = abs(totalAlong)
                                val acrossAxis = abs(if (horizontal) totalDy else totalDx)
                                when {
                                    horizontal && acrossAxis >= slopPx && acrossAxis > alongAxis -> break
                                    alongAxis >= slopPx && alongAxis >= acrossAxis -> {
                                        dragging = true
                                        val postSlopDelta = totalAlong - sign(totalAlong) * slopPx
                                        val channel = startDragSession()
                                        dragChannel = channel
                                        if (postSlopDelta != 0f) {
                                            channel.trySend(
                                                DesktopDragScrollEvent.Delta(postSlopDelta, change.uptimeMillis, desktopDragClockNanos()),
                                            )
                                        }
                                        change.consume()
                                    }
                                    else -> continue
                                }
                            } else {
                                if (axisDelta != 0f) {
                                    val channel = dragChannel ?: startDragSession().also { dragChannel = it }
                                    channel.trySend(
                                        DesktopDragScrollEvent.Delta(axisDelta, change.uptimeMillis, desktopDragClockNanos()),
                                    )
                                }
                                change.consume()
                            }
                        }
                    } finally {
                        val channel = dragChannel
                        if (channel != null && !stopDispatched) {
                            channel.trySend(DesktopDragScrollEvent.Stop(0f))
                            channel.close()
                        }
                    }
                }
            }
        }.drawWithContent {
            // A draw-time shift, not a graphics layer: a layer here added a full extra
            // composite per frame and doubled missed refreshes during flings.
            val shift = motion.carry.pending * direction
            if (shift == 0f) {
                drawContent()
            } else {
                translate(left = if (horizontal) shift else 0f, top = if (horizontal) 0f else shift) {
                    this@drawWithContent.drawContent()
                }
            }
        }
    }
}
