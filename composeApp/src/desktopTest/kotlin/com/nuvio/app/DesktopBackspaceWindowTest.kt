@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nuvio.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import com.nuvio.app.core.ui.*
import com.nuvio.app.features.settings.KeyboardNavigationSettings
import java.awt.KeyboardFocusManager
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import kotlin.test.*
import org.junit.After
import org.junit.Test

/** Real AWT events exercise the window boundary that scene-only Compose tests bypass. */
class DesktopBackspaceWindowTest {
    private val robot = Robot().apply { autoDelay = 35 }
    private var window: ComposeWindow? = null
    private var uninstall: (() -> Unit)? = null
    private val nativeEvents = mutableListOf<String>()
    private var failureDetails: () -> String = { "" }
    private val keyObserver = java.awt.KeyEventDispatcher {
        nativeEvents += "${it.id}:${it.keyCode}:${it.modifiersEx}:text=${RemoteTextEditing.isTextInputFocused}"
        false
    }
    private val originalKeyboardNavigation = KeyboardNavigationSettings.enabled.value

    private fun edt(action: () -> Unit) = SwingUtilities.invokeAndWait(action)
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            var ready = false
            edt { ready = condition() }
            if (ready) return
            Thread.sleep(20)
        }
        fail("$message (owner=${KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner}, text=${RemoteTextEditing.isTextInputFocused}, native=$nativeEvents, ${failureDetails()})")
    }

    private fun open(active: () -> Boolean = { true }, content: @Composable () -> Unit) {
        edt {
            window = ComposeWindow().apply {
                setSize(480, 320)
                setLocation(80, 80)
                setContent {
                    NuvioTheme {
                        RemoteNavigationHost(enabled = active(), backspaceEnabled = active()) { content() }
                    }
                }
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyObserver)
                uninstall = installDesktopGlobalBackNavigation(this)
                isVisible = true
                toFront()
                requestFocus()
            }
            requestDesktopComposeFocus(window!!)
        }
        await("App window never received Compose input focus") {
            window!!.isFocused && KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner !== window
        }
        robot.waitForIdle()
    }

    private fun clickWindow() {
        var x = 0
        var y = 0
        edt { x = window!!.x + window!!.width / 2; y = window!!.y + window!!.height / 2 }
        robot.mouseMove(x, y)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
        robot.waitForIdle()
    }

    private fun press(key: Int = KeyEvent.VK_BACK_SPACE) {
        robot.keyPress(key)
        robot.keyRelease(key)
        robot.waitForIdle()
    }

    private fun dispatchAwtBackspace() {
        // Windows text-input/IME sessions can consume Robot input before it reaches AWT.
        // Inject at the boundary under test while retaining the real window and focus owner.
        edt {
            val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            for (id in listOf(KeyEvent.KEY_PRESSED, KeyEvent.KEY_TYPED, KeyEvent.KEY_RELEASED)) {
                manager.dispatchEvent(KeyEvent(manager.focusOwner, id, System.currentTimeMillis(), 0,
                    if (id == KeyEvent.KEY_TYPED) KeyEvent.VK_UNDEFINED else KeyEvent.VK_BACK_SPACE, '\b'))
            }
        }
    }

    @After fun cleanup() {
        robot.keyRelease(KeyEvent.VK_BACK_SPACE)
        edt {
            uninstall?.invoke()
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyObserver)
            window?.dispose()
            KeyboardNavigationSettings.setEnabled(originalKeyboardNavigation)
        }
    }

    @Test fun mouseOpenedLoadingAndErrorPagesNavigateWithKeyboardNavigationOnAndOff() {
        var page by mutableStateOf("home")
        var backs = 0
        open {
            PlatformBackHandler(page != "home") { backs++; page = "home" }
            Box(Modifier.fillMaxSize().focusProperties { canFocus = false }.clickable { page = "loading" }) {
                BasicText(page)
            }
        }
        for (keyboard in listOf(true, false)) {
            edt { KeyboardNavigationSettings.setEnabled(keyboard) }
            for (destination in listOf("loading", "details", "error")) {
                clickWindow()
                await("Mouse click did not open page") { page == "loading" }
                if (destination != "loading") edt { page = destination }
                // No focused child is needed, including before data becomes available.
                val previousBacks = backs
                press()
                await("Backspace failed on $destination, keyboard=$keyboard") { backs == previousBacks + 1 && page == "home" }
            }
        }
        press()
        edt { assertEquals(6, backs) }
    }

    @Test fun dialogsAndDropdownsDismissBeforePageAndEscStillWorks() {
        var dialog by mutableStateOf(false)
        var menu by mutableStateOf(false)
        var pageBacks = 0
        var dismissals = 0
        var overlayReady = false
        edt { KeyboardNavigationSettings.setEnabled(false) }
        open {
            PlatformBackHandler(true) { pageBacks++ }
            BasicText("Page")
            if (dialog) DialogSurface(onDismissRequest = { dialog = false; dismissals++ }) {
                DisposableEffect(Unit) { overlayReady = true; onDispose { overlayReady = false } }
                BasicText("Dialog")
            }
            Menu(expanded = menu, onDismissRequest = { menu = false; dismissals++ }) {
                DisposableEffect(Unit) { overlayReady = true; onDispose { overlayReady = false } }
                MenuItem(text = "Option", onClick = {})
            }
        }
        for (isDialog in listOf(true, false)) {
            edt { if (isDialog) dialog = true else menu = true }
            await("Overlay was not composed") { overlayReady }
            press()
            await("Backspace did not dismiss overlay") { !dialog && !menu }
            await("Overlay was not disposed") { !overlayReady }
            edt { assertEquals(0, pageBacks) }
        }
        await("Main window did not regain focus") { window!!.isFocused }
        press(KeyEvent.VK_ESCAPE)
        await("Esc stopped navigating") { pageBacks == 1 }
        press()
        await("Backspace stopped navigating after overlay dismissal") { pageBacks == 2 }
        edt { assertEquals(2, dismissals) }
    }

    @Test fun sharedAndDirectFieldsKeepDeletionWhenKeyboardNavigationIsOff() {
        var value by mutableStateOf(TextFieldValue("ab", TextRange(2)))
        var shared by mutableStateOf(true)
        var composedShared: Boolean? = null
        var fieldActive by mutableStateOf(true)
        var backs = 0
        val keyEvents = mutableListOf<String>()
        failureDetails = { "value=$value, backs=$backs, shared=$shared, keys=$keyEvents" }
        val requester = FocusRequester()
        edt { KeyboardNavigationSettings.setEnabled(false) }
        open {
            PlatformBackHandler(true) { backs++ }
            CompositionLocalProvider(LocalScreenActive provides fieldActive) {
                key(shared) {
                    SideEffect { composedShared = shared }
                    BasicTextField(value, { value = it }, Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { keyEvents += "${it.key}:${it.type}"; false }
                        .then(if (shared) Modifier.remoteNavigationTarget(textField = true) else Modifier.remoteTextInputGuard())
                        .focusRequester(requester))
                    LaunchedEffect(Unit) { requester.requestFocus() }
                }
            }
        }
        for (sharedField in listOf(true, false)) {
            edt { shared = sharedField; value = TextFieldValue("ab", TextRange(2)) }
            await("Text field was not composed") { composedShared == sharedField }
            edt { requester.requestFocus() }
            clickWindow()
            await("Text input did not reserve Backspace") { RemoteTextEditing.isTextInputFocused }
            dispatchAwtBackspace()
            await("Text was not deleted") { value.text.length == 1 }
            dispatchAwtBackspace()
            await("Text was not emptied") { value.text.isEmpty() }
            dispatchAwtBackspace()
            edt { assertEquals(0, backs) }
        }
        edt { fieldActive = false }
        await("Outgoing input still reserves Backspace") { !RemoteTextEditing.isTextInputFocused }
        dispatchAwtBackspace()
        await("Retained outgoing input prevented Back") { backs == 1 }
    }

    @Test fun appBottomSheetClosesBeforeUnderlyingPage() {
        var sheet by mutableStateOf(false)
        var ready = false
        var backs = 0
        edt { KeyboardNavigationSettings.setEnabled(false) }
        open {
            PlatformBackHandler(true) { backs++ }
            BasicText("Page")
            if (sheet) NuvioModalBottomSheet(onDismissRequest = { sheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                DisposableEffect(Unit) { ready = true; onDispose { ready = false } }
                BasicText("Sheet")
            }
        }
        edt { sheet = true }
        await("Sheet was not composed") { ready }
        press()
        await("Backspace did not dismiss sheet") { !sheet }
        edt { assertEquals(0, backs) }
    }

    @Test fun pinInputRetainsBackspaceEvenWhenEmpty() {
        var digits = 2
        var backs = 0
        val requester = FocusRequester()
        edt { KeyboardNavigationSettings.setEnabled(false) }
        open {
            PlatformBackHandler(true) { backs++ }
            BasicText("PIN", Modifier.remoteTextInputGuard().focusRequester(requester)
                .onPreviewKeyEvent {
                    if (it.key == Key.Backspace) {
                        if (it.type == KeyEventType.KeyDown) digits = (digits - 1).coerceAtLeast(0)
                        true
                    } else false
                }.focusable())
        }
        edt { requester.requestFocus() }
        await("PIN did not reserve Backspace") { RemoteTextEditing.isTextInputFocused }
        repeat(3) { dispatchAwtBackspace() }
        edt { assertEquals(0, digits); assertEquals(0, backs) }
    }

    @Test fun heldBackspaceCannotNavigateAgainAfterDialogCloses() {
        var dialog by mutableStateOf(false)
        var dialogReady = false
        var backs = 0
        open {
            PlatformBackHandler(true) { backs++ }
            BasicText("Page")
            if (dialog) Dialog(onDismissRequest = { dialog = false }) {
                DisposableEffect(Unit) { dialogReady = true; onDispose { dialogReady = false } }
                BasicText("Dialog")
            }
        }
        edt { dialog = true }
        await("Dialog was not composed") { dialogReady }
        robot.keyPress(KeyEvent.VK_BACK_SPACE)
        await("Held Backspace did not close dialog") { !dialog }
        await("Page did not regain focus") { window!!.isFocused }
        repeat(3) {
            edt {
                val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
                manager.dispatchEvent(KeyEvent(manager.focusOwner, KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(), 0, KeyEvent.VK_BACK_SPACE, '\b'))
            }
        }
        edt { assertEquals(0, backs) }
        robot.keyRelease(KeyEvent.VK_BACK_SPACE)
        press()
        await("Fresh Backspace press did not navigate") { backs == 1 }
    }

    @Test fun playerScopeIsExcluded() {
        var app by mutableStateOf(true)
        var backs = 0
        var nativeBackspaces = 0
        open(active = { app }) {
            PlatformBackHandler(true) { backs++ }
            BasicText("Page")
        }
        press()
        await("Initial Backspace did not navigate") { backs == 1 }
        edt { app = false }
        // Observe untouched native events in the excluded route without mounting player UI.
        val observer = java.awt.KeyEventDispatcher {
            if (it.id == KeyEvent.KEY_PRESSED && it.keyCode == KeyEvent.VK_BACK_SPACE) nativeBackspaces++
            false
        }
        edt { KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(observer) }
        try {
            robot.waitForIdle()
            press()
            await("Excluded player scope received a translated shortcut") { nativeBackspaces == 1 }
            edt { assertEquals(1, backs) }
        } finally {
            edt { KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(observer) }
        }
    }

    @Test fun appAndNativeDialogsIgnoreBackgroundTextFocusAndUseTheirOwnBackDispatcher() {
        var pageBacks = 0
        var dialogBacks = 0
        var appDialog by mutableStateOf(false)
        var appDialogReady = false
        var editDialog by mutableStateOf(false)
        var dialogValue by mutableStateOf(TextFieldValue("ab", TextRange(2)))
        val dialogField = FocusRequester()
        var dialog: ComposeDialog? = null
        val field = FocusRequester()
        open {
            PlatformBackHandler(true) { pageBacks++ }
            BasicTextField("Parent input", {}, Modifier.remoteTextInputGuard().focusRequester(field))
            LaunchedEffect(Unit) { field.requestFocus() }
            if (appDialog) DialogSurface(onDismissRequest = { appDialog = false }) {
                DisposableEffect(Unit) { appDialogReady = true; onDispose { appDialogReady = false } }
                if (editDialog) BasicTextField(dialogValue, { dialogValue = it },
                    Modifier.remoteTextInputGuard().focusRequester(dialogField))
                else BasicText("App dialog")
            }
        }
        edt { field.requestFocus() }
        await("Parent input was not focused") { RemoteTextEditing.isTextInputFocused }
        try {
            edt { appDialog = true }
            await("App dialog was not composed") { appDialogReady }
            await("Background input still reserves app-dialog Backspace") { !RemoteTextEditing.isTextInputFocused }
            press()
            await("App dialog did not dismiss") { !appDialog }
            await("App dialog was not disposed") { !appDialogReady }
            edt { editDialog = true; appDialog = true }
            await("Editable app dialog was not composed") { appDialogReady }
            edt { dialogField.requestFocus() }
            await("Dialog's own text input did not reserve Backspace") { RemoteTextEditing.isTextInputFocused }
            dispatchAwtBackspace()
            await("Dialog text did not delete") { dialogValue.text.length == 1 }
            edt { assertTrue(appDialog); assertEquals(0, pageBacks) }
            press(KeyEvent.VK_ESCAPE)
            await("Editable app dialog did not dismiss") { !appDialog && !appDialogReady }
            edt { assertEquals(0, pageBacks); field.requestFocus() }
            await("Parent input did not regain focus") { RemoteTextEditing.isTextInputFocused }
            edt {
                dialog = ComposeDialog(window).apply {
                    setSize(250, 180)
                    setContent {
                        PlatformBackHandler(true) { dialogBacks++; dialog!!.dispose() }
                        BasicText("Native dialog")
                    }
                    isVisible = true
                    toFront()
                    requestFocus()
                }
                requestDesktopComposeFocus(dialog!!)
            }
            await("Native dialog never received Compose input focus") {
                dialog!!.isFocused && KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner !== dialog
            }
            await("Background input still reserves foreground Backspace") { !RemoteTextEditing.isTextInputFocused }
            press()
            await("Native dialog did not receive Backspace as Esc") { dialogBacks == 1 }
            edt { assertEquals(0, pageBacks) }
        } finally {
            edt { dialog?.dispose() }
        }
    }
}
