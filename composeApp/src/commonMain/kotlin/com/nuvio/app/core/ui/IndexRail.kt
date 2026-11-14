package com.nuvio.app.core.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Claim movement after touch slop so a drag never activates a rail mark's click. */
@Composable
internal fun Modifier.indexRailDrag(
    railState: LazyListState,
    onStart: () -> Unit,
    onSelect: (Int) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    val currentStart by rememberUpdatedState(onStart)
    val currentSelect by rememberUpdatedState(onSelect)
    val currentEnd by rememberUpdatedState(onEnd)
    return pointerInput(railState) {
        coroutineScope {
            val scope = this
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var dragging = false
                var pointerY = down.position.y
                var lastSelected: Int? = null
                var motion: Job? = null
                fun selectNearest() {
                    val nearest = railState.layoutInfo.visibleItemsInfo.minByOrNull {
                        abs(it.offset + it.size / 2f - pointerY)
                    }?.index ?: return
                    if (nearest != lastSelected) {
                        lastSelected = nearest
                        currentSelect(nearest)
                    }
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        pointerY = change.position.y
                        if (!change.pressed) {
                            if (dragging) {
                                selectNearest()
                                change.consume()
                            }
                            break
                        }
                        if (event.changes.count { it.pressed } != 1) break
                        if (!dragging) {
                            val distance = change.position - down.position
                            if (abs(distance.y) <= viewConfiguration.touchSlop) continue
                            if (abs(distance.x) > abs(distance.y)) break
                            dragging = true
                            currentStart()
                            selectNearest()
                            motion = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                railState.scroll(MutatePriority.UserInput) {
                                    var previous: Long? = null
                                    while (true) {
                                        val frame = withFrameNanos { it }
                                        val seconds = previous?.let { ((frame - it) / 1_000_000_000f).coerceIn(0f, 0.1f) } ?: (1f / 60)
                                        previous = frame
                                        val info = railState.layoutInfo
                                        val speed = indexRailEdgeVelocity(
                                            pointerY, info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat(),
                                            16.dp.toPx(), 240.dp.toPx(),
                                        )
                                        if (speed != 0f) scrollBy(speed * seconds)
                                        selectNearest()
                                    }
                                }
                            }
                        }
                        change.consume()
                    }
                } finally {
                    motion?.cancel()
                    if (dragging) currentEnd()
                }
            }
        }
    }
}

private object IndexRailTooltipPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset(
        x = (anchorBounds.left - popupContentSize.width).coerceAtLeast(0),
        y = (anchorBounds.center.y - popupContentSize.height / 2).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IndexRailMark(
    label: String,
    active: Boolean,
    isAdjacent: Boolean,
    itemHeight: Dp,
    onClick: () -> Unit,
    onHoverChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    expandLeft: Boolean = false,
    expanded: Boolean? = null,
    showFocus: Boolean = LocalRemoteNavigation.current?.keyboard == true,
) {
    val tooltip = rememberTooltipState()
    var focused by remember { mutableStateOf(false) }
    val tokens = MaterialTheme.nuvio
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsRemoteHoveredAsState()

    LaunchedEffect(hovered) {
        onHoverChanged?.invoke(hovered)
    }

    val isDirectlyExpanded = active || (expanded ?: ((focused && showFocus) || hovered))
    val markWidth by animateDpAsState(
        when {
            isDirectlyExpanded -> 28.dp
            isAdjacent -> 22.dp
            else -> 16.dp
        }
    )
    val barThickness by animateDpAsState(
        when {
            isDirectlyExpanded -> 4.5.dp
            isAdjacent -> 4.dp
            else -> 3.5.dp
        }
    )
    val barAlpha by animateFloatAsState(
        when {
            isDirectlyExpanded -> 1f
            isAdjacent -> 0.85f
            else -> 0.6f
        }
    )
    val showFocusTooltip = focused && showFocus && (expanded ?: true)
    LaunchedEffect(showFocusTooltip) {
        if (showFocusTooltip) tooltip.show() else tooltip.dismiss()
    }
    TooltipBox(
        positionProvider = IndexRailTooltipPosition,
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltip,
        focusable = false,
    ) {
        Box(
            modifier = modifier.size(width = 48.dp, height = itemHeight)
                .onFocusChanged { focused = it.hasFocus }
                .navigationGlassFeedback(interactionSource, tokens.shapes.button, selected = active, showFocus = showFocus)
                .hoverable(interactionSource)
                .remoteNavigationTarget(shape = tokens.shapes.button, onSelect = onClick)
                .semantics { contentDescription = label; selected = active }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.width(28.dp).height(5.dp),
                contentAlignment = if (expandLeft) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .width(markWidth)
                        .height(barThickness)
                        .background(tokens.colors.textPrimary.copy(alpha = barAlpha), CircleShape)
                )
            }
        }
    }
}

/** Pixels/second, with zero slope where the edge zone meets the stationary center. */
private fun indexRailEdgeVelocity(pointer: Float, start: Float, end: Float, edge: Float, maximumSpeed: Float): Float {
    if (edge <= 0f || end <= start || maximumSpeed <= 0f) return 0f
    val inset = edge.coerceAtMost((end - start) / 2f)
    val penetration = when {
        pointer < start + inset -> -((start + inset - pointer) / inset).coerceIn(0f, 1f)
        pointer > end - inset -> ((pointer - end + inset) / inset).coerceIn(0f, 1f)
        else -> return 0f
    }
    val amount = kotlin.math.abs(penetration)
    val eased = amount * amount * (3f - 2f * amount)
    return maximumSpeed * eased * if (penetration < 0f) -1f else 1f
}
