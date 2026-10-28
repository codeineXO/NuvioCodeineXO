package com.nuvio.app.features.player.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import java.awt.EventQueue
import java.awt.Rectangle
import javax.swing.JLayeredPane
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.floor

/** A heavyweight peer already draws above Compose; it must not also punch a moving Skia hole. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun WindowsPlayerSurface(
    host: NativePlayerHost,
    modifier: Modifier,
    onResized: () -> Unit,
) {
    val window = requireNotNull(LocalAwtWindow.current)
    val pane = (window as RootPaneContainer).layeredPane
    val density = LocalDensity.current.density
    val placement = remember(host, window) { WindowsPlayerPlacement(host, pane) }

    DisposableEffect(placement) {
        placement.attach()
        onDispose { placement.detach() }
    }
    Box(modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        // Compose pixels -> AWT user coordinates, including non-integral display scales.
        val left = floor(bounds.left / density).toInt()
        val top = floor(bounds.top / density).toInt()
        val right = ceil(bounds.right / density).toInt()
        val bottom = ceil(bounds.bottom / density).toInt()
        val position = SwingUtilities.convertPoint(window, left, top, pane)
        placement.place(Rectangle(position.x, position.y, right - left, bottom - top), onResized)
    })
}

private class WindowsPlayerPlacement(
    private val host: NativePlayerHost,
    private val pane: JLayeredPane,
) {
    private var attached = false
    private var requestedBounds: Rectangle? = null
    private var resized: () -> Unit = {}

    fun attach() {
        check(EventQueue.isDispatchThread())
        attached = true
        host.isVisible = false
        pane.add(host, Integer.valueOf(JLayeredPane.PALETTE_LAYER))
        applyPlacement()
    }

    fun detach() {
        check(EventQueue.isDispatchThread())
        attached = false
        val oldBounds = host.bounds
        pane.remove(host)
        pane.repaint(oldBounds.x, oldBounds.y, oldBounds.width, oldBounds.height)
    }

    fun place(bounds: Rectangle, onResized: () -> Unit) {
        check(EventQueue.isDispatchThread())
        requestedBounds = bounds
        resized = onResized
        applyPlacement()
    }

    private fun applyPlacement() {
        if (!attached) return
        val bounds = requestedBounds ?: return
        val oldBounds = host.bounds
        val sizeChanged = oldBounds.size != bounds.size
        if (oldBounds != bounds) {
            host.bounds = bounds
            val dirty = oldBounds.union(bounds)
            pane.repaint(dirty.x, dirty.y, dirty.width, dirty.height)
        }
        host.isVisible = !bounds.isEmpty
        if (sizeChanged) resized()
    }
}
