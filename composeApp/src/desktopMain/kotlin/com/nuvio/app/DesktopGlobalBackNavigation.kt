package com.nuvio.app

import com.nuvio.app.core.ui.PlatformBackDispatcher
import java.awt.AWTEvent
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

// AWT mouse button number for the standard Back / thumb button (XBUTTON1 on Windows)
private const val AWT_MOUSE_BACK_BUTTON = 4

/**
 * Installs global AWT listeners for desktop back navigation:
 * 1. Escape key: Intercepted via [KeyEventDispatcher] so it works regardless of which
 *    component or text field currently holds focus.
 * 2. Mouse Back button (button 4): Intercepted via [AWTEventListener] for the entire window hierarchy.
 */
internal fun installDesktopGlobalBackNavigation(window: Window): () -> Unit {
    val keyDispatcher = KeyEventDispatcher { event ->
        if (event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ESCAPE) {
            // Check if active window belongs to our window hierarchy
            val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
            if (focusedWindow == null || focusedWindow === window || window.isAncestorOf(focusedWindow)) {
                if (PlatformBackDispatcher.dispatch()) {
                    return@KeyEventDispatcher true
                }
            }
        }
        false
    }

    val mouseListener = AWTEventListener { event ->
        if (event is MouseEvent && event.id == MouseEvent.MOUSE_PRESSED) {
            if (event.button == AWT_MOUSE_BACK_BUTTON) {
                val sourceComponent = event.component
                if (sourceComponent == null || sourceComponent === window || window.isAncestorOf(sourceComponent)) {
                    if (PlatformBackDispatcher.dispatch()) {
                        event.consume()
                    }
                }
            }
        }
    }

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher)
    runCatching {
        Toolkit.getDefaultToolkit().addAWTEventListener(mouseListener, AWTEvent.MOUSE_EVENT_MASK)
    }

    return {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher)
        runCatching {
            Toolkit.getDefaultToolkit().removeAWTEventListener(mouseListener)
        }
    }
}
