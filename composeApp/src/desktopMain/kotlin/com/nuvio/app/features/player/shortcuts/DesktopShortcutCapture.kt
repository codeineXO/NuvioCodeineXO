package com.nuvio.app.features.player.shortcuts

import java.awt.event.KeyEvent

/**
 * Installs the recorder's key dispatcher. Call before any other dispatcher is registered: AWT runs
 * dispatchers in registration order, and focus navigation / back handling would otherwise claim
 * arrows, Esc and Backspace before the recorder sees them.
 */
internal fun installShortcutCaptureDispatcher() {
    val focusManager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
    focusManager.addKeyEventDispatcher { event -> dispatchShortcutCapture(event) }
    // Clicking anywhere else or leaving the window abandons the recording, so a later key press
    // can't silently rebind a slot the user has moved on from. Clicking another slot still works:
    // the press cancels this recording and that slot's click starts a new one.
    java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(
        { event ->
            if (event.id == java.awt.event.MouseEvent.MOUSE_PRESSED && PlayerShortcutCapture.isCapturing) {
                PlayerShortcutCapture.cancel()
            }
        },
        java.awt.AWTEvent.MOUSE_EVENT_MASK,
    )
    focusManager.addPropertyChangeListener("focusedWindow") { event ->
        if (event.newValue == null && PlayerShortcutCapture.isCapturing) PlayerShortcutCapture.cancel()
    }
}

/** Feeds AWT key presses to [PlayerShortcutCapture]; returns true when the event was consumed. */
internal fun dispatchShortcutCapture(event: KeyEvent): Boolean {
    if (!PlayerShortcutCapture.isCapturing) return false
    if (event.id != KeyEvent.KEY_PRESSED) return true
    if (event.keyCode in ModifierKeyCodes) return true
    val ctrl = event.isControlDown || event.isMetaDown
    if (!ctrl && !event.isAltDown && !event.isShiftDown &&
        (event.keyCode == KeyEvent.VK_ESCAPE || event.keyCode == KeyEvent.VK_BACK_SPACE)
    ) {
        PlayerShortcutCapture.cancel()
        return true
    }
    val code = event.domCode() ?: return true
    PlayerShortcutCapture.deliver(KeyChord(code, ctrl = ctrl, alt = event.isAltDown, shift = event.isShiftDown))
    return true
}

private val ModifierKeyCodes = setOf(
    KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META,
    KeyEvent.VK_WINDOWS, KeyEvent.VK_ALT_GRAPH, KeyEvent.VK_CAPS_LOCK, KeyEvent.VK_CONTEXT_MENU,
)

/** Maps an AWT key to its DOM `KeyboardEvent.code` name, as the player controls script sees it. */
internal fun KeyEvent.domCode(): String? {
    val code = keyCode
    return when (code) {
        in KeyEvent.VK_A..KeyEvent.VK_Z -> "Key" + ('A' + (code - KeyEvent.VK_A))
        in KeyEvent.VK_0..KeyEvent.VK_9 -> "Digit" + (code - KeyEvent.VK_0)
        in KeyEvent.VK_NUMPAD0..KeyEvent.VK_NUMPAD9 -> "Numpad" + (code - KeyEvent.VK_NUMPAD0)
        in KeyEvent.VK_F1..KeyEvent.VK_F12 -> "F" + (code - KeyEvent.VK_F1 + 1)
        KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> "ArrowLeft"
        KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> "ArrowRight"
        KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> "ArrowUp"
        KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> "ArrowDown"
        KeyEvent.VK_SPACE -> "Space"
        KeyEvent.VK_COMMA -> "Comma"
        KeyEvent.VK_PERIOD -> "Period"
        KeyEvent.VK_SLASH -> "Slash"
        KeyEvent.VK_BACK_SLASH -> "Backslash"
        KeyEvent.VK_BACK_QUOTE -> "Backquote"
        KeyEvent.VK_EQUALS -> "Equal"
        KeyEvent.VK_MINUS -> "Minus"
        KeyEvent.VK_OPEN_BRACKET -> "BracketLeft"
        KeyEvent.VK_CLOSE_BRACKET -> "BracketRight"
        KeyEvent.VK_SEMICOLON -> "Semicolon"
        KeyEvent.VK_QUOTE -> "Quote"
        KeyEvent.VK_HOME -> "Home"
        KeyEvent.VK_END -> "End"
        KeyEvent.VK_PAGE_UP -> "PageUp"
        KeyEvent.VK_PAGE_DOWN -> "PageDown"
        KeyEvent.VK_INSERT -> "Insert"
        KeyEvent.VK_DELETE -> "Delete"
        KeyEvent.VK_ADD -> "NumpadAdd"
        KeyEvent.VK_SUBTRACT -> "NumpadSubtract"
        KeyEvent.VK_MULTIPLY -> "NumpadMultiply"
        KeyEvent.VK_DIVIDE -> "NumpadDivide"
        KeyEvent.VK_DECIMAL -> "NumpadDecimal"
        KeyEvent.VK_ENTER -> if (keyLocation == KeyEvent.KEY_LOCATION_NUMPAD) "NumpadEnter" else "Enter"
        KeyEvent.VK_TAB -> "Tab"
        KeyEvent.VK_ESCAPE -> "Escape"
        KeyEvent.VK_BACK_SPACE -> "Backspace"
        else -> null
    }
}
