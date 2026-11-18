package com.nuvio.app.features.player.desktop

import androidx.compose.ui.unit.IntSize
import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.InAppPlayerManager
import com.nuvio.app.features.settings.AppIconRepository
import java.awt.Dimension
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates the PiP windows without moving the Compose/AWT Canvas. The native video surface moves either to a
 * floating always-on-top window (the app hides) or to a window docked over the app (the in-app mini player).
 */
internal object DesktopPlayerPictureInPicture {
    private val log = Logger.withTag("DesktopPlayerPiP")
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** True while the video plays in either PiP window. */
    @Volatile
    var isEnabled: Boolean = false
        private set

    /** True while that window is the docked mini player rather than floating PiP. */
    @Volatile
    var isDocked: Boolean = false
        private set

    val supportsDocking: Boolean get() = isSupportedHost()

    private var host: NativePlayerHost? = null
    private var controller: NativePlayerController? = null
    internal var pipWindow: DesktopPipSurface? = null
    private var mainWindow: Window? = null
    private var windowTitle = ""
    private var lastVideoSize = IntSize.Zero
    private var transition = false
    private var lastToggleAtMs = 0L

    // The docked window keeps its place and width for the rest of the session.
    private var dockOffset = Point(DOCK_MARGIN, DOCK_MARGIN)
    private var dockWidth = DOCK_DEFAULT_WIDTH
    private var ownerListener: ComponentListener? = null
    private val dockSettleTimer = Timer(DOCK_SETTLE_MS) { settleDockedWindow() }.apply { isRepeats = false }

    fun setHost(value: NativePlayerHost) = onEdt {
        host = value
    }

    fun setController(value: NativePlayerController) = onEdt {
        controller = value
    }

    fun setWindowTitle(title: String) = onEdt {
        windowTitle = title
        pipWindow?.updateWindowTitle(title)
    }

    fun update(isPlaying: Boolean, videoSize: IntSize) = onEdt {
        lastVideoSize = videoSize
        pipWindow?.let { surface ->
            if (videoSize.width > 0 && videoSize.height > 0) {
                val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                if (surface.aspectRatio == ratio) return@let
                surface.aspectRatio = ratio
                runCatching {
                    val windowPointer = AwtNativeViewResolver.resolveNativeViewPointer(surface.window)
                    NativePlayerBridge.setWindowAspectRatio(windowPointer, ratio)
                }.onFailure { error -> log.w(error) { "failed to update PiP aspect ratio" } }
                surface.window.bounds = desktopPipBoundsForAspectRatio(surface.window.bounds, ratio)
                surface.window.revalidate()
                if (isDocked) positionDockedWindow()
            }
        }
    }

    fun resizeWindow(encodedValue: Double) = onEdt {
        val surface = pipWindow ?: return@onEdt
        val win = surface.window
        val anchor = (encodedValue / 100000.0).toInt()
        val targetWidth = (encodedValue % 100000.0).toInt().coerceIn(320, 1920)
        val targetHeight = (targetWidth / surface.aspectRatio).toInt()
        val b = win.bounds
        val newX = when (anchor) {
            1, 3 -> b.x + b.width - targetWidth
            else -> b.x
        }
        val newY = when (anchor) {
            1, 2 -> b.y + b.height - targetHeight
            else -> b.y
        }
        win.setBounds(newX, newY, targetWidth, targetHeight)
        surface.updateWindowShape()
        win.revalidate()
    }

    fun toggle() = onEdt {
        if (!isSupportedHost()) return@onEdt
        if (transition) return@onEdt
        val now = System.currentTimeMillis()
        if (now - lastToggleAtMs < 300L) return@onEdt
        lastToggleAtMs = now
        log.d { "toggle enabled=$isEnabled docked=$isDocked host=${host != null} controller=${controller != null}" }
        when {
            // The docked window's "leave PiP" gestures expand the player. Move the video home first, like PiP restore,
            // so the full player page appears with the picture already in place instead of an empty frame.
            isEnabled && isDocked -> {
                val player = controller
                restoreOnEdt()
                InAppPlayerManager.expand()
                player?.requestKeyboardFocus()
            }
            isEnabled -> restoreOnEdt()
            else -> enterOnEdt(docked = false)
        }
    }

    /**
     * Moves the video into a window docked over the app, then runs [onDocked]. Minimizing docks first so the page
     * collapses with the picture already in the mini window. If docking fails the player stays (or returns) full.
     */
    fun enterDocked(onDocked: (() -> Unit)? = null) = onEdt {
        if (!isSupportedHost() || transition) return@onEdt
        if (isEnabled && !isDocked) restoreOnEdt()
        if (!isEnabled) enterOnEdt(docked = true)
        if (isEnabled && isDocked) {
            onDocked?.invoke()
        } else {
            log.w { "docked mini player unavailable; keeping the full player" }
            if (onDocked == null) InAppPlayerManager.expand()
        }
    }

    fun exitDocked() = onEdt {
        if (transition || !isEnabled || !isDocked) return@onEdt
        restoreOnEdt()
    }

    fun clear() = onEdt {
        if (transition || !isEnabled) return@onEdt
        restoreOnEdt()
    }

    fun release() = onEdt {
        transition = true
        val surface = pipWindow
        val mainWin = mainWindow
        detachDockListeners(mainWin)
        pipWindow = null
        mainWindow = null
        isEnabled = false
        isDocked = false
        controller?.setPictureInPictureActive(false)
        surface?.window?.dispose()
        if (mainWin != null && !mainWin.isVisible) {
            mainWin.isVisible = true
            mainWin.deiconify()
            mainWin.toFront()
            mainWin.requestFocus()
        }
        host = null
        controller = null
        lastVideoSize = IntSize.Zero
        transition = false
        notifyChanged()
    }

    private fun enterOnEdt(docked: Boolean) {
        val mainHost = host ?: return
        val player = controller ?: return
        if (!mainHost.isDisplayable) return
        val owner = SwingUtilities.getWindowAncestor(mainHost) ?: currentWindow()
        if (docked && owner == null) return

        transition = true
        mainWindow = owner
        val surface: DesktopPipSurface = if (docked) {
            DesktopPlayerDockedWindow(
                owner = owner!!,
                // Closing the docked window (Alt+F4) stops playback, like the mini player's close button.
                onCloseRequested = { InAppPlayerManager.close() },
                onResized = { controller?.layoutNativeSubviews() },
                onFocusGained = { controller?.requestKeyboardFocus() },
            )
        } else {
            DesktopPlayerPipWindow(
                onCloseRequested = ::clear,
                onResized = { controller?.layoutNativeSubviews() },
                onFocusGained = { controller?.requestKeyboardFocus() },
            )
        }
        val window = surface.window
        surface.aspectRatio = videoAspectRatio()
        surface.updateWindowTitle(windowTitle)
        window.bounds = if (docked) dockedBounds(owner!!, surface.aspectRatio) else computeDefaultBounds(owner, surface.aspectRatio)
        if (!docked) {
            window.isAlwaysOnTop = true
            AppIconRepository.ensureLoaded()
            val iconKey = AppIconRepository.state.value.selected.key
            runCatching {
                val iconPath = "icons/app-icon-${iconKey}-transparent.png"
                val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(iconPath)
                stream?.use { ImageIO.read(it) }?.let { image -> window.iconImages = listOf(image) }
            }.onFailure { error -> log.w(error) { "failed to set PiP window icon key=$iconKey" } }
        }
        window.isVisible = true
        surface.updateWindowShape()
        pipWindow = surface

        runCatching {
            val windowPointer = AwtNativeViewResolver.resolveNativeViewPointer(window)
            NativePlayerBridge.setWindowAspectRatio(windowPointer, surface.aspectRatio)
            NativePlayerBridge.setWindowResizable(windowPointer, false)
            window.validate()
        }.onFailure { error -> log.w(error) { "failed to set PiP window borderless" } }

        val pipHost = surface.videoHolderPanel
        log.d { "created window docked=$docked displayable=${window.isDisplayable} visible=${window.isVisible} pipHostDisplayable=${pipHost.isDisplayable}" }
        if (!pipHost.isDisplayable || !player.reparentSurface(pipHost)) {
            log.w { "native surface reparent failed; closing PiP window" }
            window.dispose()
            pipWindow = null
            mainWindow = null
            transition = false
            return
        }
        player.setPictureInPictureActive(true)
        isEnabled = true
        isDocked = docked
        log.d { "PiP entered docked=$docked" }
        if (docked) {
            attachDockListeners(owner!!, window)
        } else {
            owner?.isVisible = false
        }
        transition = false
        notifyChanged()
        if (!docked) {
            window.toFront()
            window.requestFocus()
        }
    }

    private fun restoreOnEdt() {
        val surface = pipWindow ?: return
        val window = surface.window
        val mainHost = host
        val player = controller
        val mainWin = mainWindow

        transition = true
        detachDockListeners(mainWin)
        if (mainWin != null) {
            mainWin.isVisible = true
            mainWin.deiconify()
            mainWin.toFront()
            mainWin.requestFocus()
        }
        if (mainHost != null && mainHost.isDisplayable && player != null) {
            val restored = player.reparentSurface(mainHost)
            log.d { "restoring PiP native surface success=$restored" }
            mainHost.requestFocusInWindow()
        }
        player?.setPictureInPictureActive(false)
        runCatching {
            val windowPointer = AwtNativeViewResolver.resolveNativeViewPointer(window)
            NativePlayerBridge.setWindowResizable(windowPointer, false)
        }
        window.isVisible = false
        window.dispose()
        pipWindow = null
        mainWindow = null
        isEnabled = false
        isDocked = false
        transition = false
        notifyChanged()
    }

    private fun attachDockListeners(owner: Window, window: Window) {
        val listener = object : ComponentAdapter() {
            override fun componentMoved(event: ComponentEvent) = positionDockedWindow()
            override fun componentResized(event: ComponentEvent) = positionDockedWindow()
        }
        owner.addComponentListener(listener)
        ownerListener = listener
        window.addComponentListener(object : ComponentAdapter() {
            // The user drags the window with the OS move loop; remember where it went, then pull it back inside
            // the app once the drag settles.
            override fun componentMoved(event: ComponentEvent) {
                rememberDockPlacement()
                dockSettleTimer.restart()
            }

            override fun componentResized(event: ComponentEvent) {
                if (window.width > 0) dockWidth = window.width
                rememberDockPlacement()
                dockSettleTimer.restart()
            }
        })
    }

    private fun detachDockListeners(owner: Window?) {
        dockSettleTimer.stop()
        ownerListener?.let { owner?.removeComponentListener(it) }
        ownerListener = null
    }

    private fun rememberDockPlacement() {
        if (!isDocked) return
        val window = pipWindow?.window ?: return
        val area = mainWindow?.usableContentArea() ?: return
        val bounds = window.bounds
        dockOffset = Point(
            area.x + area.width - (bounds.x + bounds.width),
            area.y + area.height - (bounds.y + bounds.height),
        )
    }

    private fun settleDockedWindow() {
        if (!isDocked || transition) return
        positionDockedWindow()
    }

    private fun positionDockedWindow() {
        if (!isDocked) return
        val window = pipWindow?.window ?: return
        val area = mainWindow?.usableContentArea() ?: return
        val target = desktopDockedPipBounds(area, window.size, dockOffset, DOCK_MARGIN)
        if (target.location != window.location) window.location = target.location
    }

    private fun dockedBounds(owner: Window, aspectRatio: Float): Rectangle {
        val area = owner.contentArea()
        val width = dockWidth.coerceIn(DOCK_MIN_WIDTH, maxOf(DOCK_MIN_WIDTH, area.width / 2))
        val size = desktopPipBoundsForAspectRatio(Rectangle(0, 0, width, (width / aspectRatio).roundToInt()), aspectRatio)
        return desktopDockedPipBounds(area, Dimension(size.width, size.height), dockOffset, DOCK_MARGIN)
    }

    private fun Window.contentArea(): Rectangle {
        val b = bounds
        val i = insets
        return Rectangle(b.x + i.left, b.y + i.top, b.width - i.left - i.right, b.height - i.top - i.bottom)
    }

    // A minimized window reports a parked, tiny area; leave the docked placement alone until it is back.
    private fun Window.usableContentArea(): Rectangle? {
        if (!isShowing) return null
        if (this is Frame && extendedState and Frame.ICONIFIED != 0) return null
        return contentArea().takeIf { it.width >= DOCK_MIN_WIDTH && it.height >= DOCK_MIN_WIDTH / 2 }
    }

    private fun Window.deiconify() {
        if (this is Frame) {
            val state = extendedState
            if (state and Frame.ICONIFIED != 0) {
                extendedState = state and Frame.ICONIFIED.inv()
            }
        }
    }

    private fun isSupportedHost(): Boolean =
        DesktopHostOs.current == DesktopHostOs.WINDOWS ||
            DesktopHostOs.current == DesktopHostOs.MACOS

    private fun videoAspectRatio(): Float =
        if (lastVideoSize.width > 0 && lastVideoSize.height > 0) {
            lastVideoSize.width.toFloat() / lastVideoSize.height.toFloat()
        } else {
            16f / 9f
        }

    private fun computeDefaultBounds(window: Window?, aspectRatio: Float): Rectangle {
        val configuration = window?.graphicsConfiguration
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val screen = configuration.bounds
        val insets = ToolkitInsets.get(configuration)
        val size = desktopPipBoundsForAspectRatio(Rectangle(0, 0, 480, 180), aspectRatio)
        return Rectangle(
            screen.x + screen.width - insets.right - size.width - 24,
            screen.y + screen.height - insets.bottom - size.height - 24,
            size.width,
            size.height,
        )
    }

    private fun currentWindow(): Window? =
        KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            ?: Window.getWindows().firstOrNull { it.isDisplayable && it.isVisible }

    private fun onEdt(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) action() else SwingUtilities.invokeLater(action)
    }

    private fun notifyChanged() {
        _changes.value += 1
    }

    private const val DOCK_MARGIN = 24
    private const val DOCK_MIN_WIDTH = 320
    private const val DOCK_DEFAULT_WIDTH = 400
    private const val DOCK_SETTLE_MS = 200
}

private object ToolkitInsets {
    fun get(configuration: java.awt.GraphicsConfiguration): java.awt.Insets =
        runCatching { java.awt.Toolkit.getDefaultToolkit().getScreenInsets(configuration) }
            .getOrElse { java.awt.Insets(0, 0, 0, 0) }
}
