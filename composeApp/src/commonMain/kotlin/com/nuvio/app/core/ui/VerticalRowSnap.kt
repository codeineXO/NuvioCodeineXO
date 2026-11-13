package com.nuvio.app.core.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalSnapVerticalRows = staticCompositionLocalOf { false }
internal val LocalVerticalWheelOwner = staticCompositionLocalOf<ScrollableState?> { null }

internal fun verticalRowSnapOffset(anchors: List<Int>, direction: Float): Float? =
    (if (direction > 0f) {
        anchors.filter { it > 1 }.minOrNull()
    } else {
        anchors.filter { it < -1 }.maxOrNull()
    })?.toFloat()

internal fun LazyListState.verticalRowAnchors(): List<Int> {
    val layout = layoutInfo
    // A pinned header can remain visible after its actual row has scrolled away.
    val items = layout.visibleItemsInfo.filter { it.index >= firstVisibleItemIndex }
    return buildList {
        items.mapTo(this) { it.offset }
        val last = items.lastOrNull()
        if (last != null && last.index < layout.totalItemsCount - 1) {
            add(last.offset + last.size + layout.mainAxisItemSpacing)
        }
    }
}

internal fun LazyGridState.verticalRowAnchors(): List<Int> {
    val layout = layoutInfo
    val items = layout.visibleItemsInfo.filter { it.row >= 0 }
    return buildList {
        items.distinctBy { it.row }.mapTo(this) { it.offset.y }
        val last = items.maxByOrNull { it.index }
        if (last != null && last.index < layout.totalItemsCount - 1) {
            val rowEnd = items.filter { it.row == last.row }.maxOf { it.offset.y + it.size.height }
            add(rowEnd + layout.mainAxisItemSpacing)
        }
    }
}
