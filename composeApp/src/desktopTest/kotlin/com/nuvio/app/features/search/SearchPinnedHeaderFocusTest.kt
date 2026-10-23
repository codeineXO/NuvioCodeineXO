package com.nuvio.app.features.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.RemoteNavigationHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalFoundationApi::class)
class SearchPinnedHeaderFocusTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: LazyListState
    private lateinit var scope: CoroutineScope

    private fun showScrolledSearch() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(500.dp, 400.dp)) {
                    state = rememberLazyListState()
                    scope = rememberCoroutineScope()
                    var query by remember { mutableStateOf("") }
                    var focused by remember { mutableStateOf(false) }
                    SearchFocusScrollOwner(suppressReveal = { focused }) {
                        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                            stickyHeader {
                                NuvioInputField(
                                    value = query,
                                    onValueChange = { query = it },
                                    placeholder = "Search",
                                    modifier = Modifier.testTag("search").onFocusChanged { focused = it.hasFocus },
                                )
                            }
                            items(40) { BasicText("Poster row $it", Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { scope.launch { state.scrollToItem(12, 17) } }
        compose.waitForIdle()
    }

    private fun position() = compose.runOnIdle { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }

    @Test fun clickingAndTypingInPinnedSearchPreservesPagePosition() {
        showScrolledSearch()
        val before = position()
        compose.onNodeWithTag("search").performClick().performTextInput("movie")
        compose.waitForIdle()
        assertEquals(before, position())
        compose.onNodeWithTag("search").assertTextContains("movie")
    }

    @Test fun focusingPinnedSearchStillAllowsExplicitScrollToTop() {
        showScrolledSearch()
        val before = position()
        compose.onNodeWithTag("search").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        assertEquals(before, position())
        compose.runOnIdle { scope.launch { state.animateScrollToItem(0) } }
        compose.waitForIdle()
        assertEquals(0 to 0, position())
    }
}
