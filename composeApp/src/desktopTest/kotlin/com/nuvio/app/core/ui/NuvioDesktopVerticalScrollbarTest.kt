package com.nuvio.app.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertTrue

class NuvioDesktopVerticalScrollbarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun mouseWheelOverScrollbarScrollsLazyList() {
        lateinit var state: LazyListState

        compose.setContent {
            state = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(100) { index ->
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
                NuvioDesktopVerticalScrollbar(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .testTag("scrollbar"),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("scrollbar").performMouseInput {
            scroll(100f)
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertTrue(
                state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0,
                "Expected lazy list to scroll forward after mouse wheel scroll over scrollbar, but was index ${state.firstVisibleItemIndex}, offset ${state.firstVisibleItemScrollOffset}",
            )
        }
    }

    @Test
    fun mouseWheelOverScrollbarScrollsLazyGrid() {
        lateinit var state: LazyGridState

        compose.setContent {
            state = rememberLazyGridState()
            Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(100) { index ->
                        Box(Modifier.fillMaxWidth().height(100.dp))
                    }
                }
                NuvioDesktopVerticalScrollbar(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .testTag("scrollbar"),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("scrollbar").performMouseInput {
            scroll(100f)
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertTrue(
                state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0,
                "Expected lazy grid to scroll forward after mouse wheel scroll over scrollbar, but was index ${state.firstVisibleItemIndex}, offset ${state.firstVisibleItemScrollOffset}",
            )
        }
    }

    @Test
    fun mouseWheelOverScrollbarScrollsScrollState() {
        lateinit var state: ScrollState

        compose.setContent {
            state = rememberScrollState()
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(state),
                ) {
                    Box(Modifier.fillMaxWidth().height(5000.dp))
                }
                NuvioDesktopVerticalScrollbar(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .testTag("scrollbar"),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("scrollbar").performMouseInput {
            scroll(100f)
        }
        compose.waitForIdle()

        compose.runOnIdle {
            assertTrue(
                state.value > 0,
                "Expected ScrollState to scroll forward after mouse wheel scroll over scrollbar, but was value ${state.value}",
            )
        }
    }
}
