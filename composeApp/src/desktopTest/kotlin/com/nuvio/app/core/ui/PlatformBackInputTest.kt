@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.nuvio.app.core.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.navigationevent.BackNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import com.nuvio.app.features.player.desktop.registerDesktopAppFullscreenToggle
import org.junit.Rule
import kotlin.test.*

class PlatformBackInputTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun escapeAndCustomBackCloseOverlayThenPageWithoutChangingFullscreen() {
        var overlay by mutableStateOf(true)
        var page by mutableStateOf(true)
        var pageBacks = 0
        var fullscreenToggles = 0
        val unregister = registerDesktopAppFullscreenToggle(
            handler = { fullscreenToggles++ }, isFullscreen = { true },
        )
        try {
            compose.setContent {
                TestWindowBackInput {
                BindPlatformBackInput()
                PlatformBackHandler(page) { pageBacks++; page = false }
                PlatformBackHandler(overlay) { overlay = false }
                BasicText("Page", Modifier.testTag("page").focusable())
                }
            }
            compose.onNodeWithTag("page").performSemanticsAction(SemanticsActions.RequestFocus)
            compose.onNodeWithTag("page").performKeyInput { pressKey(Key.Escape) }
            compose.runOnIdle {
                assertFalse(overlay)
                assertEquals(0, pageBacks)
                assertTrue(PlatformBackDispatcher.dispatch())
            }
            compose.waitForIdle()
            compose.onNodeWithTag("page").performKeyInput { pressKey(Key.Escape) }
            compose.runOnIdle {
                assertEquals(1, pageBacks)
                assertEquals(0, fullscreenToggles)
            }
        } finally { unregister() }
    }

    @Test
    fun dialogTakesEscapeBeforePageAndBridgeDetachesOnDisposal() {
        var dialog by mutableStateOf(true)
        var mounted by mutableStateOf(true)
        var pageBacks = 0
        compose.setContent {
            if (mounted) {
                TestWindowBackInput {
                BindPlatformBackInput()
                PlatformBackHandler(true) { pageBacks++ }
                BasicText("Page", Modifier.testTag("page").focusable())
                if (dialog) Dialog(onDismissRequest = { dialog = false }) {
                    TestWindowBackInput {
                    BasicText("Dialog", Modifier.testTag("dialog").focusable())
                    }
                }
                }
            }
        }
        compose.onNodeWithTag("dialog").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("dialog").performKeyInput { pressKey(Key.Escape) }
        compose.runOnIdle { assertFalse(dialog); assertEquals(0, pageBacks) }
        compose.runOnIdle { dialog = true }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(PlatformBackDispatcher.dispatch()) }
        compose.runOnIdle { assertFalse(dialog); assertEquals(0, pageBacks) }
        compose.onNodeWithTag("page").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("page").performKeyInput { pressKey(Key.Escape) }
        compose.runOnIdle { assertEquals(1, pageBacks); mounted = false }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(PlatformBackDispatcher.dispatch()) }
    }
}

/** The scene-only test rule omits ComposeSceneMediator's window-level Esc input. */
@Composable
private fun TestWindowBackInput(content: @Composable () -> Unit) {
    val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
    val input = remember { BackNavigationEventInput() }
    DisposableEffect(dispatcher) {
        dispatcher.addInput(input)
        onDispose { dispatcher.removeInput(input) }
    }
    Box(Modifier.onKeyEvent { input.onKeyEvent(it) }) { content() }
}
