package com.nuvio.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.components.HomeCollectionRowSection
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CollectionKeyboardNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeCollectionFoldersParticipateInPosterNavigationAndSkipTitles() {
        lateinit var session: RemoteNavigationSession
        val collection = Collection("test", "Collection", folders = listOf(
            CollectionFolder("a", "Folder A"), CollectionFolder("b", "Folder B"),
        ))
        var opened: String? = null
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(800.dp, 700.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    val state = rememberLazyListState()
                    BindPosterNavigationPage("home", Unit, listOf(
                        PosterNavigationRow("before", listOf("before"), 0),
                        PosterNavigationRow("folders", collection.folders.map { "collection_test_folder_${it.id}" }, 1),
                        PosterNavigationRow("after", listOf("after"), 2),
                    ), state, false)
                    LazyColumn(state = state) {
                        item {
                            RemotePosterRow("home", "before") {
                                RemotePosterItem("before", 0) {
                                    BasicText("Before", Modifier.size(100.dp).testTag("before")
                                        .posterCardClickable(onClick = {}, onLongClick = null))
                                }
                            }
                        }
                        item {
                            RemotePosterRow("home", "folders") {
                                HomeCollectionRowSection(collection, sectionPadding = 24.dp,
                                    animateGifs = false, onFolderClick = { _, folder -> opened = folder },
                                    onViewAllClick = {})
                            }
                        }
                        item {
                            RemotePosterRow("home", "after") {
                                RemotePosterItem("after", 0) {
                                    BasicText("After", Modifier.size(100.dp).testTag("after")
                                        .posterCardClickable(onClick = {}, onLongClick = null))
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("before").performSemanticsAction(SemanticsActions.RequestFocus)
        fun key(key: Key) {
            compose.onNodeWithTag("host").performKeyInput { pressKey(key) }
            compose.waitForIdle()
        }
        key(Key.DirectionDown)
        compose.runOnIdle { assertEquals("collection_test_folder_a", session.focusedTarget?.poster?.content) }
        key(Key.DirectionRight)
        compose.runOnIdle { assertEquals("collection_test_folder_b", session.focusedTarget?.poster?.content) }
        key(Key.Enter)
        compose.runOnIdle { assertEquals("b", opened) }
        key(Key.DirectionDown)
        compose.onNodeWithTag("after").assertIsFocused()
        key(Key.DirectionUp)
        compose.runOnIdle { assertEquals("collection_test_folder_b", session.focusedTarget?.poster?.content) }
    }

    @Test fun excludedBackButtonStillAcceptsMouseClicks() {
        var backs = 0
        var backLabel = ""
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true) {
                    backLabel = stringResource(Res.string.action_back)
                    NuvioScreenHeader("Collection", onBack = { backs++ }, backButtonKeyboardNavigationEnabled = false)
                }
            }
        }
        val back = compose.onNodeWithContentDescription(backLabel)
        back.performSemanticsAction(SemanticsActions.RequestFocus) { assertFalse(it()) }
        back.performMouseInput { click() }
        compose.runOnIdle { assertEquals(1, backs) }
    }
}
