package com.nuvio.app.core.diagnostics

import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.player.shortcuts.KeyChord
import com.nuvio.app.features.player.shortcuts.PlayerShortcutAction
import com.nuvio.app.features.player.shortcuts.PlayerShortcutSettings
import com.nuvio.app.features.player.shortcuts.domCode
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.debug_log_toast_off
import nuvio.composeapp.generated.resources.debug_log_toast_on
import org.jetbrains.compose.resources.getString

private val toastScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * App-wide debug-log hotkey (bound on the player Shortcuts page, default Ctrl+Shift+L).
 * Inside the player the web controls handle the same binding.
 */
internal fun installDesktopDebugLogShortcut(window: Window): () -> Unit {
    val dispatcher = KeyEventDispatcher { event ->
        if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false
        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow !== window) return@KeyEventDispatcher false
        val code = event.domCode() ?: return@KeyEventDispatcher false
        val chord = KeyChord(code, ctrl = event.isControlDown || event.isMetaDown, alt = event.isAltDown, shift = event.isShiftDown)
        val bindings = PlayerShortcutSettings.keymap.value.bindings(PlayerShortcutAction.ToggleDebugLog)
        if (chord !in bindings) return@KeyEventDispatcher false
        val enabled = DebugLogSettings.toggle()
        toastScope.launch {
            NuvioToastController.show(getString(if (enabled) Res.string.debug_log_toast_on else Res.string.debug_log_toast_off))
        }
        true
    }
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
    return { KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher) }
}
