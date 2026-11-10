package com.nuvio.app.features.remote

import com.nuvio.app.composeInputComponent
import com.nuvio.app.core.instance.DesktopSingleInstanceController
import java.awt.Frame
import java.awt.Robot
import java.awt.event.KeyEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns the phone's D-pad into real keyboard presses, like a physical keyboard, so the app's keyboard navigation
 * handles them.
 */
internal object RemoteKeyInjector {
    private val keys = mapOf(
        "key:up" to KeyEvent.VK_UP,
        "key:down" to KeyEvent.VK_DOWN,
        "key:left" to KeyEvent.VK_LEFT,
        "key:right" to KeyEvent.VK_RIGHT,
        "ok:null" to KeyEvent.VK_ENTER,
        "back:null" to KeyEvent.VK_ESCAPE,
    )
    private val robot by lazy { Robot() }

    /** Handles one phone message, e.g. `{"cmd":"key","key":"up"}`. Anything unknown is ignored. */
    fun handle(json: JsonObject) {
        val cmd = json["cmd"]?.jsonPrimitive?.contentOrNull
        val key = json["key"]?.jsonPrimitive?.contentOrNull
        keys["$cmd:$key"]?.let(::press)
    }

    /** Real key presses go to the foreground window, so only press while Nuvio is in front; else bring it there. */
    private fun press(keyCode: Int) {
        val frame = Frame.getFrames().firstOrNull { it.isShowing && it.composeInputComponent() != null } ?: return
        if (!frame.isActive) {
            DesktopSingleInstanceController.restoreAndFocusWindow()
            return
        }
        robot.keyPress(keyCode)
        robot.keyRelease(keyCode)
    }
}
