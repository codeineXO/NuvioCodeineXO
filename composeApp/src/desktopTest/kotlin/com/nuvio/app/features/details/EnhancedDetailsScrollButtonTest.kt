package com.nuvio.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.BindRemotePrimaryFocusReveal
import com.nuvio.app.core.ui.rememberRemotePrimaryFocus
import com.nuvio.app.core.ui.remoteNavigationUp
import com.nuvio.app.core.ui.nuvioPosterRowMouseWheelScroll
import com.nuvio.app.core.ui.HorizontalWheelEvents
import com.nuvio.app.core.ui.LocalHorizontalWheelEvents
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.core.ui.RemoteNavigationHost
import com.nuvio.app.core.ui.remoteNavigationTarget
import com.nuvio.app.core.ui.rememberRemoteNextSectionFocus
import com.nuvio.app.features.details.components.EnhancedDetailsScrollButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class EnhancedDetailsScrollButtonTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var firstList: LazyListState
    private lateinit var secondList: LazyListState
    private lateinit var scope: CoroutineScope
    private lateinit var changeHeight: (Int) -> Unit
    private val actions = mutableListOf<Boolean>()

    private fun content(firstPageHeight: Int = 900, nextSectionHeight: Int = 300, horizontalRail: Boolean = false) {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(600.dp, 400.dp).testTag("host")) {
                    var secondPage by remember { mutableStateOf(false) }
                    var viewportHeight by remember { mutableStateOf(400) }
                    changeHeight = { viewportHeight = it }
                    firstList = rememberLazyListState()
                    secondList = rememberLazyListState()
                    scope = rememberCoroutineScope()
                    val list = if (secondPage) secondList else firstList
                    val horizontalEvents = remember { HorizontalWheelEvents() }
                    val holder = rememberSaveableStateHolder()
                    BindRemotePrimaryFocusReveal(firstList, hasPlayAction = true, beforeReveal = {
                        if (secondPage) {
                            secondPage = false
                            withFrameNanos { }
                            withFrameNanos { }
                        }
                    })
                    val focusPrimary = rememberRemotePrimaryFocus()
                    val focusFirstSection = rememberRemoteNextSectionFocus(secondList, firstItemIndex = 0)
                    Box(Modifier.fillMaxWidth().height(viewportHeight.dp).background(MaterialTheme.colorScheme.background)
                        .testTag("pages").enhancedPageMouseWheelScroll(
                            state = list,
                            secondPage = secondPage,
                            enabled = true,
                            horizontalWheelEvents = horizontalEvents,
                            onSwitch = { next ->
                                secondPage = next
                                scope.launch {
                                    withFrameNanos { }
                                    withFrameNanos { }
                                    (if (next) secondList else firstList).positionEnhancedDetailsPage(atBottom = !next)
                                }
                            },
                        )) {
                        Box(Modifier.fillMaxSize()) {
                            CompositionLocalProvider(LocalHorizontalWheelEvents provides horizontalEvents) {
                                holder.SaveableStateProvider(secondPage) {
                                    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                                        if (secondPage) {
                                            if (horizontalRail) item(key = "rail") {
                                                val railState = rememberLazyListState()
                                                LazyRow(
                                                    state = railState,
                                                    modifier = Modifier.fillMaxWidth().height(100.dp).testTag("horizontal-rail")
                                                        .nuvioPosterRowMouseWheelScroll(railState, 80f),
                                                ) {
                                                    items(10) { Spacer(Modifier.size(100.dp)) }
                                                }
                                            }
                                            item(key = "details") {
                                                Button({}, Modifier.remoteNavigationUp { focusPrimary() }.remoteNavigationTarget().testTag("first-section")) { Text("Cast") }
                                                Spacer(Modifier.fillMaxWidth().height(nextSectionHeight.dp))
                                            }
                                        } else {
                                            item(key = "episodes") {
                                                var season by rememberSaveable { mutableStateOf(1) }
                                                Button({ season++ }, Modifier.remoteNavigationTarget(initialFocus = true).testTag("season")) { Text("Season $season") }
                                                Spacer(Modifier.fillMaxWidth().height(firstPageHeight.dp).testTag("episode-cards"))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        EnhancedDetailsScrollButton(
                            secondPage = secondPage,
                            onClick = { backToTop ->
                                actions += backToTop
                                secondPage = !backToTop
                                scope.launch { (if (secondPage) secondList else firstList).scrollToItem(0) }
                            },
                            onSelect = { backToTop ->
                                actions += backToTop
                                secondPage = !backToTop
                                if (!backToTop) scope.launch {
                                    withFrameNanos { }
                                    withFrameNanos { }
                                    focusFirstSection()
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).testTag("toggle"),
                        )
                    }
                }
            }
        }
    }

    @Test fun switchingRemovesEpisodeCardsAndRestoresSelectedSeason() {
        content()
        compose.onNodeWithTag("season").performClick()
        compose.onNodeWithText("Season 2").assertIsDisplayed()
        compose.onNodeWithText("See More").performClick()
        compose.onNodeWithText("Back to Top").assertIsDisplayed()
        compose.onNodeWithTag("episode-cards").assertDoesNotExist()
        compose.onNodeWithTag("first-section").assertIsDisplayed()
        compose.onNodeWithText("Back to Top").performClick()
        compose.onNodeWithText("Season 2").assertIsDisplayed()
        compose.onNodeWithTag("first-section").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(false, true), actions) }
    }

    @Test fun scrollingPageOneDoesNotChangeButtonLabelOrFocus() {
        content()
        compose.onNodeWithTag("toggle").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { scope.launch { firstList.scrollToItem(0, 120) } }
        compose.onNodeWithText("See More").assertIsDisplayed()
        compose.onNodeWithTag("toggle").assertIsFocused()
    }

    @Test fun keyboardOpensFirstSectionAtIndexZeroAndReturnsWithButtonFocus() {
        content()
        compose.onNodeWithTag("toggle").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("first-section").assertIsFocused()
        compose.onNodeWithTag("toggle").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("See More").assertIsDisplayed()
        compose.onNodeWithTag("toggle").assertIsFocused()
    }

    @Test fun explicitPrimaryFocusRestoresPageOneBeforeFocusingPlay() {
        content()
        compose.onNodeWithText("See More").performClick()
        compose.onNodeWithTag("first-section").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("season").assertIsFocused()
        compose.onNodeWithTag("first-section").assertDoesNotExist()
        compose.onNodeWithText("See More").assertIsDisplayed()
    }

    @Test fun horizontalRailWheelDoesNotReturnToPageOne() {
        content(horizontalRail = true)
        compose.onNodeWithText("See More").performClick()
        compose.onNodeWithTag("horizontal-rail").performMouseInput { moveTo(center); scroll(-1f) }
        compose.onNodeWithText("Back to Top").assertIsDisplayed()
        compose.onNodeWithTag("episode-cards").assertDoesNotExist()
    }

    @Test fun shortSecondPageAndWindowResizeNeverRevealEpisodeCards() {
        content(firstPageHeight = 400, nextSectionHeight = 20)
        compose.onNodeWithText("See More").performClick()
        compose.runOnIdle { changeHeight(250) }
        compose.onNodeWithText("Back to Top").assertIsDisplayed()
        compose.onNodeWithTag("episode-cards").assertDoesNotExist()
        compose.runOnIdle { changeHeight(400) }
        compose.onNodeWithTag("episode-cards").assertDoesNotExist()
    }

    @Test fun wheelSwitchesAtBottomOfTallFirstPage() {
        content()
        compose.runOnIdle { scope.launch { firstList.scrollToItem(0, 10000) } }
        compose.waitForIdle()
        compose.onNodeWithTag("pages").performMouseInput { scroll(1f) }
        compose.onNodeWithText("Back to Top").assertIsDisplayed()
        compose.onNodeWithTag("episode-cards").assertDoesNotExist()
    }

    @Test fun wheelUpAtSecondPageTopReturnsToFirstPage() {
        content()
        compose.onNodeWithText("See More").performClick()
        compose.onNodeWithTag("pages").performMouseInput { scroll(-1f) }
        compose.onNodeWithText("See More").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(firstList.canScrollForward)
            assertTrue(firstList.firstVisibleItemScrollOffset > 0)
        }
    }
}
