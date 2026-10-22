package com.nuvio.app.core.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp

internal data class PosterShelfItem(val controller: PosterShelfController, val key: Any, val index: Int)
internal val LocalPosterShelfItem = staticCompositionLocalOf<PosterShelfItem?> { null }

/** A shelf owns one repeat clock and one retargetable scroll, independent of focus effects. */
internal class PosterShelfController(
    private val state: LazyListState,
    private val session: RemoteNavigationSession,
    private val scope: CoroutineScope,
    private val allowSidebarEntry: Boolean = true,
) {
    var keys: List<Any> by mutableStateOf(emptyList())
        private set
    private var held: FocusDirection? = null
    private var repeatJob: Job? = null
    private var selectionJob: Job? = null
    private var scrollJob: Job? = null
    private var anchor: Any? = null
    val ownsReveal get() = anchor != null && session.activeShelf === this

    fun updateKeys(updated: List<Any>) {
        if (updated == keys) return
        val previous = keys.indexOf(anchor)
        keys = updated
        if (!ownsReveal || anchor in updated) return
        selectionJob?.cancel()
        if (updated.isEmpty()) {
            stop()
            return
        }
        select(updated[previous.coerceIn(0, updated.lastIndex)], null, null)
    }

    fun release(direction: FocusDirection) {
        if (held == direction) {
            held = null
            repeatJob?.cancel()
            repeatJob = null
        }
    }

    fun stop() {
        held = null
        repeatJob?.cancel()
        selectionJob?.cancel()
        scrollJob?.cancel()
        repeatJob = null
        selectionJob = null
        scrollJob = null
        anchor = null
    }

    fun press(source: RemoteFocusTarget, direction: FocusDirection) {
        if (held == direction) return // OS repeat must not introduce a second clock.
        session.activeShelf?.takeIf { it !== this }?.stop()
        session.activeShelf = this
        release(held ?: direction)
        selectionJob?.cancel()
        session.cancelPosterNavigation()
        held = direction
        advance(source, direction)
        repeatJob = scope.launch {
            delay(300)
            while (held == direction) {
                session.focusedTarget?.takeIf { it.shelfItem?.controller === this@PosterShelfController }
                    ?.let { advance(it, direction) }
                delay(105)
            }
        }
    }

    private fun advance(source: RemoteFocusTarget, direction: FocusDirection) {
        if (selectionJob?.isActive == true) return
        val item = source.shelfItem ?: return
        val current = keys.indexOf(item.key).takeIf { it >= 0 } ?: item.index
        val next = current + if (direction == FocusDirection.Right) 1 else -1
        if (next !in keys.indices) {
            // Preserve the existing explicit end action and sidebar rules.
            if (source.poster != null) session.enqueuePosterMove(source, direction)
            else if (direction != FocusDirection.Left || (allowSidebarEntry && session.canEnterSidebar())) {
                session.moveFocus(source.route, direction, source.manager)
                val destination = session.focusedTarget
                if (destination?.shelfItem?.controller !== this && destination?.role != RemoteNavigationRole.Sidebar) {
                    source.requester.requestFocus()
                }
            }
            return
        }
        select(keys[next], source, direction)
    }

    private fun select(key: Any, source: RemoteFocusTarget?, direction: FocusDirection?) {
        anchor = key
        startScroll()
        selectionJob = scope.launch {
            val target = withTimeoutOrNull(1500) {
                snapshotFlow {
                    session.targets.firstOrNull {
                        it.shelfItem?.let { slot -> slot.controller === this@PosterShelfController && slot.key == key } == true &&
                            it.bounds != null && state.layoutInfo.visibleItemsInfo.any { visible ->
                                visible.index == keys.indexOf(key) && visible.offset < state.layoutInfo.viewportEndOffset - state.layoutInfo.afterContentPadding &&
                                    visible.offset + visible.size > state.layoutInfo.viewportStartOffset + state.layoutInfo.beforeContentPadding
                            }
                    }
                }.first { it != null }
            }
            if (target != null && session.keyboard && session.activeShelf === this@PosterShelfController) {
                if (target.requester.requestFocus() && direction == FocusDirection.Left && session.leftHeld) {
                    session.leftMovedDuringHold = true
                }
                source?.poster?.let { session.posterPages[it.page]?.lane = null }
            }
        }
    }

    private fun startScroll() {
        if (scrollJob?.isActive == true) return
        scrollJob = scope.launch {
            state.scroll {
                var lastFrame = withFrameNanos { it }
                var velocity = 0f
                while (true) {
                    val frame = withFrameNanos { it }
                    val seconds = ((frame - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    lastFrame = frame
                    val index = keys.indexOf(anchor)
                    if (index < 0) break
                    val layout = state.layoutInfo
                    val nearest = layout.visibleItemsInfo.minByOrNull { abs(it.index - index) } ?: break
                    val measured = layout.visibleItemsInfo.firstOrNull { it.index == index }
                    val offset = measured?.offset?.toFloat() ?: (nearest.offset +
                        (index - nearest.index) * (nearest.size + layout.mainAxisItemSpacing)).toFloat()
                    val delta = offset - layout.viewportStartOffset - layout.beforeContentPadding
                    if (abs(delta) < 0.75f || (delta > 0 && !state.canScrollForward) || (delta < 0 && !state.canScrollBackward)) {
                        if (abs(delta) < 0.75f) scrollBy(delta)
                        break
                    }
                    // Critically damped tracking preserves velocity across retargets and reversals.
                    val omega = 30f
                    val decay = exp(-omega * seconds)
                    val correction = omega * delta - velocity
                    val step = delta - (delta + correction * seconds) * decay
                    velocity = (velocity + omega * correction * seconds) * decay
                    val consumed = scrollBy(step)
                    if (abs(consumed) < 0.01f && seconds > 0f) break
                }
            }
        }
    }
}

@Composable
internal fun rememberPosterShelfController(
    state: LazyListState,
    keys: List<Any>,
    allowSidebarEntry: Boolean = true,
): PosterShelfController? {
    val session = LocalRemoteNavigation.current ?: return null
    val active = LocalScreenActive.current
    val scope = rememberCoroutineScope()
    val controller = remember(state, session, allowSidebarEntry) { PosterShelfController(state, session, scope, allowSidebarEntry) }
    SideEffect { controller.updateKeys(keys) }
    DisposableEffect(controller, active) {
        if (!active) controller.stop()
        onDispose {
            controller.stop()
            if (session.activeShelf === controller) session.activeShelf = null
        }
    }
    LaunchedEffect(controller) {
        snapshotFlow { session.focusedTarget }.collect { target ->
            if (target != null && target.shelfItem?.controller !== controller && session.activeShelf === controller) {
                controller.stop()
                session.activeShelf = null
            }
        }
    }
    return controller
}

/** Limit automatic horizontal reveal to the same owner for posters and episode cards. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PosterShelfScrollOwner(controller: PosterShelfController?, content: @Composable () -> Unit) {
    val defaultReveal = LocalBringIntoViewSpec.current
    val horizontalReveal = remember(controller, defaultReveal) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (controller?.ownsReveal == true) 0f
                else defaultReveal.calculateScrollDistance(offset, size, containerSize)
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalReveal, content = content)
}
