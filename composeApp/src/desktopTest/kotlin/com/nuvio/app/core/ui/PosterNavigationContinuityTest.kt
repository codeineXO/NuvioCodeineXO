package com.nuvio.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.nuvio.app.AppScreenTab
import com.nuvio.app.RootTabHost
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.components.DetailSeriesContent
import com.nuvio.app.navigation.DetailRoute
import com.nuvio.app.navigation.NuvioNavigator
import com.nuvio.app.navigation.TabsRoute
import org.junit.Rule
import org.junit.Test
import kotlin.test.*
import kotlin.math.abs

class PosterNavigationContinuityTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var session: RemoteNavigationSession

    private fun focus(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun key(key: Key) { compose.onNodeWithTag("host").performKeyInput { pressKey(key) }; compose.waitForIdle() }

    @Composable private fun Shelves(
        entries: List<List<String>>, context: Any = Unit, loading: Boolean = false,
        onOpen: () -> Unit = {}, arrow: Boolean = false,
        posterWidths: Map<String, Int> = emptyMap(),
    ) {
        val state = rememberLazyListState()
        BindPosterNavigationPage("test", context, entries.mapIndexed { i, items -> PosterNavigationRow("row$i", items, i) }, state, loading)
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (entries.isEmpty()) item {
                BasicText("Retry", Modifier.testTag("retry").remoteNavigationTarget().clickable {})
            }
            entries.forEachIndexed { row, items ->
                item(key = "row$row") {
                    RemotePosterRow("test", "row$row") {
                        NuvioShelfSection("Title $row", items, key = { it }, onViewAllClick = onOpen.takeIf { arrow }, onTitleClick = onOpen) { id ->
                            BasicText(id, Modifier.size((posterWidths[id] ?: 100).dp, 100.dp).testTag("$row-$id")
                                .posterCardClickable(onClick = onOpen, onLongClick = null))
                        }
                    }
                }
            }
        }
    }

    private fun shelves(entries: List<List<String>>, arrow: Boolean = false, onOpen: () -> Unit = {}, viewportWidth: Int = 500, posterWidths: Map<String, Int> = emptyMap()) {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(viewportWidth.dp, 600.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    Shelves(entries, arrow = arrow, onOpen = onOpen, posterWidths = posterWidths)
                }
            }
        }
    }

    @Test fun verticalLaneSurvivesShortRowsAndSkipsHeaders() {
        shelves(listOf(listOf("a", "b", "c", "d"), listOf("a"), listOf("a", "b", "c", "d")))
        focus("0-d")
        key(Key.DirectionDown)
        compose.onNodeWithTag("1-a").assertIsFocused().assertIsDisplayed()
        key(Key.DirectionDown)
        compose.onNodeWithTag("2-d").assertIsFocused().assertIsDisplayed()
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        compose.onNodeWithTag("0-d").assertIsFocused()
        key(Key.DirectionLeft)
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        compose.onNodeWithTag("2-c").assertIsFocused()
    }

    @Test fun rightTraversesLazyShelfThenStopsAtExistingArrowAndLeftReturns() {
        var opens = 0
        shelves(listOf((0..12).map(Int::toString), listOf("other")), arrow = true, onOpen = { opens++ })
        focus("0-0")
        repeat(13) { key(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(PosterTargetRole.EndAction, session.focusedTarget?.poster?.role) }
        compose.onNodeWithTag("host").performKeyInput {
            keyDown(Key.DirectionRight)
            advanceEventTime(2000)
            keyUp(Key.DirectionRight)
        }
        compose.runOnIdle { assertEquals(PosterTargetRole.EndAction, session.focusedTarget?.poster?.role) }
        key(Key.Enter)
        compose.runOnIdle { assertEquals(1, opens) }
        key(Key.DirectionLeft)
        compose.onNodeWithTag("0-12").assertIsFocused().assertIsDisplayed()
    }

    @Test fun rowWithoutArrowStopsAtItsLastPoster() {
        shelves(listOf(listOf("a", "b"), listOf("c")))
        focus("0-b")
        repeat(5) { key(Key.DirectionRight) }
        compose.onNodeWithTag("0-b").assertIsFocused()
    }

    private fun assertFocusedAndFollowingVisible(index: Int) {
        compose.onNodeWithTag("0-$index").assertIsFocused()
        compose.runOnIdle {
            val layout = session.posterPages.getValue("test").shelves.getValue("row0").layoutInfo
            listOf(index, index + 1).forEach { position ->
                val item = layout.visibleItemsInfo.single { it.index == position }
                assertTrue(item.offset >= layout.viewportStartOffset, "Poster $position clipped on left")
                assertTrue(item.offset + item.size <= layout.viewportEndOffset, "Poster $position clipped on right")
            }
        }
    }

    @Test fun rightScrollsEarlyEnoughToLeaveOneAdditionalPosterFullyVisible() {
        shelves(listOf((0..12).map(Int::toString)))
        focus("0-0")
        repeat(2) { key(Key.DirectionRight) }
        compose.runOnIdle {
            val shelf = session.posterPages.getValue("test").shelves.getValue("row0")
            assertEquals(0 to 0, shelf.firstVisibleItemIndex to shelf.firstVisibleItemScrollOffset)
        }
        key(Key.DirectionRight)
        assertFocusedAndFollowingVisible(3)
        repeat(3) { step ->
            key(Key.DirectionRight)
            assertFocusedAndFollowingVisible(4 + step)
        }
    }

    @Test fun rightLookAheadUsesMeasuredWidthsForLazyPosters() {
        shelves(listOf((0..12).map(Int::toString)), posterWidths = mapOf("4" to 140, "5" to 80, "6" to 160))
        focus("0-3")
        key(Key.DirectionRight)
        assertFocusedAndFollowingVisible(4)
        key(Key.DirectionRight)
        assertFocusedAndFollowingVisible(5)
    }

    @Test fun narrowShelfKeepsFocusedPosterVisibleWhenTwoPostersCannotFit() {
        shelves(listOf((0..12).map(Int::toString)), viewportWidth = 160)
        focus("0-0")
        repeat(3) { index ->
            key(Key.DirectionRight)
            compose.onNodeWithTag("0-${index + 1}").assertIsFocused().assertIsDisplayed()
            compose.runOnIdle {
                val layout = session.posterPages.getValue("test").shelves.getValue("row0").layoutInfo
                val item = layout.visibleItemsInfo.single { it.index == index + 1 }
                assertTrue(item.offset >= layout.viewportStartOffset)
                assertTrue(item.offset + item.size <= layout.viewportEndOffset)
            }
        }
    }

    private fun outlinePixel(tag: String): androidx.compose.ui.graphics.Color {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, 1]
    }

    @Test fun posterOutlineTransfersAtFullOpacityWithoutFading() {
        shelves(listOf(listOf("a", "b", "c")))
        focus("0-a")
        compose.runOnIdle { session.keyboard = true }
        compose.waitForIdle()
        val solidOutline = outlinePixel("0-a")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("0-b").assertIsFocused()
        assertNotEquals(solidOutline, outlinePixel("0-a"))
        assertEquals(solidOutline, outlinePixel("0-b"))
        compose.mainClock.advanceTimeBy(220)
        assertEquals(solidOutline, outlinePixel("0-b"))
        compose.mainClock.autoAdvance = true
    }

    @Test fun heldHorizontalScrollingIsFasterAndSinglePressSpeedReturnsOnRelease() {
        shelves(listOf((0..20).map(Int::toString)))
        focus("0-3")
        compose.runOnIdle { session.keyboard = true }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            val source = session.focusedTarget!!
            session.enqueuePosterMove(source, FocusDirection.Right)
            session.enqueuePosterMove(source, FocusDirection.Right)
        }
        compose.mainClock.advanceTimeBy(112)
        compose.runOnIdle {
            val layout = session.posterPages.getValue("test").shelves.getValue("row0").layoutInfo
            val card = layout.visibleItemsInfo.single { it.index == 5 }
            assertEquals(layout.viewportEndOffset, card.offset + card.size)
            assertTrue(session.fastPosterScroll)
            session.releasePosterDirection(FocusDirection.Right)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("0-4").assertIsFocused()
        compose.runOnIdle {
            assertFalse(session.fastPosterScroll)
            assertNull(session.posterRepeatingDirection)
            session.enqueuePosterMove(session.focusedTarget!!, FocusDirection.Right)
            session.releasePosterDirection(FocusDirection.Right)
        }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            val layout = session.posterPages.getValue("test").shelves.getValue("row0").layoutInfo
            val card = layout.visibleItemsInfo.single { it.index == 6 }
            assertTrue(card.offset + card.size > layout.viewportEndOffset)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("0-5").assertIsFocused()
        compose.mainClock.autoAdvance = true
    }

    @Test fun heldVerticalScrollingCentersRowsFasterWithoutDrainingRepeatsAfterRelease() {
        shelves(List(8) { listOf("a", "b") })
        focus("2-a")
        compose.runOnIdle { session.keyboard = true }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            val source = session.focusedTarget!!
            session.enqueuePosterMove(source, FocusDirection.Down)
            session.enqueuePosterMove(source, FocusDirection.Down)
        }
        compose.mainClock.advanceTimeBy(112)
        assertRowCentered(3)
        compose.runOnIdle { session.releasePosterDirection(FocusDirection.Down) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("3-a").assertIsFocused()
        compose.runOnIdle {
            assertFalse(session.fastPosterScroll)
            assertNull(session.pendingPosterMove)
            session.enqueuePosterMove(session.focusedTarget!!, FocusDirection.Down)
            session.releasePosterDirection(FocusDirection.Down)
        }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            val layout = session.posterPages.getValue("test").state!!.layoutInfo
            val row = layout.visibleItemsInfo.single { it.index == 4 }
            assertTrue(row.offset + row.size / 2f - (layout.viewportStartOffset + layout.viewportEndOffset) / 2f > 1f)
        }
        compose.mainClock.advanceTimeBy(500)
        assertRowCentered(4)
        compose.mainClock.autoAdvance = true
    }

    @Test fun holdRepeatAcceleratesAnAlreadyRunningScroll() {
        shelves(listOf((0..20).map(Int::toString)))
        focus("0-3")
        compose.runOnIdle { session.keyboard = true }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val source = compose.runOnIdle {
            session.focusedTarget!!.also { session.enqueuePosterMove(it, FocusDirection.Right) }
        }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { session.enqueuePosterMove(source, FocusDirection.Right) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            val layout = session.posterPages.getValue("test").shelves.getValue("row0").layoutInfo
            val card = layout.visibleItemsInfo.single { it.index == 5 }
            assertEquals(layout.viewportEndOffset, card.offset + card.size)
            session.releasePosterDirection(FocusDirection.Right)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("0-4").assertIsFocused()
        compose.mainClock.autoAdvance = true
    }

    @Test fun horizontalLookAheadAnimatesAndSettlesWithFollowingPosterAtEdge() {
        shelves(listOf((0..20).map(Int::toString)))
        focus("0-3")
        compose.runOnIdle { session.keyboard = true }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            val shelf = session.posterPages.getValue("test").shelves.getValue("row0")
            val layout = shelf.layoutInfo
            val following = layout.visibleItemsInfo.single { it.index == 5 }
            val remaining = following.offset + following.size - layout.viewportEndOffset
            assertTrue(remaining in 1..149, "Look-ahead reveal jumped: $remaining remaining")
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("0-4").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle {
            val layout = session.posterPages.getValue("test").shelves.getValue("row0").layoutInfo
            val card = layout.visibleItemsInfo.single { it.index == 5 }
            assertEquals(layout.viewportEndOffset, card.offset + card.size)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun verticalCenteringAnimatesRatherThanJumpingToFinalPosition() {
        shelves(List(8) { listOf("a", "b") })
        focus("2-a")
        compose.runOnIdle { session.keyboard = true }
        compose.waitForIdle()
        val initial = compose.runOnIdle {
            val layout = session.posterPages.getValue("test").state!!.layoutInfo
            val row = layout.visibleItemsInfo.single { it.index == 3 }
            row.offset + row.size / 2f - (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            val layout = session.posterPages.getValue("test").state!!.layoutInfo
            val row = layout.visibleItemsInfo.single { it.index == 3 }
            val remaining = row.offset + row.size / 2f - (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            assertTrue(remaining > 1f && remaining < initial, "Vertical transition jumped: $remaining of $initial")
        }
        compose.mainClock.advanceTimeBy(500)
        assertRowCentered(3)
        compose.onNodeWithTag("3-a").assertIsFocused().assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun reversalInterruptsScrollAndCannotCompleteStaleFocusTransfer() {
        shelves(listOf((0..20).map(Int::toString)))
        focus("0-3")
        compose.runOnIdle { session.keyboard = true }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(80)
        val interrupted = compose.runOnIdle { session.posterPages.getValue("test").shelves.getValue("row0").firstVisibleItemScrollOffset }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("0-2").assertIsFocused()
        compose.runOnIdle {
            assertEquals(interrupted, session.posterPages.getValue("test").shelves.getValue("row0").firstVisibleItemScrollOffset)
            assertNull(session.pendingPosterMove)
            assertEquals(0, session.explicitFocusTransfers)
        }
        compose.mainClock.autoAdvance = true
    }

    private fun separatedRows(disposeSource: Boolean = false) {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(500.dp, 400.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    val state = rememberLazyListState()
                    BindPosterNavigationPage("test", Unit, listOf(
                        PosterNavigationRow("row0", listOf("a"), 0),
                        PosterNavigationRow("row1", listOf("a"), 2),
                    ), state, false)
                    LazyColumn(state = state) {
                        item {
                            if (!disposeSource || state.firstVisibleItemIndex == 0) {
                            RemotePosterRow("test", "row0") {
                                RemotePosterItem("a", 0) {
                                    BasicText("First", Modifier.size(100.dp).testTag("separated0").remoteNavigationTarget().clickable {})
                                }
                            }
                            } else Spacer(Modifier.size(100.dp))
                        }
                        item { Spacer(Modifier.height(700.dp)) }
                        item {
                            RemotePosterRow("test", "row1") {
                                RemotePosterItem("a", 0) {
                                    BasicText("Second", Modifier.size(100.dp, 160.dp).testTag("separated1").remoteNavigationTarget().clickable {})
                                }
                            }
                        }
                        item { Spacer(Modifier.height(600.dp)) }
                    }
                }
            }
        }
        focus("separated0")
        compose.runOnIdle { session.keyboard = true }
        compose.waitForIdle()
    }

    @Test fun offscreenRowRevealAnimatesFromCurrentPositionAndRefinesMeasuredHeight() {
        separatedRows()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { session.enqueuePosterMove(session.focusedTarget!!, androidx.compose.ui.focus.FocusDirection.Down) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            val state = session.posterPages.getValue("test").state!!
            assertEquals(0 to 0, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset)
        }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            val state = session.posterPages.getValue("test").state!!
            assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)
            assertTrue(state.isScrollInProgress)
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("separated1").assertIsFocused().assertIsDisplayed()
        assertRowCentered(2)
        compose.mainClock.autoAdvance = true
    }

    @Test fun reversalRecoversLogicalSourceEvenAfterLazyDisposal() {
        separatedRows(disposeSource = true)
        val source = compose.runOnIdle { session.focusedTarget!! }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { session.enqueuePosterMove(source, androidx.compose.ui.focus.FocusDirection.Down) }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle {
            assertFalse(source in session.targets)
            session.enqueuePosterMove(source, androidx.compose.ui.focus.FocusDirection.Up)
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("separated0").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(session.posterPages.getValue("test").state!!.canScrollBackward)
            assertNull(session.pendingPosterMove)
            assertEquals(0, session.explicitFocusTransfers)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun newerPendingDirectionReplacesOlderInputWithoutBuildingBacklog() {
        shelves(List(8) { listOf("a", "b", "c", "d") })
        focus("0-a")
        compose.runOnIdle { session.keyboard = true }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            val source = session.focusedTarget!!
            session.enqueuePosterMove(source, androidx.compose.ui.focus.FocusDirection.Right)
            session.releasePosterDirection(androidx.compose.ui.focus.FocusDirection.Right)
            session.enqueuePosterMove(source, androidx.compose.ui.focus.FocusDirection.Right)
            session.releasePosterDirection(androidx.compose.ui.focus.FocusDirection.Right)
            session.enqueuePosterMove(source, androidx.compose.ui.focus.FocusDirection.Down)
            session.releasePosterDirection(androidx.compose.ui.focus.FocusDirection.Down)
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("1-b").assertIsFocused()
        compose.runOnIdle { assertNull(session.pendingPosterMove) }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("1-b").assertIsFocused()
        compose.mainClock.autoAdvance = true
    }

    @Test fun releasingHeldKeyClearsCoalescedRepeats() {
        shelves(listOf((0..20).map(Int::toString)))
        focus("0-3")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("host").performKeyInput {
            keyDown(Key.DirectionRight)
            advanceEventTime(2000)
            keyUp(Key.DirectionRight)
        }
        val released = compose.runOnIdle {
            assertNull(session.pendingPosterMove)
            session.focusedTarget!!.poster!!.index
        }
        compose.mainClock.advanceTimeBy(1000)
        val settled = compose.runOnIdle {
            assertNull(session.pendingPosterMove)
            val index = session.focusedTarget!!.poster!!.index
            assertTrue(index in released..released + 1)
            index
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("0-$settled").assertIsFocused()
        compose.mainClock.autoAdvance = true
    }

    @Test fun mouseTakeoverCancelsMovementAndHidesOutline() {
        shelves(listOf((0..20).map(Int::toString)))
        focus("0-3")
        compose.runOnIdle { session.keyboard = true }
        compose.waitForIdle()
        val solidOutline = outlinePixel("0-3")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionRight) }
        compose.mainClock.advanceTimeBy(80)
        val interrupted = compose.runOnIdle {
            RemoteNavigationDispatcher.mouseMoved()
            session.posterPages.getValue("test").shelves.getValue("row0").firstVisibleItemScrollOffset
        }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle {
            assertEquals(interrupted, session.posterPages.getValue("test").shelves.getValue("row0").firstVisibleItemScrollOffset)
            assertEquals("3", session.focusedTarget?.poster?.content)
            assertFalse(session.keyboard)
            assertNull(session.pendingPosterMove)
            assertEquals(0, session.explicitFocusTransfers)
        }
        assertNotEquals(solidOutline, outlinePixel("0-3"))
        compose.mainClock.autoAdvance = true
    }

    @Test fun leavingPageCancelsAnimationAndPendingNavigation() {
        var active by mutableStateOf(true)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(500.dp, 600.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    CompositionLocalProvider(LocalScreenActive provides active) { Shelves(List(8) { listOf("a", "b") }) }
                }
            }
        }
        focus("2-a")
        compose.runOnIdle { session.keyboard = true }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { active = false }
        compose.mainClock.advanceTimeByFrame()
        val stopped = compose.runOnIdle {
            session.posterPages.getValue("test").state!!.let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset }
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle {
            val page = session.posterPages.getValue("test")
            assertEquals(stopped, page.state!!.firstVisibleItemIndex to page.state!!.firstVisibleItemScrollOffset)
            assertNull(session.pendingPosterMove)
            assertEquals(0, session.explicitFocusTransfers)
            assertFalse(page.active)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun visibleHorizontalMovesKeepViewportWhenFollowingPosterFitsAndLeftDoesNotLookAhead() {
        shelves(listOf((0..20).map(Int::toString)))
        focus("0-0")
        key(Key.DirectionRight)
        compose.runOnIdle {
            val shelf = session.posterPages.getValue("test").shelves.getValue("row0")
            assertEquals(0 to 0, shelf.firstVisibleItemIndex to shelf.firstVisibleItemScrollOffset)
        }
        repeat(3) { key(Key.DirectionRight) }
        compose.onNodeWithTag("0-4").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle {
            val layout = session.posterPages.getValue("test").shelves.getValue("row0").layoutInfo
            val item = layout.visibleItemsInfo.single { it.index == 5 }
            assertEquals(layout.viewportEndOffset, item.offset + item.size)
            assertTrue(item.offset > layout.viewportStartOffset)
        }
        repeat(2) { step ->
            key(Key.DirectionRight)
            compose.runOnIdle {
                val layout = session.posterPages.getValue("test").shelves.getValue("row0").layoutInfo
                val item = layout.visibleItemsInfo.single { it.index == 6 + step }
                assertEquals(layout.viewportEndOffset, item.offset + item.size)
                assertTrue(item.offset > layout.viewportStartOffset)
            }
        }
        val before = compose.runOnIdle {
            session.posterPages.getValue("test").shelves.getValue("row0").let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset }
        }
        key(Key.DirectionLeft)
        compose.runOnIdle {
            val shelf = session.posterPages.getValue("test").shelves.getValue("row0")
            assertEquals(before, shelf.firstVisibleItemIndex to shelf.firstVisibleItemScrollOffset)
        }
        repeat(5) { key(Key.DirectionLeft) }
        compose.onNodeWithTag("0-0").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle {
            val shelf = session.posterPages.getValue("test").shelves.getValue("row0")
            assertEquals(0 to 0, shelf.firstVisibleItemIndex to shelf.firstVisibleItemScrollOffset)
        }
    }

    private fun assertRowCentered(rowIndex: Int, pageId: String = "test", headerHeight: Int = 0) {
        compose.runOnIdle {
            val layout = session.posterPages.getValue(pageId).state!!.layoutInfo
            val row = layout.visibleItemsInfo.single { it.index == rowIndex }
            val center = (layout.viewportStartOffset + headerHeight + layout.viewportEndOffset) / 2f
            assertTrue(abs(row.offset + row.size / 2f - center) <= 1f, "Row $rowIndex was not centered: ${row.offset}, ${row.size}, $center")
        }
    }

    @Test fun verticalMovesCenterVisibleAndOffscreenShelvesAndClampAtPageEdges() {
        shelves(List(8) { listOf("a", "b") })
        focus("0-a")
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        assertRowCentered(2)
        repeat(3) { key(Key.DirectionDown) }
        assertRowCentered(5)
        repeat(2) { key(Key.DirectionDown) }
        compose.runOnIdle { assertFalse(session.posterPages.getValue("test").state!!.canScrollForward) }
        repeat(3) { key(Key.DirectionUp) }
        assertRowCentered(4)
        repeat(4) { key(Key.DirectionUp) }
        compose.runOnIdle { assertFalse(session.posterPages.getValue("test").state!!.canScrollBackward) }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Test fun verticalCenteringUsesMeasuredRowHeightsAndExcludesPinnedSearchHeader() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(500.dp, 400.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    val state = rememberLazyListState()
                    val heights = listOf(100, 180, 120, 230, 80, 180)
                    BindPosterNavigationPage("search", Unit, heights.mapIndexed { index, _ ->
                        PosterNavigationRow("row$index", listOf("a"), index + 1, grid = true)
                    }, state, false)
                    LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        stickyHeader(key = "header") { BasicText("Search", Modifier.height(60.dp)) }
                        heights.forEachIndexed { index, height ->
                            item(key = "row$index") {
                                RemotePosterRow("search", "row$index") {
                                    RemotePosterItem("a", 0) {
                                        BasicText("Row $index", Modifier.size(100.dp, height.dp).testTag("variable$index")
                                            .remoteNavigationTarget().clickable {})
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        focus("variable0")
        repeat(3) { key(Key.DirectionDown) }
        val header = compose.runOnIdle { session.posterPages.getValue("search").state!!.layoutInfo.visibleItemsInfo.single { it.index == 0 }.size }
        assertRowCentered(4, "search", header)
        key(Key.DirectionUp)
        assertRowCentered(3, "search", header)
        compose.onNodeWithTag("variable2").assertIsFocused().assertIsDisplayed()
    }

    @Test fun downRevealsOffscreenRowsAndPreservesTheirHorizontalScroll() {
        shelves(List(7) { (0..12).map(Int::toString) })
        focus("0-0")
        repeat(7) { key(Key.DirectionRight) }
        val before = compose.runOnIdle { session.posterPages.getValue("test").shelves.getValue("row0").let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset } }
        repeat(6) { key(Key.DirectionDown) }
        compose.runOnIdle { assertEquals("row6", session.focusedTarget?.poster?.row) }
        repeat(6) { key(Key.DirectionUp) }
        compose.runOnIdle {
            val shelf = session.posterPages.getValue("test").shelves.getValue("row0")
            assertEquals(before, shelf.firstVisibleItemIndex to shelf.firstVisibleItemScrollOffset)
            assertEquals("7", session.focusedTarget?.poster?.content)
        }
    }

    private fun returnThroughNavigator(change: (List<List<String>>) -> List<List<String>>, expected: String, origin: String = "duplicate", preserveScroll: Boolean = false) {
        val stack = NavBackStack<NavKey>(TabsRoute)
        val navigator = NuvioNavigator(stack)
        var entries by mutableStateOf(listOf(listOf("a", "duplicate", "c", "d", "e", "f"), listOf("duplicate", "z")))
        var loading by mutableStateOf(false)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, restorationKey = stack.last(), modifier = Modifier.size(500.dp, 500.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    NavDisplay(backStack = stack, onBack = { navigator.popBackStack() }, entryProvider = { route ->
                        NavEntry(route) {
                            RemoteNavigationEntry(route, route == navigator.currentRoute) {
                                if (route == TabsRoute) {
                                    RootTabHost(AppScreenTab.Home, active = navigator.currentRoute == TabsRoute) {
                                        Shelves(entries, loading = loading, onOpen = { navigator.navigate(DetailRoute("movie", "duplicate")) })
                                    }
                                } else BasicText("Back", Modifier.remoteNavigationTarget().clickable { navigator.popBackStack() })
                            }
                        }
                    })
                }
            }
        }
        focus("0-a")
        repeat(listOf("a", "duplicate", "c", "d", "e", "f").indexOf(origin)) { key(Key.DirectionRight) }
        key(Key.Enter)
        compose.onNodeWithText("Back").assertExists()
        val bookmark = compose.runOnIdle { session.posterPages.getValue("test").origin!! }
        compose.runOnIdle {
            loading = true
            entries = change(entries)
            navigator.popBackStack()
        }
        compose.waitForIdle()
        compose.runOnIdle { loading = false }
        compose.onNodeWithTag(expected).assertIsFocused().assertIsDisplayed()
        compose.runOnIdle {
            val page = session.posterPages.getValue("test")
            assertNull(page.origin)
            if (preserveScroll) {
                val shelf = page.shelves.getValue("row0")
                assertEquals(bookmark.shelfIndex to bookmark.shelfOffset, shelf.firstVisibleItemIndex to shelf.firstVisibleItemScrollOffset)
                assertEquals(bookmark.pageIndex to bookmark.pageOffset, page.state!!.firstVisibleItemIndex to page.state!!.firstVisibleItemScrollOffset)
            }
        }
    }

    @Test fun actualDetailsBackRestoresTitleInOriginalRowAfterReordering() =
        returnThroughNavigator({ listOf(it[0].reversed(), it[1]) }, "0-duplicate")

    @Test fun actualDetailsBackUsesOriginalNearestSurvivingNeighborAfterRemoval() =
        returnThroughNavigator({ listOf(it[0].filterNot { id -> id == "duplicate" || id == "a" }, it[1]) }, "0-c")

    @Test fun actualDetailsBackPreservesScrolledShelfAndPageOffsets() =
        returnThroughNavigator({ it }, "0-f", origin = "f", preserveScroll = true)

    @Test fun actualDetailsBackFallsThroughRemovedRowToNearbyContentRow() =
        returnThroughNavigator({ listOf(emptyList(), it[1]) }, "1-duplicate")

    @Test fun actualDetailsBackUsesRetryWhenPageBecomesEmpty() =
        returnThroughNavigator({ emptyList() }, "retry")

    @Test fun contextChangeClearsReturnBookmarkAndLaneWithoutMovingInputFocus() {
        var context by mutableStateOf(0)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(500.dp, 500.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    Column {
                        BasicText("Input", Modifier.testTag("input").remoteNavigationTarget().clickable {})
                        Shelves(listOf(listOf("a", "b")), context)
                    }
                }
            }
        }
        focus("0-b")
        compose.runOnIdle {
            session.capturePosterOrigin(session.focusedTarget?.poster)
            session.posterPages.getValue("test").lane = 300f
        }
        focus("input")
        compose.runOnIdle { context++ }
        compose.onNodeWithTag("input").assertIsFocused()
        compose.runOnIdle {
            assertNull(session.posterPages.getValue("test").origin)
            assertNull(session.posterPages.getValue("test").lane)
        }
    }

    @Test fun mixedWidthsAndIncompleteOffscreenGridRowsKeepScreenLane() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(500.dp, 280.dp).testTag("host")) {
                    session = LocalRemoteNavigation.current!!
                    val state = rememberLazyListState()
                    val counts = listOf(4, 2, 3, 1, 4)
                    val widths = listOf(100, 160, 130, 100, 100)
                    BindPosterNavigationPage("test", Unit, counts.mapIndexed { row, count ->
                        PosterNavigationRow("grid$row", (0 until count).map { "$row:$it" }, row, grid = true)
                    }, state, false)
                    LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        counts.forEachIndexed { row, count ->
                            item(key = "grid$row") {
                                RemotePosterRow("test", "grid$row") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        repeat(count) { col ->
                                            RemotePosterItem("$row:$col", col) {
                                                BasicText("$row:$col", Modifier.size(widths[row].dp, 100.dp).testTag("grid$row-$col")
                                                    .remoteNavigationTarget().clickable {})
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
        focus("grid0-3")
        key(Key.DirectionDown)
        compose.onNodeWithTag("grid1-1").assertIsFocused().assertIsDisplayed()
        assertRowCentered(1)
        key(Key.DirectionDown)
        compose.onNodeWithTag("grid2-2").assertIsFocused().assertIsDisplayed()
        assertRowCentered(2)
        key(Key.DirectionDown)
        compose.onNodeWithTag("grid3-0").assertIsFocused().assertIsDisplayed()
        assertRowCentered(3)
        key(Key.DirectionDown)
        compose.onNodeWithTag("grid4-3").assertIsFocused().assertIsDisplayed()
        repeat(4) { key(Key.DirectionUp) }
        compose.onNodeWithTag("grid0-3").assertIsFocused().assertIsDisplayed()
    }

    @Test fun independentlyScrolledShelvesKeepOffsetsWhileFollowingScreenLane() {
        shelves(List(3) { (0..20).map(Int::toString) })
        focus("0-0")
        repeat(7) { key(Key.DirectionRight) }
        key(Key.DirectionDown)
        repeat(8) { key(Key.DirectionRight) }
        val second = compose.runOnIdle {
            session.posterPages.getValue("test").shelves.getValue("row1").let { it.firstVisibleItemIndex to it.firstVisibleItemScrollOffset }
        }
        key(Key.DirectionDown)
        key(Key.DirectionUp)
        compose.runOnIdle {
            val row = session.posterPages.getValue("test").shelves.getValue("row1")
            assertEquals(second, row.firstVisibleItemIndex to row.firstVisibleItemScrollOffset)
            assertEquals("row1", session.focusedTarget?.poster?.row)
        }
    }

    @Test fun episodeReentryAndProgressMetadataRefreshKeepBrowsedPosition() {
        var preferred by mutableStateOf(1)
        var revision by mutableStateOf(0)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(900.dp, 600.dp).testTag("host")) {
                    val entry = remember { RemoteNavigationFocusDestination() }
                    Column {
                        BasicText("Enter episodes", Modifier.testTag("entry").remoteNavigationDown { entry.requestFocus() }.remoteNavigationTarget().clickable {})
                        DetailSeriesContent(
                            MetaDetails("series", "series", "Series", videos = (1..25).map { MetaVideo("$it", "Episode $it", season = 1, episode = it, overview = "revision $revision") }),
                            preferredEpisodeNumber = preferred, navigationEntry = entry,
                            onEntryUp = {}, onEpisodeClick = {},
                        )
                    }
                }
            }
        }
        focus("entry")
        key(Key.DirectionDown)
        repeat(8) { key(Key.DirectionRight) }
        val browsed = compose.onAllNodes(isFocused()).fetchSemanticsNodes().single().id
        compose.runOnIdle { preferred = 2; revision++ }
        assertEquals(browsed, compose.onAllNodes(isFocused()).fetchSemanticsNodes().single().id)
        focus("entry")
        key(Key.DirectionDown)
        compose.onNode(isFocused()).assert(hasText("Episode 1").not())
        compose.onAllNodes(isFocused()).onFirst().assertIsDisplayed()
    }
}
