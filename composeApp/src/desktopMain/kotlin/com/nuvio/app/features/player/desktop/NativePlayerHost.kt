package com.nuvio.app.features.player.desktop

import java.awt.Canvas
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

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
    private var placementWindow: Window? = null
    private var placementBounds: Rectangle? = null

    private companion object {
        val hiddenCursor: Cursor by lazy {
            val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
            Toolkit.getDefaultToolkit().createCustomCursor(image, Point(0, 0), "nuvio-hidden-cursor")
        }
    }

    init {
        isFocusable = true
        focusTraversalKeysEnabled = false
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
            updateWindowRegion()
            repaintChangedPlacement()
        }
        // SwingPanel moves its wrapper, so the Canvas itself need not receive componentMoved.
        addHierarchyBoundsListener(object : HierarchyBoundsAdapter() {
            override fun ancestorMoved(event: HierarchyEvent) = repaintChangedPlacement()
            override fun ancestorResized(event: HierarchyEvent) = repaintChangedPlacement()
        })
        addComponentListener(object : ComponentAdapter() {
            override fun componentMoved(event: ComponentEvent) = repaintChangedPlacement()

            override fun componentResized(event: ComponentEvent) {
                if (DesktopHostOs.current == DesktopHostOs.LINUX) {
                    repaint()
                    notifyFirstPaints()
                }
                updateWindowRegion()
                repaintChangedPlacement()
            }

            override fun componentShown(event: ComponentEvent) {
                if (DesktopHostOs.current == DesktopHostOs.LINUX) {
                    repaint()
                    notifyFirstPaints()
                }
                updateWindowRegion()
                repaintChangedPlacement()
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
        graphics.color = Color.BLACK
        graphics.fillRect(0, 0, width, height)
        notifyFirstPaints()
    }

    private fun repaintChangedPlacement() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        val window = SwingUtilities.getWindowAncestor(this)
        val bounds = if (window != null && isShowing) {
            // Both rectangles use AWT user coordinates; do not apply Compose/display scale again.
            SwingUtilities.convertRectangle(this, Rectangle(0, 0, width, height), window)
        } else null
        if (window === placementWindow && bounds == placementBounds) return
        val oldWindow = placementWindow
        val oldBounds = placementBounds
        placementWindow = window
        placementBounds = bounds
        // Request painting only. Relayout or moving HWNDs here would fight SwingPanel placement.
        if (oldWindow === window && bounds != null) {
            val dirty = oldBounds?.union(bounds) ?: bounds
            window?.repaint(dirty.x, dirty.y, dirty.width, dirty.height)
        } else {
            oldBounds?.let { oldWindow?.repaint(it.x, it.y, it.width, it.height) }
            bounds?.let { window?.repaint(it.x, it.y, it.width, it.height) }
        }
    }

    fun setMiniPlayerMode(isMini: Boolean, radiusPx: Int) {
        if (this.isMiniPlayerMode == isMini && this.cornerRadiusPx == radiusPx) return
        this.isMiniPlayerMode = isMini
        this.cornerRadiusPx = radiusPx
        updateWindowRegion()
        repaintChangedPlacement()
        repaint()
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
        repaintChangedPlacement()
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
        repaintChangedPlacement()
    }
}
