package com.nuvio.app.features.player.desktop

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsDevice
import java.awt.Image
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Robot
import java.awt.Window
import javax.swing.JComponent
import javax.swing.JWindow
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.Timer
import co.touchlab.kermit.Logger

/**
 * A screenshot of the app window, held in its own top-level window over it while the native player window moves on or
 * off screen. The native player draws above everything inside the app window, so nothing there can hide the frames
 * it shows black while it repaints or shuts down; a separate window above the app can.
 */
internal class NativeTransitionShield private constructor(private val window: JWindow) {
    private var fadeTimer: Timer? = null
    private var disposed = false

    /** Fades the shield away over [durationMs], then disposes it. Call on the EDT. */
    fun fadeOut(durationMs: Int) {
        if (disposed || fadeTimer != null) return
        val translucent = runCatching {
            window.graphicsConfiguration.device
                .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)
        }.getOrDefault(false)
        if (!translucent || durationMs <= 0) {
            dispose()
            return
        }
        val startedAt = System.nanoTime()
        fadeTimer = Timer(FRAME_MS) {
            val t = ((System.nanoTime() - startedAt) / 1_000_000f / durationMs).coerceIn(0f, 1f)
            if (t >= 1f) {
                dispose()
            } else {
                // Ease in: hold most of the picture at first, so the page beneath has a moment to finish drawing.
                runCatching { window.opacity = 1f - t * t }
            }
        }.apply { start() }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        fadeTimer?.stop()
        fadeTimer = null
        window.dispose()
    }

    companion object {
        private const val FRAME_MS = 16
        private val log = Logger.withTag("NativeTransitionShield")

        /**
         * Captures the content area of [component]'s window and shows it over the window at once. Returns null when
         * there is nothing on screen to capture or the capture fails. Call on the EDT.
         */
        fun cover(component: Component): NativeTransitionShield? = runCatching {
            val owner: Window = SwingUtilities.getWindowAncestor(component) ?: return null
            if (!owner.isShowing) return null
            val content: Component = (owner as? RootPaneContainer)?.contentPane ?: owner
            if (!content.isShowing || content.width <= 0 || content.height <= 0) return null
            val origin = content.locationOnScreen
            val area = Rectangle(origin.x, origin.y, content.width, content.height)
            val capture = Robot().createMultiResolutionScreenCapture(area)
            val image: Image = capture.resolutionVariants.maxByOrNull { it.getWidth(null) } ?: return null
            val picture = object : JComponent() {
                override fun paintComponent(graphics: Graphics) {
                    val g = graphics as Graphics2D
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                    g.drawImage(image, 0, 0, width, height, null)
                }
            }.apply { isOpaque = true }
            val window = JWindow(owner).apply {
                focusableWindowState = false
                isAutoRequestFocus = false
                background = Color.BLACK
                contentPane = picture
                bounds = area
                isVisible = true
            }
            // Paint now rather than on a later event, so the shield is up before the native window moves.
            picture.paintImmediately(0, 0, picture.width, picture.height)
            NativeTransitionShield(window)
        }.onFailure { error -> log.w(error) { "transition shield unavailable" } }.getOrNull()
    }
}
