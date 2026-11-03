package com.nuvio.app.features.home.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.nuvio.app.core.ui.LocalScreenActive

internal data class AuraScrollAnchor(val key: Any, val index: Int, val offset: Int, val size: Int)
internal data class AuraScrollFrame(
    val firstIndex: Int,
    val firstOffset: Int,
    val width: Int,
    val height: Int,
    val itemCount: Int,
    val anchors: List<AuraScrollAnchor>,
    val reverseLayout: Boolean = false,
)

/** Integrate measured travel, never estimate unmeasured rows from an arbitrary item height. */
internal class AuraScrollPosition {
    private var previous: AuraScrollFrame? = null
    private var initialized = false
    private var distance = 0f

    fun update(frame: AuraScrollFrame): Float {
        val old = previous
        previous = frame.takeIf { it.anchors.isNotEmpty() }
        if (frame.anchors.isEmpty()) return distance
        if (frame.firstIndex == 0 && frame.firstOffset == 0) {
            initialized = true
            distance = 0f
            return distance
        }
        if (!initialized) {
            initialized = true
            // A restored position has no measured history: anchor locally rather than jump.
            distance = frame.firstOffset.toFloat()
            return distance
        }
        if (old == null || old.width != frame.width || old.height != frame.height ||
            old.itemCount != frame.itemCount || old.reverseLayout != frame.reverseLayout ||
            old.firstIndex == frame.firstIndex && old.firstOffset == frame.firstOffset
        ) return distance

        val anchor = frame.anchors.firstNotNullOfOrNull { current ->
            old.anchors.firstOrNull {
                it.key == current.key && it.index == current.index && it.size == current.size
            }?.let { it to current }
        } ?: return distance // Jump, replacement, or resize: re-anchor without fabricated motion.
        val delta = if (old.firstIndex == frame.firstIndex) {
            frame.firstOffset - old.firstOffset
        } else {
            (anchor.first.offset - anchor.second.offset) * if (frame.reverseLayout) -1 else 1
        }
        distance = (distance + delta).coerceAtLeast(0f)
        return distance
    }
}

@Composable
internal fun rememberAuraScrollShift(list: LazyListState?, grid: LazyGridState?, enabled: Boolean): State<Float> {
    val shift = remember(list, grid) { mutableFloatStateOf(0f) }
    val position = remember(list, grid) { AuraScrollPosition() }
    val active = LocalScreenActive.current
    LaunchedEffect(list, grid, enabled, active) {
        if (!enabled) {
            shift.floatValue = 0f
            return@LaunchedEffect
        }
        if (!active || list == null && grid == null) return@LaunchedEffect
        snapshotFlow {
            if (list != null) {
                val layout = list.layoutInfo
                AuraScrollFrame(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset,
                    layout.viewportSize.width, layout.viewportSize.height, layout.totalItemsCount,
                    layout.visibleItemsInfo.map { AuraScrollAnchor(it.key, it.index, it.offset, it.size) },
                    layout.reverseLayout)
            } else {
                val state = requireNotNull(grid)
                val layout = state.layoutInfo
                AuraScrollFrame(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset,
                    layout.viewportSize.width, layout.viewportSize.height, layout.totalItemsCount,
                    layout.visibleItemsInfo.map { AuraScrollAnchor(it.key, it.index, it.offset.y, it.size.height) },
                    layout.reverseLayout)
            }
        }.collect { shift.floatValue = position.update(it) * 0.15f }
    }
    return shift
}
