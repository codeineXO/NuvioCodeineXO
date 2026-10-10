package com.nuvio.app.features.player.desktop

import java.awt.Canvas
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage

internal class NativePlayerHost : Canvas() {
    var onPeerReady: (() -> Unit)? = null
    var onDisplayableChanged: ((Boolean) -> Unit)? = null
    var onFirstPaint: (() -> Unit)? = null
    var onFirstFullSizePaint: (() -> Unit)? = null
    var onCursorActivity: (() -> Unit)? = null
    private var firstPaintNotified = false
    private var firstFullSizePaintNotified = false
    private var controlsVisible = true
    private var cursorVisible = true

    private var isMiniPlayerMode = false
    private var cornerRadiusPx = 0

    private companion object {
        val hiddenCursor: Cursor by lazy {
            val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            Toolkit.getDefaultToolkit().createCustomCursor(image, Point(0, 0), "nuvio-hidden-cursor")
        }
    }

    init {
        background = Color.BLACK
        ignoreRepaint = false
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(event: MouseEvent) {
                noteCursorActivity()
            }

            override fun mouseDragged(event: MouseEvent) {
                noteCursorActivity()
            }
        })
        addHierarchyListener {
            syncAncestorBackgrounds()
            updateWindowRegion()
        }
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                if (DesktopHostOs.current == DesktopHostOs.LINUX) {
                    repaint()
                    notifyFirstPaints()
                }
                updateWindowRegion()
                syncAncestorBackgrounds()
            }

            override fun componentShown(event: ComponentEvent) {
                if (DesktopHostOs.current == DesktopHostOs.LINUX) {
                    repaint()
                    notifyFirstPaints()
                }
                updateWindowRegion()
                syncAncestorBackgrounds()
            }
        })
    }

    private fun notifyFirstPaints() {
        if (!firstPaintNotified) {
            firstPaintNotified = true
            onFirstPaint?.invoke()
        }
        if (!firstFullSizePaintNotified && width > 1 && height > 1) {
            firstFullSizePaintNotified = true
            onFirstFullSizePaint?.invoke()
        }
    }

    fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        setCursorVisible(visible)
    }

    fun noteCursorActivity() {
        onCursorActivity?.invoke()
    }

    fun resetCursorVisibility() {
        controlsVisible = true
        setCursorVisible(true)
    }

    private fun setCursorVisible(visible: Boolean) {
        if (cursorVisible == visible) return
        cursorVisible = visible
        cursor = if (visible) Cursor.getDefaultCursor() else hiddenCursor
    }

    override fun update(graphics: Graphics) {
        paint(graphics)
    }

    override fun paint(graphics: Graphics) {
        if (!isMiniPlayerMode) {
            graphics.color = Color.BLACK
            graphics.fillRect(0, 0, width, height)
        }
        notifyFirstPaints()
    }

    fun syncAncestorBackgrounds() {
        if (isMiniPlayerMode) {
            background = Color(0, 0, 0, 0)
        } else {
            background = Color.BLACK
        }
        var current: java.awt.Component? = parent
        while (current != null) {
            if (current is javax.swing.JComponent) {
                if (isMiniPlayerMode) {
                    current.isOpaque = false
                    current.background = java.awt.Color(0, 0, 0, 0)
                    current.border = null
                } else {
                    current.isOpaque = true
                    current.background = java.awt.Color.BLACK
                }
            }
            if (current is javax.swing.JRootPane) break
            current = current.parent
        }
    }

    fun setMiniPlayerMode(isMini: Boolean, radiusPx: Int) {
        if (this.isMiniPlayerMode == isMini && this.cornerRadiusPx == radiusPx) return
        this.isMiniPlayerMode = isMini
        this.cornerRadiusPx = radiusPx
        updateWindowRegion()
        syncAncestorBackgrounds()
        if (isMini) {
            javax.swing.Timer(50) { updateWindowRegion(); syncAncestorBackgrounds() }.apply { isRepeats = false; start() }
            javax.swing.Timer(150) { updateWindowRegion(); syncAncestorBackgrounds() }.apply { isRepeats = false; start() }
        }
    }

    fun updateWindowRegion() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        val hwnd = runCatching { AwtNativeViewResolver.resolveNativeViewPointer(this) }.getOrNull() ?: 0L
        if (hwnd == 0L) return
        if (isMiniPlayerMode && width > 1 && height > 1 && cornerRadiusPx > 0) {
            WindowsWindowRegionHelper.applyRoundedTopCorners(hwnd, width, height, cornerRadiusPx)
            java.awt.EventQueue.invokeLater {
                WindowsWindowRegionHelper.applyRoundedTopCorners(hwnd, width, height, cornerRadiusPx)
            }
        } else {
            WindowsWindowRegionHelper.clearWindowRegion(hwnd)
        }
    }

    override fun addNotify() {
        super.addNotify()
        onDisplayableChanged?.invoke(true)
        repaint()
        onPeerReady?.invoke()
        updateWindowRegion()
        syncAncestorBackgrounds()
    }

    override fun removeNotify() {
        val hwnd = runCatching { AwtNativeViewResolver.resolveNativeViewPointer(this) }.getOrNull() ?: 0L
        if (hwnd != 0L) {
            WindowsWindowRegionHelper.clearWindowRegion(hwnd)
        }
        onDisplayableChanged?.invoke(false)
        firstPaintNotified = false
        firstFullSizePaintNotified = false
        onPeerReady = null
        onFirstPaint = null
        onFirstFullSizePaint = null
        resetCursorVisibility()
        super.removeNotify()
    }
}
