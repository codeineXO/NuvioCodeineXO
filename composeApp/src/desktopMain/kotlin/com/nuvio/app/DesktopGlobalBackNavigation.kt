package com.nuvio.app

import com.nuvio.app.core.ui.PlatformBackDispatcher
import com.nuvio.app.core.ui.RemoteNavigationDispatcher
import com.nuvio.app.core.ui.AppBackspaceNavigation
import com.nuvio.app.core.ui.RemoteTextEditing
import androidx.compose.ui.awt.ComposePanel
import org.jetbrains.skiko.SkiaLayer
import java.awt.Component
import java.awt.Container
import java.awt.AWTEvent
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities
import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.MouseInfo
import java.awt.Point
import java.awt.image.BufferedImage

internal val desktopNavigationHiddenPointerIcon: PointerIcon by lazy {
    PointerIcon(Toolkit.getDefaultToolkit().createCustomCursor(
        BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "nuvio-keyboard-navigation",
    ))
}

private fun Component.composeInputComponent(): Component? {
    if (this is ComposePanel || this is SkiaLayer) return this
    return (this as? Container)?.components?.firstNotNullOfOrNull { it.composeInputComponent() }
}

internal fun requestDesktopComposeFocus(window: Window) {
    SwingUtilities.invokeLater {
        if (window.isFocused) window.composeInputComponent()?.requestFocusInWindow()
    }
}

// AWT mouse button number for the standard Back / thumb button (XBUTTON1 on Windows)
private const val AWT_MOUSE_BACK_BUTTON = 4

/** Recover native focus and forward mouse Back once for the window and its dialogs. */
internal fun installDesktopGlobalBackNavigation(window: Window): () -> Unit {
    val backspaceInput = DesktopBackspaceInput()
    fun Window.belongsToApp(): Boolean {
        var candidate: Window? = this
        while (candidate != null && candidate !== window) candidate = candidate.owner
        return candidate === window
    }
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val inputFocusListener = java.beans.PropertyChangeListener { event ->
        val focusedWindow = event.newValue as? Window
        // Moving between an app window and its popup must not release a held shortcut.
        if (focusedWindow != null && (!focusedWindow.belongsToApp() || focusedWindow.composeInputComponent() == null)) backspaceInput.reset()
        if (focusedWindow == null) SwingUtilities.invokeLater {
            if (focusManager.focusedWindow?.belongsToApp() != true) backspaceInput.reset()
        }
    }
    focusManager.addPropertyChangeListener("focusedWindow", inputFocusListener)
    val focusListener = object : WindowAdapter() {
        override fun windowLostFocus(event: WindowEvent) {
            RemoteNavigationDispatcher.focusLost()
        }
    }
    window.addWindowFocusListener(focusListener)
    var lastMousePosition = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
    val keyDispatcher = KeyEventDispatcher { event ->
        val focusedWindow = focusManager.focusedWindow ?: return@KeyEventDispatcher false
        if (!focusedWindow.belongsToApp()) return@KeyEventDispatcher false
        // Native dialogs/file choosers retain their own keyboard handling.
        val input = focusedWindow.composeInputComponent() ?: return@KeyEventDispatcher false
        val owner = focusManager.focusOwner
        val liveNativePlayer = owner is com.nuvio.app.features.player.desktop.NativePlayerHost && owner.isShowing
        if (backspaceInput.process(event, eligible = AppBackspaceNavigation.isEnabled &&
                !RemoteTextEditing.isTextInputFocused && owner !is javax.swing.text.JTextComponent && !liveNativePlayer)) {
            return@KeyEventDispatcher true
        }
        if (event.id == KeyEvent.KEY_PRESSED && DesktopAutoscrollInput.cancel()) return@KeyEventDispatcher true
        if (event.id == KeyEvent.KEY_PRESSED && event.keyCode in desktopNavigationArrowKeys) {
            val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            // Dialogs and the live player retain their own key handling.
            if (manager.focusedWindow === window) {
                val input = window.composeInputComponent()
                val owner = manager.focusOwner
                if (owner is javax.swing.text.JTextComponent) return@KeyEventDispatcher false
                val outsideCompose = input != null && owner != null &&
                    owner !== input && !SwingUtilities.isDescendingFrom(owner, input)
                val liveNativePlayer = owner is com.nuvio.app.features.player.desktop.NativePlayerHost && owner.isShowing
                if (!liveNativePlayer && RemoteNavigationDispatcher.recoverFocus(force = outsideCompose)) {
                    requestDesktopComposeFocus(window)
                    return@KeyEventDispatcher true
                }
            }
        }
        val composeOwnsFocus = owner != null &&
            (owner === input || SwingUtilities.isDescendingFrom(owner, input))
        if (focusedWindow === window && event.id == KeyEvent.KEY_PRESSED && !composeOwnsFocus && !liveNativePlayer &&
            event.keyCode == KeyEvent.VK_ESCAPE) {
            return@KeyEventDispatcher PlatformBackDispatcher.dispatch()
        }
        false
    }

    val mouseListener = AWTEventListener { event ->
        if (event is MouseEvent && (event.id == MouseEvent.MOUSE_MOVED || event.id == MouseEvent.MOUSE_DRAGGED)) {
            var sourceWindow = SwingUtilities.getWindowAncestor(event.component)
            while (sourceWindow != null && sourceWindow !== window) sourceWindow = sourceWindow.owner
            if (sourceWindow === window) {
                val position = Point(event.xOnScreen, event.yOnScreen)
                if (position != lastMousePosition) RemoteNavigationDispatcher.mouseMoved()
                lastMousePosition = position
            }
        }
        if (event is MouseEvent && event.id == MouseEvent.MOUSE_PRESSED) {
            if (event.button == AWT_MOUSE_BACK_BUTTON) {
                val sourceComponent = event.component
                var sourceWindow = (sourceComponent as? Window) ?: sourceComponent?.let(SwingUtilities::getWindowAncestor)
                while (sourceWindow != null && sourceWindow !== window) sourceWindow = sourceWindow.owner
                if (sourceWindow === window && !event.isConsumed) {
                    if (DesktopAutoscrollInput.cancel()) {
                        event.consume()
                        return@AWTEventListener
                    }
                    if (PlatformBackDispatcher.dispatch()) {
                        event.consume()
                    }
                }
            }
        }
    }

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher)
    runCatching {
        Toolkit.getDefaultToolkit().addAWTEventListener(mouseListener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
    }

    return {
        focusManager.removePropertyChangeListener("focusedWindow", inputFocusListener)
        window.removeWindowFocusListener(focusListener)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher)
        runCatching {
            Toolkit.getDefaultToolkit().removeAWTEventListener(mouseListener)
        }
    }
}

private val desktopNavigationArrowKeys = setOf(
    KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
)
