package com.nuvio.app.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import com.nuvio.app.features.settings.SettingsSidebarItem
import com.nuvio.app.features.details.components.DetailActionButtons
import com.nuvio.app.AppScreenTab
import com.nuvio.app.RootTabHost
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun focus(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    }

    @Test
    fun desktopKeyEntryRecoversAfterFocusWasCleared() {
        lateinit var manager: FocusManager
        compose.setContent {
            manager = LocalFocusManager.current
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.testTag("host")) {
                    NuvioScreen(topPadding = 0.dp) {
                        item { SettingsSidebarItem("Appearance", Icons.Default.Settings, false, {}) }
                    }
                }
            }
        }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { manager.clearFocus(force = true) }
        compose.onNodeWithText("Appearance").assertIsNotFocused()
        compose.runOnIdle { assertTrue(RemoteNavigationDispatcher.recoverFocus()) }
        compose.onNodeWithText("Appearance").assertIsFocused()
    }

    @Test
    fun realTabHostRestoresNavigationAcrossTabDisposal() {
        var tab by mutableStateOf(AppScreenTab.Home)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, restorationKey = tab, modifier = Modifier.testTag("host")) {
                    RootTabHost(selectedTab = tab) { current ->
                        NuvioScreen(topPadding = 0.dp) {
                            item { SettingsSidebarItem(current.name, Icons.Default.Settings, false, {}) }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText(AppScreenTab.Home.name).assertIsFocused()
        compose.runOnIdle { tab = AppScreenTab.Settings }
        compose.onNodeWithText(AppScreenTab.Settings.name).assertIsFocused()
        compose.runOnIdle { tab = AppScreenTab.Home }
        compose.onNodeWithText(AppScreenTab.Home.name).assertIsFocused()
    }

    @Test
    fun returningFromExcludedPlayerRequestsPlatformFocusAndRestoresSelection() {
        var playing by mutableStateOf(false)
        var platformFocusRequests = 0
        compose.setContent {
            NuvioTheme {
                CompositionLocalProvider(LocalRemotePlatformFocus provides { platformFocusRequests++ }) {
                    RemoteNavigationHost(enabled = !playing, modifier = Modifier.testTag("host")) {
                        if (!playing) {
                            NuvioScreen(topPadding = 0.dp) {
                                item { DetailActionButtons(playLabel = "Play", onPlayClick = { playing = true }) }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle {
            assertFalse(RemoteNavigationDispatcher.recoverFocus(force = true))
            platformFocusRequests = 0
            playing = false
        }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.runOnIdle {
            assertEquals(1, platformFocusRequests)
            assertFalse(RemoteNavigationDispatcher.recoverFocus())
        }
    }

    @Test
    fun firstArrowReachesPosterInsideRealShelfAndScreen() {
        var selected = -1
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.testTag("host")) {
                    NuvioScreen(topPadding = 0.dp) {
                        item {
                            NuvioShelfSection(title = "Movies", entries = (0..8).toList()) { index ->
                                NuvioPosterCard(title = "Poster $index", imageUrl = null,
                                    showTitleBelow = false, onClick = { selected = index })
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Poster 0").assertIsFocused()
        compose.onNodeWithText("Poster 0").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.Enter)
        }
        compose.runOnIdle { assertEquals(1, selected) }
    }

    @Test
    fun firstArrowReachesSettingsInsideRealScreen() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.testTag("host")) {
                    NuvioScreen(topPadding = 0.dp) {
                        item { SettingsSidebarItem("Appearance", Icons.Default.Settings, false, {}) }
                        item { SettingsSidebarItem("Playback", Icons.Default.Settings, false, {}) }
                    }
                }
            }
        }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Appearance").assertIsFocused()
        compose.onNodeWithText("Appearance").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Playback").assertIsFocused()
    }

    @Test
    fun firstArrowReachesDetailsActionsInsideRealScreen() {
        var plays = 0
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.testTag("host")) {
                    NuvioScreen(topPadding = 0.dp) {
                        item { DetailActionButtons(playLabel = "Play", onPlayClick = { plays++ }) }
                    }
                }
            }
        }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Play").assertIsFocused()
        compose.onNodeWithText("Play").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, plays) }
    }

    @Test
    fun firstArrowEntersContentWithoutTab() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.testTag("host")) {
                    BasicText("First", Modifier.size(80.dp).testTag("first")
                        .remoteNavigationTarget().clickable {})
                }
            }
        }
        compose.onNodeWithTag("host").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("first").assertIsFocused()
    }

    @Test
    fun overlayTrapsFocusAndRestoresOpener() {
        var open by mutableStateOf(false)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    Row {
                        BasicText("Open", Modifier.size(80.dp).testTag("opener")
                            .remoteNavigationTarget().clickable { open = true })
                        if (open) {
                            Row(Modifier.remoteNavigationModal()) {
                                BasicText("Action", Modifier.size(80.dp).testTag("action")
                                    .remoteNavigationTarget().clickable {})
                            }
                        }
                        BasicText("Outside", Modifier.size(80.dp).testTag("outside")
                            .remoteNavigationTarget().clickable {})
                    }
                }
            }
        }
        focus("opener")
        compose.onNodeWithTag("opener").performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.Enter)
        }
        compose.onNodeWithTag("action").assertIsFocused()
        compose.onNodeWithTag("action").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("action").assertIsFocused()
        compose.runOnIdle { open = false }
        compose.onNodeWithTag("opener").assertIsFocused()
    }

    @Test
    fun returningToRouteRestoresFocusedControl() {
        var route by mutableStateOf("home")
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, restorationKey = route) {
                    key(route) {
                        Row {
                            BasicText("First", Modifier.size(80.dp).testTag("first")
                                .remoteNavigationTarget().clickable {})
                            BasicText("Second", Modifier.size(80.dp).testTag("second")
                                .remoteNavigationTarget().clickable {})
                        }
                    }
                }
            }
        }
        focus("first")
        compose.onNodeWithTag("first").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { route = "details" }
        compose.runOnIdle { route = "home" }
        compose.onNodeWithTag("second").assertIsFocused()
    }

    @Test
    fun removedControlRestoresNearestRemainingItem() {
        var route by mutableStateOf("home")
        var removed by mutableStateOf(false)
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, restorationKey = route) {
                    key(route) {
                        Row {
                            (0..2).filterNot { removed && it == 1 }.forEach { index ->
                                key(index) {
                                    BasicText("Item $index", Modifier.size(80.dp).testTag("item$index")
                                        .remoteNavigationTarget().clickable {})
                                }
                            }
                        }
                    }
                }
            }
        }
        focus("item0")
        compose.onNodeWithTag("item0").performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { route = "details" }
        compose.runOnIdle { removed = true }
        compose.runOnIdle { route = "home" }
        compose.onNodeWithTag("item2").assertIsFocused()
    }

    @Test
    fun arrowsStopAtEdgesAndEnterActivatesOnce() {
        var clicks = 0
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    Row {
                        BasicText("First", Modifier.size(80.dp).testTag("first")
                            .remoteNavigationTarget().clickable { clicks++ })
                        BasicText("Second", Modifier.size(80.dp).testTag("second")
                            .remoteNavigationTarget().clickable { clicks++ })
                    }
                }
            }
        }
        focus("first")
        compose.onNodeWithTag("first").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithTag("first").assertIsFocused()
        compose.onNodeWithTag("first").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("second").assertIsFocused()
        compose.onNodeWithTag("second").performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.Enter)
        }
        compose.onNodeWithTag("second").assertIsFocused()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun enterEditsFieldAndEscapeExitsBeforeBack() {
        var value by mutableStateOf(TextFieldValue("hello", TextRange(5)))
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    Row {
                        BasicTextField(value, { value = it }, Modifier.width(120.dp).testTag("field")
                            .remoteNavigationTarget(textField = true))
                        BasicText("Next", Modifier.size(80.dp).testTag("next")
                            .remoteNavigationTarget().clickable {})
                    }
                }
            }
        }
        focus("field")
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("next").assertIsFocused()
        focus("field")
        compose.onNodeWithTag("field").performKeyInput {
            pressKey(Key.Enter)
            pressKey(Key.DirectionLeft)
        }
        compose.onNodeWithTag("field").assertIsFocused()
        compose.runOnIdle {
            assertEquals(4, value.selection.start)
            assertTrue(RemoteTextEditing.dispatchBack())
        }
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("next").assertIsFocused()
        compose.runOnIdle { assertFalse(RemoteTextEditing.dispatchBack()) }
    }

    @Test
    fun excludedScreenKeepsNativeTextEditing() {
        var value by mutableStateOf(TextFieldValue("hello", TextRange(5)))
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = false) {
                    BasicTextField(value, { value = it }, Modifier.testTag("field")
                        .remoteNavigationTarget(textField = true))
                }
            }
        }
        focus("field")
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle {
            assertEquals(4, value.selection.start)
            assertFalse(RemoteTextEditing.dispatchBack())
        }
    }

    @Test
    fun fieldTrailingButtonCanBeActivatedWithEnter() {
        var clicks = 0
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    OutlinedTextField(
                        value = "password",
                        onValueChange = {},
                        modifier = Modifier.remoteNavigationTarget(textField = true),
                        trailingIcon = {
                            IconButton(
                                onClick = { clicks++ },
                                modifier = Modifier.testTag("visibility").remoteNavigationTarget(),
                            ) { BasicText("Show") }
                        },
                    )
                }
            }
        }
        focus("visibility")
        compose.onNodeWithTag("visibility").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun enterAndVerticalArrowsExitEditing() {
        var value by mutableStateOf("hello")
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    Column {
                        BasicText("Above", Modifier.size(80.dp).testTag("above").remoteNavigationTarget().clickable {})
                        NuvioInputField(value, { value = it }, "Search", Modifier.testTag("field"))
                        BasicText("Below", Modifier.size(80.dp).testTag("below").remoteNavigationTarget().clickable {})
                    }
                }
            }
        }
        for (key in listOf(Key.Enter, Key.NumPadEnter)) {
            focus("field")
            compose.onNodeWithTag("field").performKeyInput { pressKey(key) }
            compose.runOnIdle { assertTrue(RemoteTextEditing.isEditing) }
            compose.onNodeWithTag("field").performKeyInput { pressKey(key) }
            compose.onNodeWithTag("field").assertIsFocused()
            compose.runOnIdle { assertFalse(RemoteTextEditing.isEditing); assertEquals("hello", value) }
        }
        for ((key, destination) in listOf(Key.DirectionUp to "above", Key.DirectionDown to "below")) {
            compose.onNodeWithTag("field").performClick()
            compose.onNodeWithTag("field").performKeyInput { pressKey(key) }
            compose.onNodeWithTag(destination).assertIsFocused()
            compose.runOnIdle { assertFalse(RemoteTextEditing.isEditing) }
        }
    }

    @Test
    fun customDownExitsEditingAndNoDestinationStillEndsEditing() {
        var customMoves = 0
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    NuvioInputField("hello", {}, "Search", Modifier.testTag("field")
                        .remoteNavigationDown { customMoves++ })
                }
            }
        }
        compose.onNodeWithTag("field").performClick()
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { assertEquals(1, customMoves); assertFalse(RemoteTextEditing.isEditing) }
        compose.onNodeWithTag("field").assertIsFocused()
        compose.onNodeWithTag("field").performClick()
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.DirectionUp) }
        compose.runOnIdle { assertFalse(RemoteTextEditing.isEditing) }
        compose.onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun fieldClearButtonIsReachedByRightAndRestoresFieldAfterClearing() {
        var value by mutableStateOf("query")
        var clicks = 0
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
                    NuvioInputField(value, { value = it }, "Search",
                        Modifier.testTag("field").focusRequester(requester),
                        trailingContent = if (value.isNotEmpty()) ({
                            IconButton(onClick = { value = ""; clicks++; requester.requestFocus() },
                                modifier = Modifier.testTag("clear").remoteNavigationTarget()) { BasicText("Clear") }
                        }) else null,
                    )
                }
            }
        }
        focus("field")
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("clear").assertIsFocused()
        compose.onNodeWithTag("clear").performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals("", value); assertEquals(1, clicks) }
        compose.onNodeWithTag("field").assertIsFocused()
    }

    @Test
    fun clickingFieldEntersEditingWithoutEnter() {
        var value by mutableStateOf(TextFieldValue("hello", TextRange(5)))
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    BasicTextField(value, { value = it }, Modifier.width(120.dp).testTag("field")
                        .remoteNavigationTarget(textField = true))
                }
            }
        }
        compose.onNodeWithTag("field").performClick()
        compose.runOnIdle { value = value.copy(selection = TextRange(5)) }
        compose.onNodeWithTag("field").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle {
            assertEquals(4, value.selection.start)
            assertTrue(RemoteTextEditing.dispatchBack())
        }
    }

    @Test
    fun directionalFocusScrollsLazyRowBeyondViewport() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true) {
                    LazyRow(Modifier.width(240.dp).remoteNavigationGroup()) {
                        items((0..11).toList(), key = { it }) { index ->
                            BasicText("Item $index", Modifier.size(80.dp).testTag("item$index")
                                .remoteNavigationTarget().clickable {})
                        }
                    }
                }
            }
        }
        focus("item0")
        repeat(8) { index ->
            compose.onNodeWithTag("item$index").performKeyInput { pressKey(Key.DirectionRight) }
        }
        compose.onNodeWithTag("item8").assertIsFocused().assertIsDisplayed()
    }
}
