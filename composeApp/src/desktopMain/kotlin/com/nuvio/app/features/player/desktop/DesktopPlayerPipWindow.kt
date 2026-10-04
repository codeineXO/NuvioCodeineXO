package com.nuvio.app.features.player.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.awt.Panel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JFrame
import javax.swing.WindowConstants

/** Borderless, always-on-top video surface used by desktop PiP. */
internal class DesktopPlayerPipWindow(
    private val onCloseRequested: () -> Unit,
    private val onResized: () -> Unit = {},
    private val onFocusGained: () -> Unit = {},
) : JFrame() {
    /** Heavyweight host required by the native HWND/NSView reparenting bridge. */
    val videoHolderPanel = Panel(BorderLayout())

    var aspectRatio: Float = 16f / 9f

    override fun getInsets(): Insets = Insets(0, 0, 0, 0)

    fun updateWindowShape() {
        val w = width
        val h = height
        if (w > 0 && h > 0) {
            runCatching {
                shape = RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), 16f, 16f)
            }
        }
    }

    init {
        isUndecorated = true
        isResizable = false
        focusableWindowState = true
        background = Color.BLACK
        rootPane.border = null
        minimumSize = Dimension(320, 180)
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        videoHolderPanel.background = Color.BLACK
        contentPane = videoHolderPanel
        title = ""
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) {
                onCloseRequested()
            }
        })
        addWindowFocusListener(object : WindowAdapter() {
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
        addComponentListener(object : ComponentAdapter() {
            private var resizing = false

            override fun componentResized(event: ComponentEvent) {
                updateWindowShape()
                if (DesktopHostOs.current == DesktopHostOs.WINDOWS) {
                    return
                }
                if (resizing) return
                resizing = true
                try {
                    val width = width.coerceAtLeast(minimumSize.width)
                    val height = (width / aspectRatio).toInt().coerceAtLeast(minimumSize.height)
                    if (this@DesktopPlayerPipWindow.width != width ||
                        this@DesktopPlayerPipWindow.height != height
                    ) {
                        setSize(width, height)
                    }
                } finally {
                    resizing = false
                }
                onResized()
            }
        })
    }

    fun updateWindowTitle(windowTitle: String) {
        title = windowTitle
    }
}
