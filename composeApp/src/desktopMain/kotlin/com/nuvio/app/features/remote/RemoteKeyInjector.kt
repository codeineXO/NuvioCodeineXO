package com.nuvio.app.features.remote

import com.nuvio.app.composeInputComponent
import com.nuvio.app.core.instance.DesktopSingleInstanceController
import com.nuvio.app.core.ui.PlatformBackDispatcher
import java.awt.Frame
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Turns phone remote messages into the key presses the app's keyboard navigation already understands. */
internal object RemoteKeyInjector {
    private val arrowKeys = mapOf(
        "up" to KeyEvent.VK_UP,
        "down" to KeyEvent.VK_DOWN,
        "left" to KeyEvent.VK_LEFT,
        "right" to KeyEvent.VK_RIGHT,
    )

    /** Handles one phone message, e.g. `{"cmd":"key","key":"up"}`. Anything unknown is ignored. */
    fun handle(message: String) {
        val json = runCatching { Json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return
        when (json["cmd"]?.jsonPrimitive?.contentOrNull) {
            "key" -> arrowKeys[json["key"]?.jsonPrimitive?.contentOrNull]?.let(::press)
            "ok" -> press(KeyEvent.VK_ENTER)
            "back" -> SwingUtilities.invokeLater { PlatformBackDispatcher.dispatch() }
        }
    }

    /** Sends a press and release to the main window's focused component, where real key presses go. */
    private fun press(keyCode: Int) = SwingUtilities.invokeLater {
        val frame = Frame.getFrames().firstOrNull { it.isShowing && it.composeInputComponent() != null }
            ?: return@invokeLater
        // A TV remote wakes the TV: bring Nuvio to the front if another window is active.
        if (!frame.isFocused) DesktopSingleInstanceController.restoreAndFocusWindow()
        val target = frame.mostRecentFocusOwner ?: return@invokeLater
        val now = System.currentTimeMillis()
        target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, now, 0, keyCode, KeyEvent.CHAR_UNDEFINED))
        target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, now, 0, keyCode, KeyEvent.CHAR_UNDEFINED))
    }
}
