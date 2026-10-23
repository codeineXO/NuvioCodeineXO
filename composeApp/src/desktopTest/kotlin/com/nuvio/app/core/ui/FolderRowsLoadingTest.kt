package com.nuvio.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.collection.*
import com.nuvio.app.features.home.HomeCatalogSettingsUiState
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

class FolderRowsLoadingTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var list: LazyListState
    private lateinit var scope: CoroutineScope
    private lateinit var session: RemoteNavigationSession
    private var state by mutableStateOf(initialState())
    private var details by mutableStateOf(false)

    private fun content() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, restorationKey = if (details) "details" else "folder",
                    modifier = Modifier.size(900.dp, 600.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    list = rememberLazyListState()
                    scope = rememberCoroutineScope()
                    if (details) BasicText("Back", Modifier.remoteNavigationTarget().clickable { details = false })
                    else RowsContent(state, emptySet(), homeCatalogSettings = HomeCatalogSettingsUiState(),
                        onCatalogClick = {}, onPosterClick = { details = true },
                        slots = folderRowSlots(state, "collection"), listState = list)
                }
            }
        }
    }

    private fun complete(index: Int, empty: Boolean = false, error: String? = null) {
        compose.runOnIdle {
            val tabs = state.tabs.mapIndexed { i, tab ->
                if (i != index) tab else tab.copy(isLoading = false, error = error,
                    items = if (empty || error != null) emptyList() else (0..7).map {
                        MetaPreview("$index-$it", "movie", "Poster $index-$it")
                    })
            }
            state = state.copy(tabs = tabs, isLoading = tabs.any { it.isLoading })
        }
        compose.waitForIdle()
    }

    private fun position() = compose.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
    private fun rowKeys() = compose.runOnIdle { session.posterPages.getValue("collection").rows.map { it.key } }

    @Test fun laterSourceFinishingFirstDoesNotMoveOpeningPositionOrRekeyDuplicateTitles() {
        content()
        val keys = rowKeys()
        assertEquals(3, keys.distinct().size)
        val firstHeight = compose.runOnIdle { list.layoutInfo.visibleItemsInfo.first().size }
        complete(2)
        assertEquals(0 to 0, position())
        complete(1, error = "Source unavailable")
        complete(0)
        assertEquals(keys, rowKeys())
        assertEquals(0 to 0, position())
        compose.runOnIdle {
            assertEquals(firstHeight, list.layoutInfo.visibleItemsInfo.first().size)
            assertEquals(listOf(8, 0, 8), session.posterPages.getValue("collection").rows.map { it.items.size })
        }
    }

    @Test fun completingEmptyAndFailedRowsAboveUserScrollPreservesPosition() {
        content()
        compose.runOnIdle { scope.launch { list.scrollToItem(2, 19) } }
        compose.waitForIdle()
        val before = position()
        assertTrue(before.first > 0)
        complete(2)
        complete(0, empty = true)
        complete(1, error = "Source unavailable")
        assertEquals(before, position())
        val lastKey = rowKeys().last()
        compose.runOnIdle {
            assertEquals(listOf(0, 0, 8), session.posterPages.getValue("collection").rows.map { it.items.size })
            assertTrue(session.targets.filter { it.poster != null }.all { it.poster!!.row == lastKey })
        }
    }

    @Test fun keyboardSkipsEmptyRowsAndDetailsBackRestoresPosterAndScroll() {
        content()
        complete(2)
        complete(0)
        complete(1, empty = true)
        compose.onNode(hasText("Poster 0-0") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("movie:2-0", session.focusedTarget?.poster?.content) }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Back").assertExists()
        val origin = compose.runOnIdle { session.posterPages.getValue("collection").origin!! }
        compose.runOnIdle { details = false }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("movie:2-0", session.focusedTarget?.poster?.content)
            assertEquals(origin.pageIndex, list.firstVisibleItemIndex)
            assertTrue(kotlin.math.abs(origin.pageOffset - list.firstVisibleItemScrollOffset) <= 1)
        }
    }

    companion object {
        private fun initialState() = FolderDetailUiState(
            folder = CollectionFolder("folder", "Folder"), collectionTitle = "Collection",
            viewMode = FolderViewMode.ROWS,
            tabs = (0..2).map { FolderTab(label = "Duplicate title", manifestUrl = "https://example.test/manifest.json",
                catalogId = "source-$it", type = "movie") },
        )
    }
}
