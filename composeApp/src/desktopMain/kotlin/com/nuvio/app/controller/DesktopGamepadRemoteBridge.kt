package com.nuvio.app.controller

import com.nuvio.app.composeInputComponent
import com.nuvio.app.controller.audio.ControllerSoundPlayer
import com.nuvio.app.controller.audio.DesktopControllerSound
import com.nuvio.app.core.ui.RemoteNavigationDispatcher
import java.awt.Frame
import java.awt.Robot
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Drives the fork's remote-style keyboard navigation from a gamepad.
 *
 * Upstream's [DesktopControllerInput] polls XInput (Windows) or SDL2 (Linux/macOS); its events are
 * turned into real key presses here, the same way the phone remote's D-pad is, so every screen and
 * the player handle a controller exactly like the keyboard.
 */
internal object DesktopGamepadRemoteBridge {
    private val robot by lazy { runCatching { Robot() }.getOrNull() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    fun start() {
        if (job != null) return
        DesktopControllerSound.install()
        job = scope.launch {
            ControllerManager.events.collect(::handle)
        }
        DesktopControllerInput.start()
    }

    fun stop() {
        DesktopControllerInput.stop()
        job?.cancel()
        job = null
        DesktopControllerSound.uninstall()
    }

    private fun handle(event: ControllerEvent) {
        when (event) {
            is ControllerEvent.Navigate -> {
                val key = when (event.direction) {
                    ControllerDirection.Up -> KeyEvent.VK_UP
                    ControllerDirection.Down -> KeyEvent.VK_DOWN
                    ControllerDirection.Left -> KeyEvent.VK_LEFT
                    ControllerDirection.Right -> KeyEvent.VK_RIGHT
                }
                if (press(key)) ControllerSoundPlayer.playNavigate()
            }
            is ControllerEvent.ButtonPressed -> when (event.button) {
                XboxButton.A -> if (press(KeyEvent.VK_ENTER)) ControllerSoundPlayer.playSelect()
                XboxButton.B -> if (press(KeyEvent.VK_ESCAPE)) ControllerSoundPlayer.playBack()
                XboxButton.Menu -> if (appInFront()) {
                    SwingUtilities.invokeLater { RemoteNavigationDispatcher.openContextMenu() }
                }
                else -> Unit
            }
            is ControllerEvent.ButtonReleased,
            is ControllerEvent.AnalogScroll,
            -> Unit
        }
    }

    /** A controller only acts on Nuvio while it is the foreground app, like the keyboard. */
    private fun appInFront(): Boolean =
        Frame.getFrames().any { it.isShowing && it.isActive && it.composeInputComponent() != null }

    private fun press(keyCode: Int): Boolean {
        if (!appInFront()) return false
        val input = robot ?: return false
        input.keyPress(keyCode)
        input.keyRelease(keyCode)
        return true
    }
}
