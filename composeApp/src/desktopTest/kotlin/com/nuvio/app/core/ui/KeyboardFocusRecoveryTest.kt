package com.nuvio.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

class KeyboardFocusRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var session: RemoteNavigationSession
    private lateinit var list: LazyListState
    private lateinit var scope: CoroutineScope
    private var details by mutableStateOf(false)
    private var available by mutableStateOf(true)
    private var loading by mutableStateOf(false)
    private var unfocusable by mutableStateOf(false)
    private var originalAvailable by mutableStateOf(true)
    private var rowsAvailable by mutableStateOf(true)

    private fun content(pageId: String = "home", hero: Boolean = false, posterCount: Int = 20, posterHeight: Int = 100) {
        compose.setContent {
            list = rememberLazyListState()
            scope = rememberCoroutineScope()
            NuvioTheme {
                RemoteNavigationHost(true, restorationKey = if (details) "details" else "home",
                    modifier = Modifier.size(500.dp, 400.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    if (details) {
                        RemoteNavigationEntry("details", true) {
                            BasicText("Details", Modifier.remoteNavigationTarget().clickable {})
                        }
                    } else {
                        RemoteNavigationEntry("home", true) {
                            BindPosterNavigationPage(pageId, Unit,
                                (0 until if (rowsAvailable) posterCount else 0).map { PosterNavigationRow(it, listOf("poster$it"), it + 1) }, list, loading)
                            NuvioScreen(listState = list, topPadding = 0.dp, horizontalPadding = 0.dp,
                                verticalArrangement = Arrangement.Top, modifier = Modifier.testTag("viewport")) {
                                item { BasicText("Retry", Modifier.height(40.dp).remoteNavigationTarget().clickable {}) }
                                items(if (rowsAvailable) posterCount else 0) { index ->
                                    RemotePosterRow(pageId, index) {
                                        RemotePosterItem("poster$index", 0) {
                                            val action = if (available && (index != 0 || originalAvailable)) Modifier.posterCardClickable(
                                                onClick = { details = true }, onLongClick = null) else Modifier
                                            Column(if (hero && index == 0) Modifier.remoteNavigationScrollToTop(list) else Modifier) {
                                                if (hero && index == 0) Spacer(Modifier.height(280.dp))
                                                BasicText("Poster $index", Modifier.size(200.dp, posterHeight.dp)
                                                    .testTag("poster$index").focusProperties { canFocus = !unfocusable }.then(action))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun arrow(key: Key = Key.DirectionDown) {
        compose.onNodeWithTag("host").performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun openDetails() {
        compose.onNodeWithTag("poster0").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("poster0").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Details").assertExists()
    }

    @Test fun failedPosterReturnReleasesPendingRestorationAndFocusesRetry() {
        content()
        openDetails()
        compose.runOnIdle { available = false; details = false }
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()
        compose.waitUntil(7_000) { !session.posterRestorationPending("home") }
        compose.onNodeWithText("Retry").assertIsFocused()
        compose.runOnIdle { assertNull(session.posterPages.getValue("home").origin) }
    }

    @Test fun firstArrowAfterWheelScrollSelectsVisiblePosterWithoutMovingPage() {
        content()
        compose.onNodeWithTag("poster0").performSemanticsAction(SemanticsActions.RequestFocus)
        arrow(Key.DirectionRight)
        compose.onNodeWithTag("viewport").performMouseInput { scroll(600f) }
        compose.waitForIdle()
        val position = compose.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        assertTrue(position.first > 1, "Wheel must scroll past the old focus")
        arrow()
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
            val bounds = session.focusedTarget?.bounds ?: error("No visible entry selected")
            assertTrue(bounds.top >= 0 && bounds.bottom <= 400 * compose.density.density)
            assertNotNull(session.focusedTarget?.poster)
        }
        val selected = compose.runOnIdle { session.focusedTarget!!.poster!!.row as Int }
        arrow()
        compose.runOnIdle { assertEquals(selected + 1, session.focusedTarget?.poster?.row) }
    }

    @Test fun mouseModeListMovementAlsoReanchorsKeyboardOnAnotherPosterPage() {
        content("collection")
        compose.onNodeWithTag("poster0").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { session.keyboard = false; scope.launch { list.scrollToItem(8) } }
        compose.waitForIdle()
        val position = compose.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        arrow(Key.DirectionLeft)
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
            assertEquals(7, session.focusedTarget?.poster?.row)
        }
    }

    @Test fun delayedPosterTargetCompletesReturnBeforeDeadline() {
        content()
        openDetails()
        compose.runOnIdle { available = false; details = false }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { available = true }
        compose.onNodeWithTag("poster0").assertIsFocused()
        compose.runOnIdle {
            assertFalse(session.posterRestorationPending("home"))
            assertNull(session.posterPages.getValue("home").origin)
        }
    }

    @Test fun rejectedFocusRequestFallsBackToRetry() {
        content()
        openDetails()
        compose.runOnIdle { unfocusable = true; details = false }
        compose.waitUntil(7_000) { !session.posterRestorationPending("home") }
        compose.onNodeWithText("Retry").assertIsFocused()
    }

    @Test fun cachedPosterRestoresDespiteHomeStillLoading() {
        content()
        openDetails()
        compose.runOnIdle { loading = true; details = false }
        compose.waitForIdle()
        compose.onNodeWithTag("poster0").assertIsFocused()
        compose.runOnIdle {
            assertTrue(loading)
            assertFalse(session.posterRestorationPending("home"))
            assertNull(session.posterPages.getValue("home").origin)
        }
        arrow()
        compose.onNodeWithTag("poster1").assertIsFocused()
    }

    @Test fun missingTargetReleasesReturnWithinShortBudget() {
        content()
        openDetails()
        compose.runOnIdle { loading = true; available = false; details = false }
        compose.waitForIdle()
        compose.waitUntil(1_000) { !session.posterRestorationPending("home") }
        compose.onNodeWithText("Retry").assertIsFocused()
        compose.runOnIdle { loading = false; available = true }
        arrow()
        compose.runOnIdle { assertNotNull(session.focusedTarget?.poster) }
    }

    @Test fun loadingWithoutRowsDoesNotReserveKeyboardForFiveSeconds() {
        content()
        openDetails()
        compose.runOnIdle { loading = true; rowsAvailable = false; details = false }
        compose.waitForIdle()
        compose.waitUntil(1_000) { !session.posterRestorationPending("home") }
        compose.onNodeWithText("Retry").assertIsFocused()
        compose.runOnIdle { rowsAvailable = true; loading = false }
        compose.waitForIdle()
        compose.onNodeWithText("Retry").assertIsFocused()
        arrow()
        compose.runOnIdle { assertNotNull(session.focusedTarget?.poster) }
    }

    @Test fun arrowInterruptsReturnAndLateOriginalCannotStealFocus() {
        content()
        openDetails()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { originalAvailable = false; loading = true; details = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue(session.posterRestorationPending("home")) }
        arrow()
        compose.runOnIdle {
            assertFalse(session.posterRestorationPending("home"))
            assertNull(session.posterPages.getValue("home").origin)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("poster1").assertIsFocused()
        arrow()
        compose.onNodeWithTag("poster2").assertIsFocused()
        val position = compose.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        compose.runOnIdle { originalAvailable = true; loading = false }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("poster2").assertIsFocused()
        compose.runOnIdle { assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset) }
    }

    @Test fun desktopRecoveryInterruptsPendingReturnImmediately() {
        content()
        openDetails()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { originalAvailable = false; details = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertTrue(session.posterRestorationPending("home"))
            assertTrue(RemoteNavigationDispatcher.recoverFocus())
            assertFalse(session.posterRestorationPending("home"))
            assertNull(session.posterPages.getValue("home").origin)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("poster1").assertIsFocused()
        arrow()
        compose.onNodeWithTag("poster2").assertIsFocused()
    }

    @Test fun leavingDuringReturnPreservesBookmarkAndCannotStealDetailsFocus() {
        content()
        openDetails()
        val bookmark = compose.runOnIdle { session.posterPages.getValue("home").origin }
        compose.runOnIdle { loading = true; originalAvailable = false; details = false }
        compose.waitForIdle()
        compose.runOnIdle { details = true }
        compose.onNodeWithText("Details").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { loading = false; originalAvailable = true }
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithText("Details").assertIsFocused()
        compose.runOnIdle { assertSame(bookmark, session.posterPages.getValue("home").origin); details = false }
        compose.onNodeWithTag("poster0").assertIsFocused()
    }

    @Test fun wheelDuringReturnCancelsBookmarkAndKeepsUserViewport() {
        content()
        openDetails()
        compose.runOnIdle { loading = true; originalAvailable = false; details = false }
        compose.waitForIdle()
        compose.onNodeWithTag("viewport").performMouseInput { scroll(600f) }
        compose.waitForIdle()
        val position = compose.runOnIdle {
            assertFalse(session.posterRestorationPending("home"))
            assertNull(session.posterPages.getValue("home").origin)
            list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
        }
        compose.runOnIdle { loading = false; originalAvailable = true }
        arrow()
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset) }
    }

    @Test fun visibleHeroEntryDoesNotRunDelayedScrollToTop() {
        content(hero = true)
        compose.onNodeWithTag("poster0").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { session.keyboard = false; scope.launch { list.scrollToItem(1, 50) } }
        compose.waitForIdle()
        val position = compose.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        arrow(Key.DirectionRight)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("poster0").assertIsFocused()
        compose.runOnIdle { assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset) }
    }

    @Test fun partiallyVisibleEntryDoesNotTriggerNativeFocusScrolling() {
        content(posterCount = 1, posterHeight = 500)
        compose.runOnIdle { scope.launch { list.scrollToItem(1, 80) } }
        compose.waitForIdle()
        val position = compose.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        arrow()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("poster0").assertIsFocused()
        compose.runOnIdle { assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset) }
    }

    @Test fun desktopRecoveryUsesViewportBeforeOldFocusHistory() {
        content()
        compose.onNodeWithTag("poster0").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { session.keyboard = false; scope.launch { list.scrollToItem(8) } }
        compose.waitForIdle()
        val position = compose.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        compose.runOnIdle { assertTrue(RemoteNavigationDispatcher.recoverFocus()) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertEquals(7, session.focusedTarget?.poster?.row)
            assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
        }
    }

    @Test fun draggingScrollbarReanchorsFirstArrow() {
        content()
        compose.onNodeWithTag("poster0").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("viewport").performMouseInput {
            moveTo(Offset(center.x * 2 - 6, 20f))
            press()
            moveTo(Offset(center.x * 2 - 6, center.y + 60))
            release()
        }
        compose.waitForIdle()
        val position = compose.runOnIdle { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        assertTrue(position.first > 1, "Drag must move the scrollbar thumb")
        arrow()
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertEquals(position, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
            assertTrue((session.focusedTarget?.poster?.row as Int) >= position.first - 1)
        }
    }

    @Test fun newerPosterVisitSurvivesCleanupOfCancelledReturn() {
        content()
        openDetails()
        compose.runOnIdle { loading = true; originalAvailable = false; details = false }
        compose.waitForIdle()
        compose.onNodeWithTag("poster2").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("poster2").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Details").assertExists()
        compose.runOnIdle {
            assertEquals("poster2", session.posterPages.getValue("home").origin?.content)
            loading = false
            details = false
        }
        compose.onNodeWithTag("poster2").assertIsFocused()
        compose.runOnIdle { assertNull(session.posterPages.getValue("home").origin) }
    }

    @Test fun mouseMovementWithoutScrollingPreservesExistingFocus() {
        content()
        compose.onNodeWithTag("poster1").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { RemoteNavigationDispatcher.mouseMoved() }
        compose.runOnIdle {
            assertTrue(session.pendingViewportRoutes.isEmpty())
            assertFalse(RemoteNavigationDispatcher.recoverFocus())
        }
        compose.onNodeWithTag("poster1").assertIsFocused()
    }

    @Test fun viewportEntryRespectsHorizontallyScrolledShelf() {
        compose.setContent {
            list = rememberLazyListState()
            scope = rememberCoroutineScope()
            NuvioTheme {
                RemoteNavigationHost(true, restorationKey = "home", modifier = Modifier.size(500.dp, 400.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    BindPosterNavigationPage("home", Unit, listOf(PosterNavigationRow("row", (0..12).toList(), 0)), list, false)
                    NuvioScreen(listState = list, topPadding = 0.dp) {
                        item {
                            RemotePosterRow("home", "row") {
                                NuvioShelfSection("Movies", (0..12).toList(), key = { it }) { index ->
                                    BasicText("Horizontal $index", Modifier.size(150.dp, 100.dp).testTag("horizontal$index")
                                        .posterCardClickable(onClick = {}, onLongClick = null))
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("horizontal0").performSemanticsAction(SemanticsActions.RequestFocus)
        val shelf = compose.runOnIdle { session.posterPages.getValue("home").shelves.getValue("row") }
        compose.runOnIdle { session.keyboard = false; scope.launch { shelf.scrollToItem(5) } }
        compose.waitForIdle()
        val position = compose.runOnIdle { shelf.firstVisibleItemIndex to shelf.firstVisibleItemScrollOffset }
        arrow(Key.DirectionRight)
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle {
            assertTrue(session.focusedTarget!!.poster!!.index >= 5)
            assertEquals(position, shelf.firstVisibleItemIndex to shelf.firstVisibleItemScrollOffset)
        }
    }

    @Test fun excludedStreamBackIsSkippedByArrowsTabsAndRecoveryButStillClicks() {
        var backs = 0
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(500.dp, 400.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    PlatformBackHandler(true) { backs++ }
                    Column {
                        NuvioBackButton(onClick = { backs++ }, keyboardNavigationEnabled = false,
                            usePageStyle = true, modifier = Modifier.testTag("back"))
                        repeat(2) { index ->
                            BasicText("Source $index", Modifier.size(200.dp, 50.dp).testTag("source$index")
                                .remoteNavigationTarget(role = RemoteNavigationRole.StreamSource).clickable {})
                        }
                    }
                }
            }
        }
        arrow()
        compose.onNodeWithTag("source0").assertIsFocused()
        arrow(Key.DirectionUp)
        compose.onNodeWithTag("back").assertIsNotFocused()
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.Tab); keyDown(Key.ShiftLeft); pressKey(Key.Tab); keyUp(Key.ShiftLeft) }
        compose.onNodeWithTag("back").assertIsNotFocused()
        compose.runOnIdle {
            assertTrue(RemoteNavigationDispatcher.recoverFocus(force = true))
            assertTrue(session.targets.none { it.role == RemoteNavigationRole.Back })
        }
        compose.onNodeWithTag("back").performSemanticsAction(SemanticsActions.RequestFocus) { assertFalse(it()) }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.Backspace) }
        compose.runOnIdle { assertEquals(1, backs) }
        compose.onNodeWithTag("back").performMouseInput { click() }
        compose.runOnIdle { assertEquals(2, backs) }
    }
}
