package com.nuvio.app.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.nuvio.app.isDesktop

/** Shared desktop wheel easing and drag behavior with native touch scrolling. */
@Composable
internal fun NuvioLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (reverseLayout) Arrangement.Bottom else Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = rememberNuvioFlingBehavior(),
    userScrollEnabled: Boolean = true,
    desktopDragEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    val snap = LocalSnapVerticalRows.current && !reverseLayout
    val fling = if (snap) rememberSnapFlingBehavior(state, SnapPosition.Start) else flingBehavior
    val ownsWheel = LocalVerticalWheelOwner.current !== state
    val rowAnchors = if (snap && ownsWheel) remember(state) { { state.verticalRowAnchors() } } else null
    LazyColumn(
        modifier = modifier
            .nuvioVerticalMouseWheelScroll(state, userScrollEnabled && ownsWheel, reverseLayout, rowAnchors)
            .nuvioDesktopDragScroll(
                state, Orientation.Vertical, userScrollEnabled && desktopDragEnabled, reverseLayout, fling,
                settleWithoutMomentum = snap,
            ),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = fling,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

@Composable
internal fun NuvioLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (reverseLayout) Arrangement.Bottom else Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = rememberNuvioFlingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit,
) {
    val snap = LocalSnapVerticalRows.current && !reverseLayout
    val fling = if (snap) rememberSnapFlingBehavior(state, SnapPosition.Start) else flingBehavior
    val ownsWheel = LocalVerticalWheelOwner.current !== state
    val rowAnchors = if (snap && ownsWheel) remember(state) { { state.verticalRowAnchors() } } else null
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier
            .nuvioVerticalMouseWheelScroll(state, userScrollEnabled && ownsWheel, reverseLayout, rowAnchors)
            .nuvioDesktopDragScroll(
                state, Orientation.Vertical, userScrollEnabled, reverseLayout, fling,
                settleWithoutMomentum = snap,
            )
            .navigationGlassSource(zIndex = 1f),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        flingBehavior = fling,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

internal fun Modifier.nuvioVerticalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
): Modifier {
    if (!isDesktop) return verticalScroll(state, enabled, flingBehavior, reverseScrolling)
    return composed {
        val fling = flingBehavior ?: rememberNuvioFlingBehavior()
        nuvioVerticalMouseWheelScroll(state, enabled, reverseScrolling)
            .nuvioDesktopDragScroll(state, Orientation.Vertical, enabled, reverseScrolling, fling)
            .verticalScroll(state, enabled, fling, reverseScrolling)
    }
}
