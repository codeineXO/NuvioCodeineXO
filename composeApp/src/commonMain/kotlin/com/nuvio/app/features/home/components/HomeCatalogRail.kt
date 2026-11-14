package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.IndexRailMark
import com.nuvio.app.core.ui.LocalRemoteNavigation
import com.nuvio.app.core.ui.indexRailDrag
import com.nuvio.app.core.ui.nuvioVerticalMouseWheelScroll
import kotlin.math.abs

internal data class HomeCatalogRailEntry(val key: Any, val title: String, val lazyIndex: Int)

@Composable
internal fun HomeCatalogRail(
    entries: List<HomeCatalogRailEntry>,
    homeListState: LazyListState,
    maxHeight: Dp,
) {
    val activeIndex by remember(entries, homeListState) {
        derivedStateOf {
            val atEnd = homeListState.canScrollBackward && !homeListState.canScrollForward
            if (atEnd) entries.lastIndex
            else entries.indexOfLast { it.lazyIndex <= homeListState.firstVisibleItemIndex }.coerceAtLeast(0)
        }
    }
    HomeCatalogRail(entries, activeIndex, maxHeight) {
        homeListState.requestScrollToItem(it.lazyIndex)
    }
}

@Composable
internal fun HomeCatalogRail(
    entries: List<HomeCatalogRailEntry>,
    activeIndex: Int,
    maxHeight: Dp,
    onSelect: (HomeCatalogRailEntry) -> Unit,
) {
    if (entries.isEmpty() || maxHeight < 18.dp) return
    val railState = rememberLazyListState()
    val navigation = LocalRemoteNavigation.current
    var hoveredIndex by remember(entries) { mutableStateOf<Int?>(null) }
    var focusedIndex by remember(entries) { mutableStateOf<Int?>(null) }
    var scrubbedIndex by remember(entries) { mutableStateOf<Int?>(null) }
    val keyboardActive = navigation?.keyboard == true

    // Follow changes in the Home list while allowing the rail to be browsed on its own.
    LaunchedEffect(activeIndex) {
        if (activeIndex in entries.indices && scrubbedIndex == null && (!keyboardActive || focusedIndex == null) &&
            railState.layoutInfo.visibleItemsInfo.none { it.index == activeIndex }
        ) {
            railState.scrollToItem(activeIndex)
        }
    }

    val itemHeight = when {
        entries.size >= 30 -> 12.dp
        entries.size >= 20 -> 14.dp
        entries.size >= 12 -> 16.dp
        else -> 18.dp
    }
    LazyColumn(
        state = railState,
        modifier = Modifier.width(48.dp).heightIn(max = maxHeight)
            .indexRailDrag(
                railState = railState,
                onStart = {
                    hoveredIndex = null
                    navigation?.keyboard = false
                },
                onSelect = { index ->
                    entries.getOrNull(index)?.let {
                        scrubbedIndex = index
                        onSelect(it)
                    }
                },
                onEnd = { scrubbedIndex = null },
            )
            .pointerInput(railState) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Exit, PointerEventType.Scroll -> hoveredIndex = null
                            PointerEventType.Enter, PointerEventType.Move -> {
                                val pointer = event.changes.firstOrNull { !it.pressed && !it.previousPressed }
                                if (pointer != null) {
                                    hoveredIndex = railState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        pointer.position.y >= it.offset && pointer.position.y < it.offset + it.size
                                    }?.index
                                }
                            }
                        }
                    }
                }
            }
            .nuvioVerticalMouseWheelScroll(railState, consumeAtBounds = true),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
            val highlightedIndex = scrubbedIndex ?: if (keyboardActive) focusedIndex else hoveredIndex
            IndexRailMark(
                label = entry.title,
                active = index == (scrubbedIndex ?: activeIndex),
                isAdjacent = highlightedIndex?.let { abs(index - it) == 1 } == true,
                itemHeight = itemHeight,
                onClick = { onSelect(entry) },
                onHoverChanged = { isHovered ->
                    if (isHovered) {
                        hoveredIndex = index
                    } else if (hoveredIndex == index) {
                        hoveredIndex = null
                    }
                },
                modifier = Modifier.onFocusChanged {
                    if (it.hasFocus) focusedIndex = index
                    else if (focusedIndex == index) focusedIndex = null
                },
                expandLeft = true,
                expanded = index == highlightedIndex,
                showFocus = keyboardActive,
            )
        }
    }
}
