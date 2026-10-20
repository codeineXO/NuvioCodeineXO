package com.nuvio.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.RemoteNavigationHost
import com.nuvio.app.features.details.components.EnhancedDetailsScrollButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnhancedDetailsScrollButtonTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var list: LazyListState
    private lateinit var scope: CoroutineScope
    private val actions = mutableListOf<Boolean>()

    private fun content(firstPageHeight: Int = 900, nextSectionHeight: Int = 300) {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(600.dp, 400.dp).testTag("host")) {
                    list = rememberLazyListState()
                    scope = rememberCoroutineScope()
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                            item(key = "first-page") { Spacer(Modifier.fillMaxWidth().height(firstPageHeight.dp)) }
                            item(key = "next-section") { Spacer(Modifier.fillMaxWidth().height(nextSectionHeight.dp)) }
                        }
                        EnhancedDetailsScrollButton(
                            listState = list,
                            onClick = { backToTop ->
                                actions += backToTop
                                scope.launch { list.animateScrollToItem(if (backToTop) 0 else 1) }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).testTag("toggle"),
                        )
                    }
                }
            }
        }
    }

    @Test fun seeMoreChangesToBackToTopEvenWhenTallFirstPageRemainsVisible() {
        content()
        compose.onNodeWithText("See More").assertIsDisplayed()
        savePreview("see-more")
        compose.onNodeWithText("See More").performClick()
        compose.onNodeWithText("Back to Top").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, list.firstVisibleItemIndex)
            assertTrue(list.firstVisibleItemScrollOffset > 0)
        }
        savePreview("back-to-top")
        compose.onNodeWithText("Back to Top").performClick()
        compose.onNodeWithText("See More").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0 to 0, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
            assertEquals(listOf(false, true), actions)
        }
    }

    @Test fun manualScrollingUpdatesTheLabelWithoutChangingFocus() {
        content()
        compose.onNodeWithTag("toggle").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { scope.launch { list.scrollToItem(0, 120) } }
        compose.onNodeWithText("Back to Top").assertIsDisplayed()
        compose.onNodeWithTag("toggle").assertIsFocused()
        compose.runOnIdle { scope.launch { list.scrollToItem(0) } }
        compose.onNodeWithText("See More").assertIsDisplayed()
        compose.onNodeWithTag("toggle").assertIsFocused()
    }

    @Test fun keyboardActivationUsesTheUpdatedActionAndRetainsFocus() {
        content()
        compose.onNodeWithTag("toggle").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Back to Top").assertIsDisplayed()
        compose.onNodeWithTag("toggle").assertIsFocused()
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("See More").assertIsDisplayed()
        compose.onNodeWithTag("toggle").assertIsFocused()
        compose.runOnIdle { assertEquals(listOf(false, true), actions) }
    }

    @Test fun shortFollowingSectionStillSwitchesLabelWhenScrollClamps() {
        content(firstPageHeight = 400, nextSectionHeight = 48)
        compose.onNodeWithText("See More").performClick()
        compose.onNodeWithText("Back to Top").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, list.firstVisibleItemIndex)
            assertTrue(list.firstVisibleItemScrollOffset > 0)
        }
    }

    private fun savePreview(name: String) {
        val directory = File("build/reports/enhanced-details-scroll-button").apply { mkdirs() }
        ImageIO.write(compose.onNodeWithTag("toggle").captureToImage().toAwtImage(), "png", File(directory, "$name.png"))
    }
}
