package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.components.DesktopDetailHero
import com.nuvio.app.navigation.DetailRoute
import com.nuvio.app.navigation.TabsRoute
import com.nuvio.app.AppScreenTab
import com.nuvio.app.DesktopHoverSidebar
import com.nuvio.app.RootTabHost
import org.junit.Rule
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.test.*

class RemoteNavigationRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sidebarActivationRetainsFocusWhenRootTabContentChanges() {
        var selectedTab by mutableStateOf(AppScreenTab.Home)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, restorationKey = selectedTab) {
                    Row(Modifier.size(800.dp, 600.dp)) {
                        DesktopHoverSidebar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            onProfileSelected = {},
                            onAddProfileRequested = {},
                            sidebarExpanded = true,
                            sidebarWidth = 220.dp,
                        )
                        RootTabHost(selectedTab = selectedTab, modifier = Modifier.weight(1f)) { tab ->
                            BasicText("Page ${tab.name}", Modifier.size(100.dp)
                                .remoteNavigationTarget().clickable {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Home").performSemanticsAction(SemanticsActions.RequestFocus)
        listOf(
            Key.DirectionDown to AppScreenTab.Search,
            Key.DirectionDown to AppScreenTab.Library,
            Key.DirectionUp to AppScreenTab.Search,
            Key.DirectionUp to AppScreenTab.Home,
        ).forEach { (direction, tab) ->
            compose.onNodeWithText(selectedTab.name).performKeyInput { pressKey(direction) }
            compose.onNodeWithText(tab.name).assertIsFocused()
            compose.onNodeWithText(tab.name).performKeyInput { pressKey(Key.Enter) }
            compose.runOnIdle { assertEquals(tab, selectedTab) }
            compose.onNodeWithText(tab.name).assertIsFocused()
        }
    }

    @Test
    fun activatingSidebarTabsKeepsFocusOnTheActivatedItem() {
        var selectedTab by mutableStateOf("Home")
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, restorationKey = selectedTab) {
                    CompositionLocalProvider(LocalRemoteNavigationRole provides RemoteNavigationRole.Sidebar) {
                        Column {
                            listOf("Home", "Search", "Library").forEach { tab ->
                                key(tab) {
                                    BasicText(tab, Modifier.size(100.dp, 40.dp)
                                        .remoteNavigationTarget(sidebarEntry = selectedTab == tab)
                                        .clickable { selectedTab = tab })
                                }
                            }
                        }
                    }
                }
            }
        }
        // Leaving Search via Library remembers Library under Search's route.
        // Returning to Search must retain the newly activated Search item.
        listOf("Search", "Library", "Search", "Home").forEach { tab ->
            compose.onNodeWithText(tab).performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onNodeWithText(tab).performKeyInput { pressKey(Key.Enter) }
            compose.runOnIdle { assertEquals(tab, selectedTab) }
            compose.onNodeWithText(tab).assertIsFocused()
        }
    }

    @Test
    fun contextMenuOutlineFollowsClippedFirstMiddleLastAndSingleRows() {
        var accent = Color.Unspecified
        compose.setContent {
            NuvioTheme {
                accent = MaterialTheme.nuvio.colors.accent
                RemoteNavigationHost(enabled = true) {
                    Row {
                        listOf(3, 1).forEach { count ->
                            Column(Modifier.width(240.dp).clip(RoundedCornerShape(NuvioTokens.Radius.md)).background(Color.Black)) {
                                repeat(count) { index ->
                                    ActionMenuRow(
                                        action = ActionMenuAction(Icons.Rounded.Check, "Action $index", onSelected = {}),
                                        shape = actionMenuRowShape(index, count),
                                        enabled = true,
                                        onSelected = {},
                                        modifier = Modifier.testTag("menu$count-$index"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        listOf(3, 1).forEach { count ->
            repeat(count) { index ->
                val node = compose.onNodeWithTag("menu$count-$index")
                node.performSemanticsAction(SemanticsActions.RequestFocus)
                node.performKeyInput { pressKey(Key.Enter) }
                val pixels = node.captureToImage().toPixelMap()
                val scale = pixels.width / 240f
                listOf(true, false).forEach { top ->
                    val rounded = if (top) index == 0 else index == count - 1
                    val diagonal = (scale + (if (rounded) 8f * scale else 0f) * (1 - 1 / sqrt(2f))).roundToInt()
                    listOf(true, false).forEach { left ->
                        val x = if (left) diagonal else pixels.width - 1 - diagonal
                        val y = if (top) diagonal else pixels.height - 1 - diagonal
                        val matches = (-1..1).any { dx -> (-1..1).any { dy ->
                            val color = pixels[(x + dx).coerceIn(0, pixels.width - 1), (y + dy).coerceIn(0, pixels.height - 1)]
                            abs(color.red - accent.red) + abs(color.green - accent.green) + abs(color.blue - accent.blue) < 0.25f
                        } }
                        assertTrue(matches, "Context outline clipped: count=$count row=$index top=$top left=$left")
                    }
                }
            }
        }
    }

    @Test
    fun downLeavesEpisodePageAndUpRestoresEntireHero() {
        lateinit var state: androidx.compose.foundation.lazy.LazyListState
        compose.setContent {
            state = rememberLazyListState()
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.size(500.dp, 400.dp)) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize().remoteNavigationGroup()) {
                        item {
                            Column(Modifier.height(400.dp).remoteNavigationScrollToTop(state)) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.BottomStart) {
                                    BasicText("Play", Modifier.testTag("heroAction").remoteNavigationTarget(initialFocus = true).clickable {})
                                }
                                LazyRow(Modifier.height(100.dp).remoteNavigationGroup()) {
                                    items(5) { index ->
                                        BasicText("Episode $index", Modifier.size(120.dp, 100.dp).testTag("episode$index")
                                            .remoteNavigationTarget().clickable {})
                                    }
                                }
                            }
                        }
                        item {
                            BasicText("Below episodes", Modifier.fillMaxWidth().height(200.dp).testTag("below")
                                .remoteNavigationTarget().clickable {})
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("episode0").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("episode0").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithTag("below").assertIsFocused()
        compose.onNodeWithTag("below").performKeyInput { pressKey(Key.DirectionUp) }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(0, state.firstVisibleItemIndex); assertEquals(0, state.firstVisibleItemScrollOffset) }
        compose.onNodeWithTag("heroAction").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(0, state.firstVisibleItemIndex); assertEquals(0, state.firstVisibleItemScrollOffset) }
    }

    @Test
    fun enterHoldOpensContextOnceAndReleaseDoesNotSelect() {
        var clicks = 0
        var contexts = 0
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    BasicText("Poster", Modifier.size(100.dp).testTag("hold")
                        .remoteNavigationTarget(onSelect = { clicks++ }, onContext = { contexts++ })
                        .clickable { clicks++ })
                }
            }
        }
        compose.onNodeWithTag("hold").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("hold").performKeyInput { keyDown(Key.Enter) }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithTag("hold").performKeyInput { keyUp(Key.Enter) }
        compose.runOnIdle { assertEquals(0, clicks); assertEquals(1, contexts) }
        compose.onNodeWithTag("hold").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(1, contexts) }
    }

    @Test
    fun windowBackDispatchesOutsideFieldsAndBackspaceDeletesInsideFields() {
        var backs = 0
        var value by mutableStateOf("hello")
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    PlatformBackHandler(enabled = true) { backs++ }
                    Row {
                        BasicText("Action", Modifier.size(80.dp).testTag("action")
                            .remoteNavigationTarget().clickable {})
                        BasicTextField(value, { value = it }, Modifier.width(120.dp).testTag("field")
                            .remoteNavigationTarget(textField = true))
                    }
                }
            }
        }
        compose.onNodeWithTag("action").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { assertTrue(AppBackspaceNavigation.isEnabled); assertTrue(PlatformBackDispatcher.dispatch()) }
        compose.runOnIdle { assertEquals(1, backs) }
        compose.onNodeWithTag("field").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { assertTrue(RemoteTextEditing.isTextInputFocused) }
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("field").performTextInputSelection(androidx.compose.ui.text.TextRange(5))
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.Backspace) }
        compose.runOnIdle { assertEquals("hell", value); assertEquals(1, backs) }
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.Escape); pressKey(Key.Backspace) }
        compose.runOnIdle { assertEquals("hell", value); assertEquals(1, backs) }
    }

    @Test
    fun customPinInputKeepsBackspace() {
        var deletes = 0
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    BasicText("PIN", Modifier.testTag("pin").remoteTextInputGuard().focusable()
                        .onPreviewKeyEvent {
                            if (it.key == Key.Backspace && it.type == KeyEventType.KeyDown) { deletes++; true }
                            else false
                        })
                }
            }
        }
        compose.onNodeWithTag("pin").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { assertTrue(RemoteTextEditing.isTextInputFocused) }
        compose.onNodeWithTag("pin").performKeyInput { pressKey(Key.Backspace) }
        compose.runOnIdle { assertEquals(1, deletes) }
    }

    @Test
    fun syntheticHoverDoesNotHideKeyboardOutlineOrMoveFocus() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    Row {
                        BasicText("First", Modifier.size(100.dp).testTag("first")
                            .remoteNavigationTarget().clickable {})
                        BasicText("Second", Modifier.size(100.dp).testTag("second")
                            .remoteNavigationTarget().clickable {})
                    }
                }
            }
        }
        compose.onNodeWithTag("first").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("first").performKeyInput { pressKey(Key.DirectionUp) }
        val before = compose.onNodeWithTag("first").captureToImage().toPixelMap()
        compose.onNodeWithTag("second").performMouseInput { moveTo(center) }
        compose.onNodeWithTag("first").assertIsFocused()
        val after = compose.onNodeWithTag("first").captureToImage().toPixelMap()
        assertEquals(before[before.width / 2, 1], after[after.width / 2, 1])
        compose.onNodeWithTag("second").performMouseInput { click() }
        val mouse = compose.onNodeWithTag("first").captureToImage().toPixelMap()
        assertNotEquals(before[before.width / 2, 1], mouse[mouse.width / 2, 1])
    }

    @Test
    fun keyboardSuppressesHoverExpansionUntilNativeMouseMovement() {
        val source = MutableInteractionSource()
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    val hovered by source.collectIsRemoteHoveredAsState()
                    Column {
                        Box(Modifier.desktopPosterHoverScale(interactionSource = source)
                            .size(100.dp).testTag("poster").remoteNavigationTarget().focusable())
                        BasicText("keyboard=${isRemoteKeyboardNavigationActive},hover=$hovered")
                    }
                }
            }
        }
        compose.onNodeWithTag("poster").performMouseInput { moveTo(center) }
        compose.onNodeWithText("keyboard=false,hover=true").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        val expanded = compose.onNodeWithTag("poster").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithTag("poster").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("poster").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("keyboard=true,hover=false").assertExists()
        val normal = compose.onNodeWithTag("poster").fetchSemanticsNode().boundsInRoot.width
        assertTrue(expanded > normal, "Keyboard navigation must immediately cancel poster hover expansion")
        compose.runOnIdle { RemoteNavigationDispatcher.mouseMoved() }
        compose.onNodeWithText("keyboard=false,hover=true").assertExists()
        compose.onNodeWithTag("poster").assertIsFocused()
    }

    @Test
    fun detailsBackRemainsReachableWhenSpatialFocusSearchFails() {
        compose.setContent {
            val realManager = LocalFocusManager.current
            val blockedSearch = remember {
                object : FocusManager {
                    override fun clearFocus(force: Boolean) = realManager.clearFocus(force)
                    override fun moveFocus(focusDirection: FocusDirection): Boolean = false
                }
            }
            NuvioTheme {
                CompositionLocalProvider(LocalFocusManager provides blockedSearch) {
                    RemoteNavigationHost(enabled = true) {
                        NuvioBackButton(onClick = {}, modifier = Modifier.testTag("back"))
                        BasicText("Play", Modifier.offset(200.dp, 100.dp).size(80.dp)
                            .remoteNavigationTarget(initialFocus = true).focusable())
                        BasicText("Episode", Modifier.offset(500.dp, 250.dp).size(80.dp)
                            .remoteNavigationTarget().focusable())
                    }
                }
            }
        }
        compose.onNodeWithText("Episode").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Episode").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("back").assertIsFocused()
        compose.onNodeWithTag("back").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("back").assertIsFocused()
    }

    @Test
    fun fullscreenButtonIsSkippedByKeyboardNavigation() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    Row {
                        BasicText("First", Modifier.size(50.dp).remoteNavigationTarget().focusable())
                        FullscreenActionButton(modifier = Modifier.testTag("fullscreen"))
                        BasicText("Second", Modifier.size(50.dp).remoteNavigationTarget().focusable())
                    }
                }
            }
        }
        compose.onNodeWithText("First").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("First").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Second").assertIsFocused()
        compose.onNodeWithTag("fullscreen").performSemanticsAction(SemanticsActions.RequestFocus) { request -> assertFalse(request()) }
        compose.onNodeWithTag("fullscreen").assertHasClickAction()
    }

    @Test
    fun outlineFollowsPosterRadiusAndCircularButtonContour() {
        var accent = Color.Unspecified
        val shapes: List<Shape> = listOf(RoundedCornerShape(0.dp), RoundedCornerShape(24.dp), CircleShape)
        compose.setContent {
            NuvioTheme {
                accent = MaterialTheme.nuvio.colors.accent
                RemoteNavigationHost(enabled = true) {
                    Row {
                        shapes.forEachIndexed { index, shape ->
                            Box(Modifier.size(100.dp).testTag("shape$index")
                                .remoteNavigationTarget(shape = shape).clip(shape)
                                .background(Color.Black).clickable {})
                        }
                    }
                }
            }
        }
        shapes.indices.forEach { index ->
            compose.onNodeWithTag("shape$index").performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onNodeWithTag("shape$index").performKeyInput { pressKey(Key.DirectionUp) }
            val pixels = compose.onNodeWithTag("shape$index").captureToImage().toPixelMap()
            val scale = pixels.width / 100f
            val radius = when (index) { 0 -> 0f; 1 -> 24f * scale; else -> pixels.width / 2f }
            val diagonal = (radius * (1 - 1 / sqrt(2f)) - 0.5f).roundToInt().coerceAtLeast(0)
            val matches = (-1..1).any { dx -> (-1..1).any { dy ->
                val color = pixels[(diagonal + dx).coerceIn(0, pixels.width - 1), (diagonal + dy).coerceIn(0, pixels.height - 1)]
                abs(color.red - accent.red) + abs(color.green - accent.green) + abs(color.blue - accent.blue) < 0.25f
            } }
            assertTrue(matches, "Missing focus border on shape $index at its actual corner contour")
        }
    }

    @Test
    fun detailsLoadsDuringNavigationAndReceivesPlayFocusInsteadOfOutgoingPosters() {
        val stack = mutableStateListOf<NavKey>(TabsRoute)
        var loaded by mutableStateOf(false)
        var plays = 0
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, restorationKey = stack.last(), modifier = Modifier.testTag("host")) {
                    NavDisplay(backStack = stack, onBack = { stack.removeAt(stack.lastIndex) }, entryProvider = { key ->
                        NavEntry(key) {
                            RemoteNavigationEntry(routeKey = key, active = key == stack.last()) {
                                if (key == TabsRoute) {
                                    NuvioScreen(topPadding = 0.dp) {
                                        item {
                                            NuvioPosterCard("Poster", null, showTitleBelow = false,
                                                onClick = { stack.add(DetailRoute("movie", "test")) })
                                        }
                                    }
                                } else if (!loaded) {
                                    BasicText("Loading")
                                } else {
                                    NuvioScreen(topPadding = 0.dp) {
                                        item {
                                            DesktopDetailHero(
                                                meta = MetaDetails("test", "movie", "Test movie"),
                                                showOverallRatings = false, isMdbListActive = false,
                                                playButtonLabel = "Play", isPrimaryPlayEnabled = true,
                                                isSaved = false, isWatched = false, onHeightChanged = {},
                                                heroTrailerSourceUrl = null, heroTrailerReady = false,
                                                heroTrailerMuted = true, onHeroTrailerMuteToggle = {},
                                                onPlayClick = { plays++ }, onPlayLongClick = null,
                                                onShuffleClick = null, shuffleEnabled = false,
                                                onWatchedClick = {}, onSaveClick = {}, onSaveLongClick = null,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    })
                }
            }
        }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Poster").performKeyInput { pressKey(Key.Enter) }
        compose.mainClock.advanceTimeBy(100)
        if (compose.onAllNodesWithText("Poster").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Poster").performSemanticsAction(SemanticsActions.RequestFocus) { request ->
                assertFalse(request(), "Retained outgoing posters must not accept keyboard focus")
            }
        }
        compose.runOnIdle { loaded = true }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, plays) }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun retainedEntriesDoNotShareFocusOrRouteMemory() {
        var active by mutableStateOf("home")
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, restorationKey = active, modifier = Modifier.testTag("host")) {
                    RemoteNavigationEntry("home", active == "home") {
                        BasicText("Poster", Modifier.size(100.dp).remoteNavigationTarget().clickable {})
                    }
                    RemoteNavigationEntry("details", active == "details") {
                        BasicText("Play", Modifier.size(100.dp).remoteNavigationTarget(initialFocus = true).clickable {})
                    }
                }
            }
        }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Poster").assertIsFocused()
        compose.runOnIdle { active = "details" }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Poster").performSemanticsAction(SemanticsActions.RequestFocus) { request -> assertFalse(request()) }
        compose.runOnIdle { active = "home" }
        compose.onNodeWithText("Poster").assertIsFocused()
    }

    @Test
    fun containerFocusDoesNotPreventRecoveryToDetailsPlayAction() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    Column {
                        Box(Modifier.size(20.dp).testTag("container").focusable())
                        BasicText("Play", Modifier.size(100.dp).remoteNavigationTarget(initialFocus = true).clickable {})
                    }
                }
            }
        }
        compose.onNodeWithTag("container").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { assertTrue(RemoteNavigationDispatcher.recoverFocus()) }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.runOnIdle { assertFalse(RemoteNavigationDispatcher.recoverFocus()) }
    }

    @Test
    fun outlineHandlesTemporarilyCollapsedControls() {
        var width by mutableStateOf(100.dp)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    Box(Modifier.width(width).height(50.dp).testTag("action").clip(CircleShape)
                        .remoteNavigationTarget(shape = CircleShape).clickable {})
                }
            }
        }
        compose.onNodeWithTag("action").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("action").performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { width = 1.dp }
        compose.onRoot().captureToImage()
        compose.runOnIdle { width = 100.dp }
        compose.onNodeWithTag("action").assertIsFocused()
    }
}
