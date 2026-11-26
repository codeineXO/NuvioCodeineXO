package com.nuvio.app.core.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.modifier.modifierLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop
import kotlin.math.abs

/** Modifier-local routing also follows children composed by lazy layouts. */
internal val ModifierLocalSoftWheelRoute = modifierLocalOf<SoftWheelRoute?> { null }

internal class SoftWheelRoute {
    var parent: SoftWheelRoute? = null
    var heroStretch: HeroStretchState? = null
    private var eventTime: Long? = null
    private var pointerId: Long? = null
    private var owner: SoftWheelRoute? = null

    // PointerEvent wrappers differ between layout nodes because positions are local.
    private fun matches(event: PointerEvent): Boolean {
        val change = event.changes.firstOrNull() ?: return false
        return eventTime == change.uptimeMillis && pointerId == change.id.value
    }

    fun hasClaim(event: PointerEvent): Boolean = matches(event) || parent?.hasClaim(event) == true

    fun owns(event: PointerEvent): Boolean = matches(event) && owner === this

    fun claim(event: PointerEvent, owner: SoftWheelRoute = this) {
        eventTime = event.changes.firstOrNull()?.uptimeMillis
        pointerId = event.changes.firstOrNull()?.id?.value
        this.owner = owner
        parent?.claim(event, owner)
    }
}

/** Vertical content wheels by scroll state, so scrollbars can drive the same glide instead of a competing one. */
internal object SoftWheelRegistry {
    /** [direction] is the sign the content applies to wheel deltas before [SoftWheelScroll.scrollBy]. */
    class Entry(val wheel: SoftWheelScroll, val direction: Float)

    private val wheels = mutableMapOf<ScrollableState, Entry>()

    fun register(state: ScrollableState, wheel: SoftWheelScroll, reverseScrolling: Boolean) {
        wheels[state] = Entry(wheel, if (reverseScrolling) -1f else 1f)
    }

    fun unregister(state: ScrollableState, wheel: SoftWheelScroll) {
        if (wheels[state]?.wheel === wheel) wheels.remove(state)
    }

    fun get(state: ScrollableState): Entry? = wheels[state]
}

/** Content wheel step per notch; scrollbar wheel input uses the same value. */
internal val SoftWheelStep = 100.dp

/** Reserve native wheel input, then let descendant containers take ownership before scrolling. */
internal fun Modifier.nuvioVerticalMouseWheelScroll(
    state: ScrollableState,
    enabled: Boolean = true,
    reverseScrolling: Boolean = false,
    rowAnchors: (() -> List<Int>)? = null,
    consumeAtBounds: Boolean = false,
): Modifier {
    if (!isDesktop || !enabled) return this
    return composed {
        val active = LocalScreenActive.current
        val scope = rememberCoroutineScope()
        val dispatcher = remember { NestedScrollDispatcher() }
        val connection = remember { object : NestedScrollConnection {} }
        val route = remember { SoftWheelRoute() }
        val wheel = remember(state, scope, reverseScrolling, rowAnchors) {
            SoftWheelScroll(state, scope, dispatcher, reverseScrolling, rowAnchors) { delta ->
                route.heroStretch?.acceptsWheelOverscroll(state, delta, reverseScrolling) == true
            }
        }
        DisposableEffect(wheel, active) {
            if (!active) wheel.cancel() else SoftWheelRegistry.register(state, wheel, reverseScrolling)
            onDispose {
                SoftWheelRegistry.unregister(state, wheel)
                wheel.cancel()
            }
        }
        if (!active) return@composed this
        modifierLocalConsumer {
            route.parent = ModifierLocalSoftWheelRoute.current
            route.heroStretch = ModifierLocalHeroWheelStretch.current
        }
            .modifierLocalProvider(ModifierLocalSoftWheelRoute) { route }
            .nestedScroll(connection, dispatcher)
            .pointerInput(wheel, route, reverseScrolling) {
                val stepPx = SoftWheelStep.toPx()
                val direction = if (reverseScrolling) -1f else 1f
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) wheel.cancel()
                        if (event.type != PointerEventType.Scroll) continue
                        val x = event.changes.sumOf { it.scrollDelta.x.toDouble() }.toFloat()
                        val y = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                        if (y == 0f || abs(x) > abs(y)) continue
                        // Respect menus/page-switch suppression; a vertical ancestor's reservation
                        // can still be taken over by the innermost scrollable child.
                        if (event.changes.any { it.isConsumed } && route.parent?.hasClaim(event) != true) continue
                        val delta = y * stepPx * direction
                        if (!consumeAtBounds && !wheel.canScrollBy(delta)) continue
                        route.claim(event)
                        event.changes.forEach { it.consume() }
                        awaitPointerEvent(PointerEventPass.Main)
                        if (route.owns(event)) wheel.scrollBy(delta)
                    }
                }
            }
    }
}

/** Reserve native wheel input, then scroll horizontal containers with vertical or horizontal wheel events. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.nuvioHorizontalMouseWheelScroll(
    state: ScrollableState,
    enabled: Boolean = true,
    reverseScrolling: Boolean = false,
): Modifier {
    if (!isDesktop || !enabled) return this
    return composed {
        val active = LocalScreenActive.current
        val scope = rememberCoroutineScope()
        val horizontalEvents = LocalHorizontalWheelEvents.current
        val dispatcher = remember { NestedScrollDispatcher() }
        val connection = remember { object : NestedScrollConnection {} }
        val route = remember { SoftWheelRoute() }
        val wheel = remember(state, scope, reverseScrolling) {
            SoftWheelScroll(state, scope, dispatcher, reverseScrolling)
        }
        val density = LocalDensity.current
        val stepPx = remember(density) { with(density) { 80.dp.toPx() } }
        val layoutDirection = LocalLayoutDirection.current

        DisposableEffect(wheel, active) {
            if (!active) wheel.cancel()
            onDispose { wheel.cancel() }
        }
        if (!active) return@composed this

        modifierLocalConsumer { route.parent = ModifierLocalSoftWheelRoute.current }
            .modifierLocalProvider(ModifierLocalSoftWheelRoute) { route }
            .nestedScroll(connection, dispatcher)
            .onPointerEvent(PointerEventType.Press) { wheel.cancel() }
            // Initial pass: consume before the container's built-in wheel handling sees
            // Shift+wheel (horizontal) deltas, otherwise both scroll it.
            .pointerInput(wheel, route, horizontalEvents, stepPx, layoutDirection, reverseScrolling) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Scroll) continue
                        val canScrollHorizontally = state.canScrollBackward || state.canScrollForward
                        if (!canScrollHorizontally) continue

                        val totalDelta = event.changes.fold(0f) { acc, change ->
                            acc + change.scrollDelta.y + change.scrollDelta.x
                        }
                        if (totalDelta != 0f) {
                            val isRtl = layoutDirection == LayoutDirection.Rtl
                            val direction = if (reverseScrolling xor isRtl) -1f else 1f
                            val delta = totalDelta * stepPx * direction
                            route.claim(event)
                            horizontalEvents?.handled = true
                            wheel.scrollBy(delta)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
    }
}
