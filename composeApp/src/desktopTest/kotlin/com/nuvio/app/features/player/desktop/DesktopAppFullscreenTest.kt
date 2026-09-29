package com.nuvio.app.features.player.desktop

import androidx.compose.ui.window.WindowPlacement
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopAppFullscreenTest {
    @Test
    fun `native fullscreen exit lets the window listener restore placement`() {
        val updates = mutableListOf<String>()

        applyMacosComposeFullscreenExit(
            restorePlacement = WindowPlacement.Maximized,
            requestNativeFullscreenExit = {
                updates += "native"
                true
            },
            clearComposeFullscreen = { updates += "compose" },
            setStatePlacement = { updates += "state:$it" },
        )

        assertEquals(listOf("native"), updates)
    }

    @Test
    fun `compose fallback clears fullscreen before restoring maximized placement`() {
        val updates = mutableListOf<Pair<String, WindowPlacement>>()

        applyMacosComposeFullscreenExit(
            restorePlacement = WindowPlacement.Maximized,
            requestNativeFullscreenExit = { false },
            clearComposeFullscreen = { updates += "compose" to WindowPlacement.Floating },
            setStatePlacement = { updates += "state" to it },
        )

        assertEquals(
            listOf(
                "compose" to WindowPlacement.Floating,
                "state" to WindowPlacement.Maximized,
            ),
            updates,
        )
    }

    @Test
    fun `fullscreen cannot be restored as its own exit placement`() {
        val updates = mutableListOf<Pair<String, WindowPlacement>>()

        applyMacosComposeFullscreenExit(
            restorePlacement = WindowPlacement.Fullscreen,
            requestNativeFullscreenExit = { false },
            clearComposeFullscreen = { updates += "compose" to WindowPlacement.Floating },
            setStatePlacement = { updates += "state" to it },
        )

        assertEquals(
            listOf(
                "compose" to WindowPlacement.Floating,
                "state" to WindowPlacement.Floating,
            ),
            updates,
        )
    }

    @Test
    fun `exitDesktopAppFullscreen is a no-op when already windowed`() {
        var toggled = false
        val unregister = registerDesktopAppFullscreenToggle(
            handler = { toggled = true },
            isFullscreen = { false },
        )
        try {
            exitDesktopAppFullscreen()
            javax.swing.SwingUtilities.invokeAndWait {}
            assertEquals(false, toggled)
        } finally {
            unregister()
        }
    }

    @Test
    fun `exitDesktopAppFullscreen triggers toggle when fullscreen`() {
        var toggled = false
        val unregister = registerDesktopAppFullscreenToggle(
            handler = { toggled = true },
            isFullscreen = { true },
        )
        try {
            exitDesktopAppFullscreen()
            javax.swing.SwingUtilities.invokeAndWait {}
            assertEquals(true, toggled)
        } finally {
            unregister()
        }
    }

    @Test
    fun `setDesktopAppFullscreen is a no-op when already in target state`() {
        var toggleCount = 0
        var isFullscreenState = true
        val unregister = registerDesktopAppFullscreenToggle(
            handler = { toggleCount++ },
            isFullscreen = { isFullscreenState },
        )
        try {
            setDesktopAppFullscreen(fullscreen = true)
            javax.swing.SwingUtilities.invokeAndWait {}
            assertEquals(0, toggleCount)

            isFullscreenState = false
            setDesktopAppFullscreen(fullscreen = false)
            javax.swing.SwingUtilities.invokeAndWait {}
            assertEquals(0, toggleCount)

            setDesktopAppFullscreen(fullscreen = true)
            javax.swing.SwingUtilities.invokeAndWait {}
            assertEquals(1, toggleCount)
        } finally {
            unregister()
        }
    }
}
