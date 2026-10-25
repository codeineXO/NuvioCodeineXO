package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActionMenuTest {
    @get:Rule val compose = createComposeRule()
    private var clicks = 0
    private var selections = 0
    private var target by mutableStateOf<Pair<Int, ActionMenuInvocation>?>(null)

    @After
    fun clearMenu() {
        ActionMenuCoordinator.active = null
        ActionMenuCoordinator.panelBounds = null
        ActionMenuCoordinator.dismiss = null
        ActionMenuCoordinator.pendingRestore = null
    }

    @Test
    fun pointerPositionFlipsAtEdgesAndClampsOffscreen() {
        val window = IntSize(640, 480)
        val panel = IntSize(244, 120)
        assertEquals(IntOffset(30, 40), actionMenuPosition(window, panel, 8, Offset(30f, 40f), null))
        assertEquals(IntOffset(386, 350), actionMenuPosition(window, panel, 8, Offset(630f, 470f), null))
        assertEquals(IntOffset(8, 8), actionMenuPosition(window, panel, 8, Offset(-20f, -20f), null))
    }

    @Test
    fun nonPointerPositionUsesCardOrCentersWithoutSource() {
        val window = IntSize(640, 480)
        val panel = IntSize(244, 120)
        assertEquals(IntOffset(108, 20), actionMenuPosition(window, panel, 8, null, Rect(0f, 20f, 100f, 170f)))
        assertEquals(IntOffset(248, 352), actionMenuPosition(window, panel, 8, null, Rect(500f, 400f, 600f, 470f)))
        assertEquals(IntOffset(198, 180), actionMenuPosition(window, panel, 8, null, null))
    }

    @Test
    fun rightClickSwitchesTargetInOneGesture() {
        content()
        compose.onNodeWithTag("poster0").performMouseInput { click(button = MouseButton.Secondary) }
        compose.onNodeWithText("Action 0").assertExists()
        val preview = java.io.File("build/reports/action-menu-preview.png")
        preview.parentFile.mkdirs()
        org.jetbrains.skia.Image.makeFromBitmap(compose.onRoot().captureToImage().asSkiaBitmap()).use { image ->
            image.encodeToData()?.use { preview.writeBytes(it.bytes) }
        }
        compose.onNodeWithTag("poster1").performMouseInput { click(button = MouseButton.Secondary) }
        compose.onNodeWithText("Action 1").assertExists()
        compose.onNodeWithText("Action 0").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun outsideLeftClickDismissesWithoutActivatingCard() {
        content()
        compose.onNodeWithTag("poster0").performMouseInput { click(button = MouseButton.Secondary) }
        compose.onNodeWithTag("poster1").performMouseInput { click() }
        compose.onNodeWithText("Action 0").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.onNodeWithTag("poster1").performMouseInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun keyboardSelectionDispatchesOnceAndEscapeRestoresOpener() {
        content()
        compose.onNodeWithTag("poster0").performMouseInput { click(button = MouseButton.Secondary) }
        compose.onNodeWithText("Action 0").assertIsFocused()
        compose.onNodeWithText("Action 0").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Second action 0").assertIsFocused()
        compose.onNodeWithText("Second action 0").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, selections) }
        compose.onNodeWithTag("poster0").assertIsFocused()
        compose.onNodeWithTag("poster1").performMouseInput { click(button = MouseButton.Secondary) }
        compose.onNodeWithText("Action 1").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithText("Action 1").assertDoesNotExist()
        compose.onNodeWithTag("poster1").assertIsFocused()
        compose.runOnIdle { assertEquals(1, selections) }
    }

    @Test
    fun removingSourceDismissesMenu() {
        var showSource by mutableStateOf(true)
        content { showSource }
        compose.onNodeWithTag("poster0").performMouseInput { click(button = MouseButton.Secondary) }
        compose.runOnIdle { showSource = false }
        compose.onNodeWithText("Action 0").assertDoesNotExist()
    }

    @Test
    fun hoverProxyRetainsOriginalCardFocusAndLifecycle() {
        var visible by mutableStateOf(true)
        content(includeHoverProxy = true, sourceVisible = { visible })
        compose.onNodeWithTag("hoverProxy").performMouseInput { click(button = MouseButton.Secondary) }
        compose.onNodeWithTag("hoverProxy").assertDoesNotExist()
        compose.onNodeWithText("Action 0").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithTag("poster0").assertIsFocused()
        compose.onNodeWithTag("hoverProxy").performMouseInput { click(button = MouseButton.Secondary) }
        compose.runOnIdle { visible = false }
        compose.onNodeWithText("Action 0").assertDoesNotExist()
    }

    @Test
    fun immediateDismissDoesNotRaceTheEntranceAnimation() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.size(640.dp, 480.dp).actionMenuInput()) {
                    val haze = rememberHazeState()
                    val invocation = remember { ActionMenuInvocation() }
                    var open by remember { mutableStateOf(true) }
                    if (open) NuvioActionMenu(invocation,
                        listOf(ActionMenuAction(Icons.Rounded.Check, "Action 0", onSelected = {})),
                        haze, onDismissed = { open = false })
                    LaunchedEffect(Unit) { ActionMenuCoordinator.dismiss?.invoke(true) }
                }
            }
        }
        compose.onNodeWithText("Action 0").assertDoesNotExist()
    }

    @Test
    fun outsideScrollDismissesAndStillScrollsThePage() {
        lateinit var scroll: androidx.compose.foundation.ScrollState
        var observedMax = 0
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.size(640.dp, 480.dp).actionMenuInput()) {
                    val haze = rememberHazeState()
                    scroll = rememberScrollState()
                    LaunchedEffect(scroll) {
                        snapshotFlow { scroll.value }.collect { observedMax = maxOf(observedMax, it) }
                    }
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().hazeSource(haze).testTag("page").verticalScroll(scroll)) {
                            Box(Modifier.size(100.dp, 150.dp).background(Color.Blue).testTag("poster0")
                                .posterCardClickable(onClick = {}, onLongClick = {
                                    target = 0 to ActionMenuInvocationHolder.consume()
                                }))
                            Spacer(Modifier.height(1000.dp))
                        }
                        target?.let { (_, invocation) ->
                            NuvioActionMenu(invocation,
                                listOf(ActionMenuAction(Icons.Rounded.Check, "Action 0", onSelected = {})),
                                haze, onDismissed = { target = null })
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("poster0").performMouseInput { click(button = MouseButton.Secondary) }
        compose.onNodeWithTag("page").performMouseInput {
            moveTo(bottomRight - Offset(10f, 10f))
            scroll(120f)
        }
        compose.onNodeWithText("Action 0").assertDoesNotExist()
        compose.runOnIdle { assertTrue(scroll.value > 0, "Scroll value=${scroll.value}, max=${scroll.maxValue}, observed=$observedMax") }
    }

    @Test
    fun longLabelsWrapAndMenuFitsSmallViewportWithoutFrost() {
        val label = "A very long translated action label that needs several lines in a compact menu"
        compose.setContent {
            NuvioTheme {
                CompositionLocalProvider(LocalFrostedGlassEnabled provides false) {
                    RemoteNavigationHost(enabled = true, modifier = Modifier.size(200.dp, 180.dp).actionMenuInput().testTag("menuHost")) {
                        val haze = rememberHazeState()
                        val invocation = remember { ActionMenuInvocation() }
                        NuvioActionMenu(invocation, List(8) {
                            ActionMenuAction(Icons.Rounded.Check, "$label $it", onSelected = {})
                        }, haze, onDismissed = {})
                    }
                }
            }
        }
        compose.runOnIdle {
            val bounds = requireNotNull(ActionMenuCoordinator.panelBounds)
            val host = compose.onNodeWithTag("menuHost").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= 0f && bounds.top >= 0f)
            assertTrue(bounds.width > 0f && bounds.height > 0f)
            assertTrue(bounds.right <= host.right && bounds.bottom <= host.bottom)
        }
        compose.onNodeWithText("$label 0").assertIsDisplayed()
        compose.onNodeWithText("$label 7").performScrollTo().assertIsDisplayed()
    }

    private fun content(includeHoverProxy: Boolean = false, sourceVisible: () -> Boolean = { true }) {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.size(640.dp, 480.dp).actionMenuInput()) {
                    val haze = rememberHazeState()
                    val actionSource = remember { ActionMenuSource() }
                    Box(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxSize().hazeSource(haze)
                            .background(Brush.horizontalGradient(listOf(Color.Blue, Color.Red, Color.Green))),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            repeat(2) { index ->
                                if (sourceVisible()) CompositionLocalProvider(
                                    LocalActionMenuSource provides actionSource.takeIf { index == 0 },
                                ) { Box(
                                    Modifier.size(100.dp, 150.dp).background(Color.Blue).testTag("poster$index")
                                        .posterCardClickable(
                                            onClick = { clicks++ },
                                            onLongClick = { target = index to ActionMenuInvocationHolder.consume() },
                                            hoverScaleEnabled = false,
                                        ),
                                ) }
                            }
                        }
                        if (includeHoverProxy && sourceVisible() && ActionMenuCoordinator.active == null) {
                            Box(Modifier.align(androidx.compose.ui.Alignment.Center).size(100.dp).testTag("hoverProxy")
                                .actionMenuSecondaryClick(
                                    { target = 0 to ActionMenuInvocationHolder.consume() }, actionSource,
                                ))
                        }
                        target?.let { (index, invocation) ->
                            key(invocation) {
                                NuvioActionMenu(
                                    invocation,
                                    listOf(
                                        ActionMenuAction(Icons.Rounded.Check, "Action $index", onSelected = { selections++ }),
                                        ActionMenuAction(Icons.Rounded.Check, "Second action $index", onSelected = { selections++ }),
                                    ),
                                    haze,
                                    onDismissed = { if (target?.second === invocation) target = null },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
