package com.nuvio.app.features.player.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.awt.Panel
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.RootPaneContainer
import javax.swing.WindowConstants

/** A borderless window that hosts the native video surface outside the main player page. */
internal interface DesktopPipSurface {
    val window: Window
    /** Heavyweight host required by the native HWND/NSView reparenting bridge. */
    val videoHolderPanel: Panel
    var aspectRatio: Float
    fun updateWindowShape()
    fun updateWindowTitle(windowTitle: String)
}

/** Floating PiP: its own always-on-top, taskbar-visible window while the app is hidden. */
internal class DesktopPlayerPipWindow(
    onCloseRequested: () -> Unit,
    onResized: () -> Unit = {},
    onFocusGained: () -> Unit = {},
) : JFrame(), DesktopPipSurface {
    override val window: Window get() = this
    override val videoHolderPanel = Panel(BorderLayout())
    override var aspectRatio: Float = 16f / 9f

    override fun getInsets(): Insets = Insets(0, 0, 0, 0)

    override fun updateWindowShape() = applyRoundedShape()

    override fun updateWindowTitle(windowTitle: String) {
        title = windowTitle
    }

    init {
        isUndecorated = true
        isResizable = false
        focusableWindowState = true
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        title = ""
        installPipSurface(this, videoHolderPanel, onCloseRequested, onResized, onFocusGained)
    }
}

/**
 * Docked mini player: owned by the main window, so it stays above the app (and only the app), follows it
 * when minimized, and leaves the app usable underneath.
 */
internal class DesktopPlayerDockedWindow(
    owner: Window,
    onCloseRequested: () -> Unit,
    onResized: () -> Unit = {},
    onFocusGained: () -> Unit = {},
) : JDialog(owner, ModalityType.MODELESS), DesktopPipSurface {
    override val window: Window get() = this
    override val videoHolderPanel = Panel(BorderLayout())
    override var aspectRatio: Float = 16f / 9f

    override fun getInsets(): Insets = Insets(0, 0, 0, 0)

    override fun updateWindowShape() = applyRoundedShape()

    override fun updateWindowTitle(windowTitle: String) {
        title = windowTitle
    }

    init {
        isUndecorated = true
        isResizable = false
        focusableWindowState = true
        // Docking must not pull keyboard focus away from the page the user is browsing.
        isAutoRequestFocus = false
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        title = ""
        installPipSurface(this, videoHolderPanel, onCloseRequested, onResized, onFocusGained)
    }
}

private fun Window.applyRoundedShape() {
    val w = width
    val h = height
    if (w > 0 && h > 0) {
        runCatching {
            shape = RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), 16f, 16f)
        }
    }
}

private fun <T> installPipSurface(
    window: T,
    videoHolderPanel: Panel,
    onCloseRequested: () -> Unit,
    onResized: () -> Unit,
    onFocusGained: () -> Unit,
) where T : Window, T : RootPaneContainer {
    window.background = Color.BLACK
    window.rootPane.border = null
    window.minimumSize = Dimension(320, 180)
    videoHolderPanel.background = Color.BLACK
    window.contentPane = videoHolderPanel
    window.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(event: WindowEvent) {
            onCloseRequested()
        }
    })
    window.addWindowFocusListener(object : WindowAdapter() {
        override fun windowGainedFocus(event: WindowEvent) {
            onFocusGained()
        }
    })
    videoHolderPanel.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
            if (DesktopHostOs.current != DesktopHostOs.WINDOWS) {
                onResized()
            }
        }
    })
    window.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(event: ComponentEvent) {
            window.applyRoundedShape()
        }
    })
    // No Java-side aspect correction on macOS: the native bridge applies the aspect
    // lock while it tracks the resize. Correcting here reads AWT bounds on the EDT
    // while AppKit keeps resizing, so it re-applies stale sizes and the window jitters.
}
