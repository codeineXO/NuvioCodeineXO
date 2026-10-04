package com.nuvio.app.features.player.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Panel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.WindowConstants

/** Borderless, always-on-top video surface used by desktop PiP. */
internal class DesktopPlayerPipWindow(
    private val onCloseRequested: () -> Unit,
) : JFrame() {
    /** Heavyweight host required by the native HWND/NSView reparenting bridge. */
    val videoHolderPanel = Panel(BorderLayout())

    var aspectRatio: Float = 16f / 9f

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

        addComponentListener(object : ComponentAdapter() {
            private var resizing = false

            override fun componentResized(event: ComponentEvent) {
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
            }
        })
    }

    fun updateWindowTitle(windowTitle: String) {
        title = windowTitle
    }
}
