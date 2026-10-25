package com.nuvio.app.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopDragScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var scroll: ScrollState
    private lateinit var list: LazyListState
    private lateinit var grid: LazyGridState
    private var clicks = 0
    private val active = mutableStateOf(true)
    private val visible = mutableStateOf(true)

    @Test fun mouseDragsVerticalColumn() {
        column()
        drag()
        compose.runOnIdle { assertTrue(scroll.value > 0) }
    }

    @Test fun mouseDragsLazyColumn() {
        compose.setContent {
            list = rememberLazyListState()
            NuvioLazyColumn(Modifier.size(320.dp).testTag("scroll"), state = list) {
                items(100) { Box(Modifier.fillMaxWidth().height(80.dp)) }
            }
        }
        drag()
        compose.runOnIdle { assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0) }
    }

    @Test fun mouseDragsLazyGrid() {
        compose.setContent {
            grid = rememberLazyGridState()
            NuvioLazyVerticalGrid(GridCells.Fixed(2), Modifier.size(320.dp).testTag("scroll"), state = grid) {
                items(100) { Box(Modifier.fillMaxWidth().height(80.dp)) }
            }
        }
        drag()
        compose.runOnIdle { assertTrue(grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 0) }
    }

    @Test fun nativeTouchStillScrollsOnce() {
        column()
        compose.onNodeWithTag("scroll").performTouchInput {
            swipe(Offset(center.x, height * 0.8f), Offset(center.x, height * 0.2f), 240)
        }
        compose.runOnIdle { assertTrue(scroll.value > 0) }
    }

    @Test fun verticalSwipeOverPosterRowScrollsPageOnly() {
        nested()
        drag(tag = "posters")
        compose.runOnIdle {
            assertTrue(list.firstVisibleItemScrollOffset > 0 || list.firstVisibleItemIndex > 0)
            assertEquals(0, scroll.value)
        }
    }

    @Test fun horizontalSwipeOverPosterRowScrollsPostersOnly() {
        nested()
        drag(tag = "posters", horizontal = true)
        compose.runOnIdle {
            assertTrue(scroll.value > 0)
            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, list.firstVisibleItemScrollOffset)
        }
    }

    @Test fun horizontalLazyRowGetsMomentum() {
        compose.setContent {
            list = rememberLazyListState()
            LazyRow(Modifier.size(320.dp).nuvioDesktopDragScroll(list).testTag("scroll"), state = list) {
                items(100) { Box(Modifier.width(80.dp).height(100.dp)) }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        drag(horizontal = true)
        val released = list.firstVisibleItemIndex * 10000 + list.firstVisibleItemScrollOffset
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(list.firstVisibleItemIndex * 10000 + list.firstVisibleItemScrollOffset > released) }
    }

    @Test fun verticalFlingContinuesAndNewPressStopsIt() {
        column()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        drag()
        val released = scroll.value
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        assertTrue(scroll.value > released, "Swipe must keep moving after release")
        compose.onNodeWithTag("scroll").performMouseInput { moveTo(center); press() }
        compose.waitForIdle()
        val stopped = scroll.value
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        assertEquals(stopped, scroll.value)
        compose.onNodeWithTag("scroll").performMouseInput { release() }
    }

    @Test fun horizontalScrollStateGetsMomentum() {
        column(horizontal = true)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        drag(horizontal = true)
        val released = scroll.value
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        assertTrue(scroll.value > released)
    }

    @Test fun leavingScreenCancelsMomentum() {
        column()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        drag()
        compose.runOnIdle { active.value = false }
        compose.waitForIdle()
        val stopped = scroll.value
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        assertEquals(stopped, scroll.value)
    }

    @Test fun disposingScrollerCancelsMomentum() {
        column()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        drag()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        val stopped = scroll.value
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        assertEquals(stopped, scroll.value)
    }

    @Test fun posterMouseWheelInterruptsMomentumWithoutChangingWheelHandling() {
        compose.setContent {
            list = rememberLazyListState()
            LazyRow(
                Modifier.size(320.dp).testTag("scroll")
                    .nuvioPosterRowMouseWheelScroll(list, scrollStepPx = 1f)
                    .nuvioDesktopDragScroll(list),
                state = list,
            ) {
                items(100) { Box(Modifier.width(80.dp).height(100.dp)) }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        drag(horizontal = true)
        val released = list.firstVisibleItemIndex * 10000 + list.firstVisibleItemScrollOffset
        compose.onNodeWithTag("scroll").performMouseInput { scroll(20f) }
        compose.waitForIdle()
        val stopped = list.firstVisibleItemIndex * 10000 + list.firstVisibleItemScrollOffset
        assertTrue(stopped > released, "Existing wheel handler must still move the row")
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        assertEquals(stopped, list.firstVisibleItemIndex * 10000 + list.firstVisibleItemScrollOffset)
    }

    @Test fun legacyPlayerHorizontalDragStopsOnRelease() {
        compose.setContent {
            scroll = rememberScrollState()
            Row(Modifier.size(320.dp).testTag("scroll")
                .nuvioDesktopDragScroll(scroll, momentumEnabled = false).horizontalScroll(scroll)) {
                Box(Modifier.size(5000.dp, 320.dp))
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        drag(horizontal = true)
        val stopped = scroll.value
        assertTrue(stopped > 0)
        compose.mainClock.advanceTimeBy(160)
        compose.waitForIdle()
        assertEquals(stopped, scroll.value)
    }

    @Test fun syntheticTouchNestedRowsKeepTheirAxis() {
        nested()
        compose.onNodeWithTag("posters").performTouchInput {
            swipe(Offset(width * 0.8f, center.y), Offset(width * 0.2f, center.y), 240)
        }
        compose.runOnIdle {
            assertTrue(scroll.value > 0)
            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, list.firstVisibleItemScrollOffset)
        }
    }

    @Test fun dragCancelsClickButTapStillWorks() {
        column(clickable = true)
        compose.onNodeWithTag("scroll").performMouseInput { moveTo(center); click() }
        compose.runOnIdle { assertEquals(1, clicks) }
        drag()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun shortContentAndBoundaryDoNotMove() {
        column(short = true)
        drag()
        compose.runOnIdle { assertEquals(0, scroll.value) }
        drag(backward = true)
        compose.runOnIdle { assertEquals(0, scroll.value) }
    }

    @Test fun upwardBoundaryDoesNotOverscroll() {
        column()
        drag(backward = true)
        compose.runOnIdle { assertEquals(0, scroll.value) }
    }

    @Test fun disabledHandlerPreservesExistingBehavior() {
        column(enabled = false)
        drag()
        compose.runOnIdle { assertEquals(0, scroll.value) }
    }

    @Test fun sharedPlayerListCanKeepMouseDraggingDisabled() {
        compose.setContent {
            list = rememberLazyListState()
            NuvioLazyColumn(Modifier.size(320.dp).testTag("scroll"), state = list, desktopDragEnabled = false) {
                items(100) { Box(Modifier.fillMaxWidth().height(80.dp)) }
            }
        }
        drag()
        compose.runOnIdle { assertEquals(0, list.firstVisibleItemScrollOffset) }
    }

    @Test fun childVerticalControlKeepsItsGesture() {
        var childMovement = 0f
        compose.setContent {
            scroll = rememberScrollState()
            Column(Modifier.size(320.dp).nuvioVerticalScroll(scroll)) {
                Box(Modifier.size(320.dp).testTag("scroll").draggable(
                    state = rememberDraggableState { childMovement += it },
                    orientation = Orientation.Vertical,
                ))
                Box(Modifier.size(320.dp, 4000.dp))
            }
        }
        drag()
        compose.runOnIdle {
            assertTrue(childMovement < 0f)
            assertEquals(0, scroll.value)
        }
    }

    @Test fun reverseLayoutScrollsInTheOppositeDirection() {
        compose.setContent {
            list = rememberLazyListState()
            NuvioLazyColumn(Modifier.size(320.dp).testTag("scroll"), state = list, reverseLayout = true) {
                items(100) { Box(Modifier.fillMaxWidth().height(80.dp)) }
            }
        }
        drag(backward = true)
        compose.runOnIdle { assertTrue(list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0) }
    }

    private fun column(horizontal: Boolean = false, enabled: Boolean = true, short: Boolean = false, clickable: Boolean = false) {
        compose.setContent {
            scroll = rememberScrollState()
            CompositionLocalProvider(LocalScreenActive provides active.value) {
                if (!visible.value) return@CompositionLocalProvider
                val modifier = Modifier.size(320.dp).testTag("scroll")
                val child = Modifier.then(if (clickable) Modifier.clickable { clicks++ } else Modifier)
                if (horizontal) {
                    Row(modifier.nuvioDesktopDragScroll(scroll).horizontalScroll(scroll)) {
                        Box(child.size(if (short) 100.dp else 5000.dp, 320.dp))
                    }
                } else {
                    Column(modifier.nuvioVerticalScroll(scroll, enabled = enabled)) {
                        Box(child.size(320.dp, if (short) 100.dp else 5000.dp))
                    }
                }
            }
        }
    }

    private fun nested() {
        compose.setContent {
            list = rememberLazyListState()
            scroll = rememberScrollState()
            NuvioLazyColumn(Modifier.size(320.dp), state = list) {
                item {
                    Row(Modifier.height(180.dp).fillMaxWidth().testTag("posters")
                        .nuvioDesktopDragScroll(scroll).horizontalScroll(scroll)) {
                        Box(Modifier.size(5000.dp, 180.dp).clickable { clicks++ })
                    }
                }
                items(100) { Box(Modifier.fillMaxWidth().height(80.dp)) }
            }
        }
    }

    private fun drag(tag: String = "scroll", horizontal: Boolean = false, backward: Boolean = false) {
        compose.onNodeWithTag(tag).performMouseInput {
            val start = if (backward) 0.2f else 0.8f
            val end = if (backward) 0.8f else 0.2f
            fun point(fraction: Float) = if (horizontal) Offset(width * fraction, center.y) else Offset(center.x, height * fraction)
            moveTo(point(start))
            press()
            repeat(6) { step ->
                advanceEventTime(16)
                moveTo(point(start + (end - start) * (step + 1) / 6f))
            }
            release()
        }
        compose.waitForIdle()
    }
}
