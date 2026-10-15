package com.nuvio.app.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.awt.event.MouseWheelEvent

@Composable
internal actual fun NuvioDesktopVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier,
    backgroundColor: Color?,
) {
    VerticalScrollbar(
        // A density change resizes items without necessarily changing the list's pixel width.
        adapter = remember(state, LocalDensity.current) { MeasuredLazyListScrollbarAdapter(state) },
        modifier = Modifier
            .scrollbarMouseWheelScroll(state)
            .then(modifier)
            .recalculateWindowInsets()
            .statusBarsPadding(),
        style = nuvioDesktopScrollbarStyle(backgroundColor),
    )
}

@Composable
internal actual fun NuvioDesktopVerticalScrollbar(
    state: LazyGridState,
    modifier: Modifier,
    backgroundColor: Color?,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = Modifier
            .scrollbarMouseWheelScroll(state)
            .then(modifier)
            .recalculateWindowInsets()
            .statusBarsPadding(),
        style = nuvioDesktopScrollbarStyle(backgroundColor),
    )
}

@Composable
internal actual fun NuvioDesktopVerticalScrollbar(
    state: ScrollState,
    modifier: Modifier,
    backgroundColor: Color?,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = Modifier
            .scrollbarMouseWheelScroll(state)
            .then(modifier)
            .recalculateWindowInsets()
            .statusBarsPadding(),
        style = nuvioDesktopScrollbarStyle(backgroundColor),
    )
}

@Composable
private fun Modifier.scrollbarMouseWheelScroll(state: ScrollableState): Modifier {
    val density = LocalDensity.current
    val fallbackStepPx = remember(density) { with(density) { 48.dp.toPx() } }

    return this.pointerInput(state, fallbackStepPx) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.type == PointerEventType.Scroll) {
                    val totalDeltaY = event.changes.fold(0f) { acc, change ->
                        if (!change.isConsumed) acc + change.scrollDelta.y else acc
                    }
                    if (totalDeltaY != 0f) {
                        val awtEvent = event.awtEventOrNull as? MouseWheelEvent
                        val scrollAmount = awtEvent?.scrollAmount?.coerceAtLeast(1) ?: 3
                        val isPageScroll = awtEvent?.scrollType == MouseWheelEvent.WHEEL_BLOCK_SCROLL

                        val containerHeight = size.height
                        val scrollDeltaPx = if (isPageScroll && containerHeight > 0) {
                            totalDeltaY * containerHeight * 0.9f
                        } else {
                            val lineStepPx = if (containerHeight > 0) {
                                (containerHeight / 20f).coerceIn(24f, 60f)
                            } else {
                                fallbackStepPx
                            }
                            totalDeltaY * lineStepPx * scrollAmount
                        }

                        state.dispatchRawDelta(scrollDeltaPx)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }
}

@Composable
private fun nuvioDesktopScrollbarStyle(backgroundColor: Color?): ScrollbarStyle {
    val colorScheme = MaterialTheme.colorScheme
    val adaptiveThumbColor = backgroundColor?.let { color ->
        if (color.luminance() > EqualBlackWhiteContrastLuminance) Color.Black else Color.White
    }
    return ScrollbarStyle(
        minimalHeight = 48.dp,
        thickness = 6.dp,
        shape = RoundedCornerShape(100),
        hoverDurationMillis = 180,
        unhoverColor = adaptiveThumbColor?.copy(alpha = 0.72f)
            ?: colorScheme.onSurfaceVariant.copy(alpha = 0.34f),
        hoverColor = adaptiveThumbColor?.copy(alpha = 0.94f)
            ?: colorScheme.primary.copy(alpha = 0.78f),
    )
}

private const val EqualBlackWhiteContrastLuminance = 0.179f
