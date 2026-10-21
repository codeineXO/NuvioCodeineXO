package com.nuvio.app.features.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toPixelMap
import com.nuvio.app.core.ui.*
import com.nuvio.app.features.details.components.AnimeFillerChip
import com.nuvio.app.features.details.components.EnhancedDetailsScrollButton
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EnhancedDetailsNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun content() {
        compose.setContent {
            NuvioTheme {
                RemoteNavigationHost(true, modifier = Modifier.size(600.dp, 440.dp).testTag("host")) {
                    var secondPage by remember { mutableStateOf(false) }
                    val first = rememberLazyListState()
                    val second = rememberLazyListState()
                    val list = if (secondPage) second else first
                    val scope = rememberCoroutineScope()
                    val transition = remember { EnhancedDetailsPageTransition() }
                    val footerFocus = remember { RemoteNavigationFocusDestination() }
                    val fillerFocus = remember { RemoteNavigationFocusDestination() }
                    val seasonFocus = remember { RemoteNavigationFocusDestination() }
                    val episodeFocus = remember { RemoteNavigationFocusDestination() }
                    val focusFirstSection = rememberRemoteNextSectionFocus(second, 0)
                    val focusFirstPageEnd = rememberRemoteNextSectionFocus(first, 0, fromEnd = true)
                    val focusLastSection = rememberRemoteNextSectionFocus(list, 0, fromEnd = true)
                    val focusPlay = rememberRemotePrimaryFocus()
                    BindRemotePrimaryFocusReveal(first, true)
                    val boundary = rememberRemoteNavigationPageBoundary(list) {
                        if (secondPage) scope.launch {
                            footerFocus.requestFocus()
                            transition.change(false) {
                                secondPage = false
                                withFrameNanos { }
                                withFrameNanos { }
                                first.positionEnhancedDetailsPage(true)
                            }
                            focusFirstPageEnd()
                        }
                    }
                    val switch: (Boolean, Boolean) -> Unit = { next, keyboard ->
                        scope.launch {
                            footerFocus.requestFocus()
                            transition.change(next) {
                                secondPage = next
                                withFrameNanos { }
                                withFrameNanos { }
                                (if (next) second else first).positionEnhancedDetailsPage(false)
                            }
                            if (next && keyboard) focusFirstSection()
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        NuvioBackButton({}, Modifier.align(Alignment.TopStart).padding(16.dp).excludeFromRemoteNavigation().testTag("back"), buttonSize = 48.dp)
                        CompositionLocalProvider(LocalRemoteNavigationPageBoundary provides boundary) {
                            Box(Modifier.fillMaxSize().enhancedDetailsPageTransition(transition)) {
                                LazyColumn(
                                    state = list,
                                    modifier = Modifier.padding(top = if (secondPage) 80.dp else 0.dp).fillMaxSize()
                                        .onGloballyPositioned { boundary.viewportBounds = it.boundsInRoot() }.testTag("content"),
                                ) {
                                    if (secondPage) {
                                        items(6) { index ->
                                            Button({}, Modifier.height(100.dp).width(280.dp).remoteNavigationTarget().testTag("section-$index")) {
                                                Text("Section $index")
                                            }
                                        }
                                        item { Spacer(Modifier.height(68.dp)) }
                                    } else item {
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Button({}, Modifier.remoteNavigationDown { fillerFocus.requestFocus() }.remoteNavigationTarget(initialFocus = true).testTag("play")) { Text("Play") }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                AnimeFillerChip("Filler markers", false, 14.sp, {}, Modifier
                                                    .remoteNavigationFocusDestination(fillerFocus)
                                                    .remoteNavigationUp { focusPlay() }.remoteNavigationDown { seasonFocus.requestFocus() }.testTag("filler"))
                                                AnimeFillerChip("Hide fillers", false, 14.sp, {}, Modifier
                                                    .remoteNavigationUp { focusPlay() }.remoteNavigationDown { seasonFocus.requestFocus() }.testTag("hide"))
                                            }
                                            Button({}, Modifier.remoteNavigationFocusDestination(seasonFocus)
                                                .remoteNavigationUp { fillerFocus.requestFocus() }.remoteNavigationDown { episodeFocus.requestFocus() }
                                                .remoteNavigationTarget().testTag("season")) { Text("Season") }
                                            Button({}, Modifier.remoteNavigationFocusDestination(episodeFocus)
                                                .remoteNavigationUp { seasonFocus.requestFocus() }.remoteNavigationDown { footerFocus.requestFocus() }
                                                .remoteNavigationTarget().testTag("episode")) { Text("Episode") }
                                        }
                                    }
                                }
                            }
                            EnhancedDetailsScrollButton(
                                secondPage = secondPage,
                                onClick = { switch(!it, false) },
                                onSelect = { switch(!it, !it) },
                                modifier = Modifier.align(Alignment.BottomCenter).remoteNavigationFocusDestination(footerFocus)
                                    .remoteNavigationUp { focusLastSection() }
                                    .remoteNavigationDown { if (!secondPage) switch(true, true) }.testTag("footer"),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun focus(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus)
    private fun press(key: Key) = compose.onNodeWithTag("host").performKeyInput { pressKey(key) }
    private fun pageTwo() {
        focus("footer")
        press(Key.Enter)
        compose.onNodeWithTag("section-0").assertIsFocused()
    }

    @Test fun fillerControlsHighlightAndParticipateInTheVerticalFocusPath() {
        content()
        val before = compose.onNodeWithTag("filler").captureToImage().toPixelMap()
        focus("play")
        press(Key.DirectionDown)
        compose.onNodeWithTag("filler").assertIsFocused()
        val after = compose.onNodeWithTag("filler").captureToImage().toPixelMap()
        assertNotEquals(before[before.width / 2, 4], after[after.width / 2, 4])
        press(Key.DirectionRight)
        compose.onNodeWithTag("hide").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag("season").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("filler").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithTag("play").assertIsFocused()
    }

    @Test fun firstPageDoesNotWrapOrFocusBack() {
        content()
        focus("play")
        repeat(3) { press(Key.DirectionUp); press(Key.DirectionLeft) }
        compose.onNodeWithTag("play").assertIsFocused()
        compose.onNodeWithTag("back").assertIsNotFocused()
        compose.onNodeWithTag("host").performKeyInput { keyDown(Key.ShiftLeft); pressKey(Key.Tab); keyUp(Key.ShiftLeft) }
        compose.onNodeWithTag("play").assertIsFocused()
    }

    @Test fun secondPageContentStaysBelowBackAndDownDoesNotCycle() {
        content()
        pageTwo()
        val back = compose.onNodeWithTag("back").fetchSemanticsNode().boundsInRoot
        val firstSection = compose.onNodeWithTag("section-0").fetchSemanticsNode().boundsInRoot
        assertTrue(firstSection.top >= back.bottom + 8)
        focus("footer")
        repeat(3) { press(Key.DirectionDown); press(Key.Tab) }
        compose.onNodeWithTag("footer").assertIsFocused()
        compose.onNodeWithTag("back").assertIsNotFocused()
    }

    @Test fun movingUpTraversesLazySectionsThenReturnsToFirstPage() {
        content()
        pageTwo()
        focus("footer")
        press(Key.DirectionUp)
        compose.onNodeWithTag("section-5").assertIsFocused()
        for (index in 4 downTo 0) {
            press(Key.DirectionUp)
            compose.onNodeWithTag("section-$index").assertIsFocused()
        }
        press(Key.DirectionUp)
        compose.onNodeWithTag("episode").assertIsFocused()
        compose.onNodeWithTag("back").assertIsNotFocused()
        compose.onNodeWithText("See More").assertIsDisplayed()
    }
}
