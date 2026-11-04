package com.nuvio.app.features.settings

import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.KeyEvent

internal fun installDesktopAppScaleShortcuts(window: Window): () -> Unit {
    val dispatcher = KeyEventDispatcher { event ->
        if (KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow !== window) return@KeyEventDispatcher false
        if (event.id != KeyEvent.KEY_PRESSED) return@KeyEventDispatcher false

        val modifiers = event.modifiersEx
        val hasCtrlOrMeta = (modifiers and KeyEvent.CTRL_DOWN_MASK != 0) || (modifiers and KeyEvent.META_DOWN_MASK != 0)
        val hasAlt = modifiers and KeyEvent.ALT_DOWN_MASK != 0
        if (!hasCtrlOrMeta || hasAlt) return@KeyEventDispatcher false

        when (event.keyCode) {
            KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS, KeyEvent.VK_ADD -> {
                ThemeSettingsRepository.stepAppScale(10)
                true
            }
            KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> {
                ThemeSettingsRepository.stepAppScale(-10)
                true
            }
            KeyEvent.VK_0, KeyEvent.VK_NUMPAD0 -> {
                ThemeSettingsRepository.setAppScale(100)
                true
            }
            else -> false
        }
    }
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
    return {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
    }
}
