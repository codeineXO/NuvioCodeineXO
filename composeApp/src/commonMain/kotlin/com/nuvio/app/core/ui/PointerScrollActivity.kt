package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tracks wheel, drag and arrow-button scroll motion so cursor-driven poster raise
 * and glow wait until the content stops sliding under a stationary pointer.
 * Keyboard focus styling is not gated. Accessed on the UI dispatcher only.
 */
internal object PointerScrollActivity {
    /** Bridges short gaps between wheel notches so hover does not flicker mid-burst. */
    private const val SettleDelayMillis = 140L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeCount = 0
    private var settleJob: Job? = null

    var moving by mutableStateOf(false)
        private set

    fun begin() {
        activeCount++
        settleJob?.cancel()
        settleJob = null
        moving = true
    }

    fun end() {
        activeCount = (activeCount - 1).coerceAtLeast(0)
        if (activeCount > 0) return
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(SettleDelayMillis)
            if (activeCount == 0) moving = false
        }
    }
}

/** Pointer hover that stays false while content scrolls; only cards whose result flips recompose. */
@Composable
internal fun State<Boolean>.settledPointerHover(): State<Boolean> =
    remember(this) { derivedStateOf { value && !PointerScrollActivity.moving } }

/**
 * Writes whether the pointer is over this node into [inside]. Unlike hoverable plus
 * collectIsHoveredAsState it launches no coroutine, so idle cards stay cheap, and a
 * reader that starts observing later still sees a hover that began earlier.
 */
internal fun Modifier.trackPointerInside(inside: MutableState<Boolean>): Modifier =
    this then PointerInsideElement(inside)

private data class PointerInsideElement(val inside: MutableState<Boolean>) : ModifierNodeElement<PointerInsideNode>() {
    override fun create() = PointerInsideNode(inside)
    override fun update(node: PointerInsideNode) {
        if (node.inside !== inside) {
            node.inside.value = false
            node.inside = inside
        }
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "trackPointerInside"
    }
}

private class PointerInsideNode(var inside: MutableState<Boolean>) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main) return
        when (pointerEvent.type) {
            PointerEventType.Enter -> inside.value = true
            PointerEventType.Exit -> inside.value = false
        }
    }

    override fun onCancelPointerInput() {
        inside.value = false
    }

    override fun onDetach() {
        inside.value = false
    }
}
