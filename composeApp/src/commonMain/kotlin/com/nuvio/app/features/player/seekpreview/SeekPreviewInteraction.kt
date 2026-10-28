package com.nuvio.app.features.player.seekpreview

import androidx.compose.ui.composed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Local interaction state; previewing never changes the playback target. */
internal class SeekPreviewInteraction {
    var hoverPositionMs by mutableStateOf<Long?>(null)
    var dragging by mutableStateOf(false)
    var pointerPressed by mutableStateOf(false)
        private set
    var keyboardPositionMs by mutableStateOf<Long?>(null)
        private set
    var timelineLeft by mutableStateOf(0f)
    var timelineWidth by mutableStateOf(0f)
    private var keyboardJob: Job? = null

    fun keyboard(positionMs: Long, scope: CoroutineScope) {
        keyboardJob?.cancel()
        keyboardPositionMs = positionMs
        keyboardJob = scope.launch {
            delay(1500)
            keyboardPositionMs = null
        }
    }

    fun startPointer() {
        keyboardJob?.cancel()
        keyboardPositionMs = null
        pointerPressed = true
        dragging = false
    }

    fun endPointer() {
        pointerPressed = false
        dragging = false
    }

    fun clear() {
        keyboardJob?.cancel()
        keyboardPositionMs = null
        hoverPositionMs = null
        endPointer()
    }
}

internal val LocalSeekPreviewInteraction = staticCompositionLocalOf<SeekPreviewInteraction?> { null }

internal fun seekPreviewKeyboardOffset(event: KeyEvent): Long? {
    if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return null
    val direction = when (event.key) {
        Key.DirectionLeft, Key.J -> -1L
        Key.DirectionRight, Key.L -> 1L
        else -> return null
    }
    val step = when {
        event.isShiftPressed -> 1_000L
        event.key == Key.J || event.key == Key.L -> 10_000L
        else -> 3_000L
    }
    return direction * step
}

/** Observe events before the slider consumes them, without consuming input ourselves. */
internal fun Modifier.seekPreviewInput(durationMs: Long): Modifier = composed {
    val interaction = LocalSeekPreviewInteraction.current
    if (interaction == null) this else this
        .onGloballyPositioned {
            val bounds = it.boundsInRoot()
            interaction.timelineLeft = bounds.left
            interaction.timelineWidth = bounds.width
        }
        .pointerInput(interaction, durationMs) {
            try {
                awaitPointerEventScope {
                    var pressX = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Exit -> interaction.hoverPositionMs = null
                            PointerEventType.Press -> {
                                pressX = change.position.x
                                interaction.startPointer()
                            }
                            PointerEventType.Move -> {
                                if (interaction.pointerPressed && change.pressed &&
                                    abs(change.position.x - pressX) >= viewConfiguration.touchSlop
                                ) interaction.dragging = true
                            }
                        }
                        if (event.type != PointerEventType.Exit && durationMs > 0 && size.width > 0) {
                            val within = (change.type == PointerType.Mouse || (interaction.dragging && change.pressed)) &&
                                change.position.x in 0f..size.width.toFloat() &&
                                change.position.y in 0f..size.height.toFloat()
                            interaction.hoverPositionMs = if (within) {
                                (durationMs * (change.position.x / size.width).coerceIn(0f, 1f)).toLong()
                            } else null
                        }
                        if (event.type == PointerEventType.Release) {
                            // Slider tap callbacks run in Main; retain pointer ownership until then.
                            awaitPointerEvent(PointerEventPass.Final)
                            interaction.endPointer()
                        }
                    }
                }
            } finally {
                interaction.clear()
            }
        }
}
