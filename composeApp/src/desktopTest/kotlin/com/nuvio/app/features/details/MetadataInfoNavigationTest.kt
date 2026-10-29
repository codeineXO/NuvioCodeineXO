package com.nuvio.app.features.details

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
import com.nuvio.app.core.ui.*
import com.nuvio.app.features.streams.*
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertTrue

class MetadataInfoNavigationTest {
    @get:Rule val compose = createComposeRule()
    private var visible by mutableStateOf(true)
    private lateinit var session: RemoteNavigationSession

    @Test fun detailsControlsUseVerticalArrowsAndRetainKeyboardFocusOnActivation() {
        content()
        verifyDirections("Metadata info")
        compose.onNodeWithText("Metadata info").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Metadata info").assertIsFocused()
        compose.onNodeWithText("Metadata info").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Copy Metadata ID").assertIsFocused()
        compose.onNodeWithContentDescription("Copy Metadata ID").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Metadata info").assertIsFocused()
    }

    @Test fun enhancedDetailsPageTraversalIncludesThePanelButHorizontalTraversalSkipsIt() {
        content(enhanced = true)
        verifyDirections("Metadata info")
        compose.onNodeWithText("Metadata info").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithContentDescription("Copy Metadata ID").assertIsFocused()
    }

    @Test fun streamSelectionPanelUsesVerticalArrowsWithoutDisruptingProviderFilters() {
        content(streams = true)
        verifyDirections("Source & IDs")
        compose.onNodeWithText("Source & IDs").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("below").assertIsFocused()
        compose.onNodeWithTag("below").performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Source & IDs").assertIsFocused()
        compose.onNodeWithText("Source & IDs").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Source & IDs").assertIsFocused()
    }

    @Test fun disabledToggleRemovesPanelTargetsAndPreservesSurroundingArrowNavigation() {
        content()
        verifyToggleOff("Metadata info")
    }

    @Test fun disabledStreamPanelLeavesProviderNavigationUnchanged() {
        content(streams = true)
        verifyToggleOff("Source & IDs")
    }

    @Test fun mouseActivationClearsHighlightAfterKeyboardActivationRetainsFocus() {
        content(streams = true)
        compose.onNodeWithText("Source & IDs").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Source & IDs").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Source & IDs").assertIsFocused()
        compose.onNodeWithText("Source & IDs").performMouseInput { click() }
        compose.onNodeWithText("Source & IDs").assertIsNotFocused()
    }

    private fun verifyToggleOff(header: String) {
        compose.onNodeWithText(header).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.runOnIdle { visible = false }
        compose.onNodeWithText(header).assertDoesNotExist()
        compose.runOnIdle { assertTrue(session.targets.none { it.verticalOnly }) }
        focus("left")
        compose.onNodeWithTag("left").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithTag("right").assertIsFocused()
        focus("left")
        compose.onNodeWithTag("left").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("below").assertIsFocused()
        compose.runOnIdle { visible = true }
        focus("above")
        compose.onNodeWithTag("above").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText(header).assertIsFocused()
    }

    private fun verifyDirections(header: String) {
        focus("left")
        compose.onNodeWithTag("left").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText(header).assertIsNotFocused()
        focus("right")
        compose.onNodeWithTag("right").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.onNodeWithText(header).assertIsNotFocused()
        focus("above")
        compose.onNodeWithTag("above").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText(header).assertIsFocused()
        compose.onNodeWithText(header).performKeyInput { pressKey(Key.DirectionLeft); pressKey(Key.DirectionRight) }
        compose.onNodeWithText(header).assertIsFocused()
        compose.onNodeWithText(header).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("above").assertIsFocused()
        compose.onNodeWithTag("above").performKeyInput { pressKey(Key.DirectionDown) }
    }

    private fun focus(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)

    private fun content(enhanced: Boolean = false, streams: Boolean = false) {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(enabled = true, modifier = Modifier.size(700.dp, 600.dp)) {
                    session = LocalRemoteNavigation.current!!
                    val list = rememberLazyListState()
                    val boundary = rememberRemoteNavigationPageBoundary(list)
                    val results = remember { StreamResultsNavigation() }
                    CompositionLocalProvider(
                        LocalRemoteNavigationPageBoundary provides boundary.takeIf { enhanced },
                        LocalStreamResultsNavigation provides results.takeIf { streams },
                    ) {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize().remoteNavigationGroup()
                            .then(if (streams) Modifier.streamResultsNavigation(results, listOf(null), null) {} else Modifier)) {
                            item { Box(Modifier.padding(start = 96.dp)) { control("above", Modifier.width(400.dp)) } }
                            item {
                                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    control("left", Modifier.padding(top = 12.dp).width(80.dp))
                                    Box(Modifier.width(400.dp)) {
                                        if (visible) MetadataInfoPanel(
                                            meta = MetaDetails("tt123", "movie", "Title"), requestedId = "tt123",
                                            playbackId = "tt123".takeIf { streams },
                                        )
                                    }
                                    control("right", Modifier.padding(top = 12.dp).width(80.dp))
                                }
                            }
                            item { Box(Modifier.padding(start = 96.dp)) {
                                control("below", Modifier.width(400.dp).then(if (streams) Modifier.streamResultsFocus(true, null) else Modifier))
                            } }
                        }
                    }
                }
            }
        }
    }

    @Composable private fun control(tag: String, modifier: Modifier) {
        BasicText(tag, modifier.height(48.dp).testTag(tag).remoteNavigationTarget(onSelect = {}).clickable {})
    }
}
