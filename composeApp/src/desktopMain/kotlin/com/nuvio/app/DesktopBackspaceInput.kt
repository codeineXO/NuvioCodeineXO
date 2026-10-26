package com.nuvio.app

import java.awt.event.KeyEvent

/** Translate a complete physical press once; repeats must not reach the next overlay/page. */
internal class DesktopBackspaceInput {
    private var held = false

    fun reset() { held = false }

    /** Returns true when the event must be swallowed; translated Esc events continue normally. */
    fun process(event: KeyEvent, eligible: Boolean): Boolean {
        if (event.id == KeyEvent.KEY_TYPED) return held && event.keyChar == '\b'
        if (event.keyCode != KeyEvent.VK_BACK_SPACE) return false
        if (event.id == KeyEvent.KEY_RELEASED) {
            if (!held) return false
            held = false
        } else if (event.id == KeyEvent.KEY_PRESSED) {
            if (held) return true
            if (!eligible || event.modifiersEx != 0) return false
            held = true
        } else return false
        event.keyCode = KeyEvent.VK_ESCAPE
        event.keyChar = '\u001b'
        return false
    }
}
