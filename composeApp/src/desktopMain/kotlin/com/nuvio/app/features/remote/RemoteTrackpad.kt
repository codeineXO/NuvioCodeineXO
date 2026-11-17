package com.nuvio.app.features.remote

import com.nuvio.app.DesktopRemoteCursorController
import com.nuvio.app.composeInputComponent
import com.nuvio.app.features.player.desktop.DesktopHostOs
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Native pointer input and drag ownership are serialized on the AWT event thread. */
internal class RemoteTrackpad {
    private val robot by lazy { runCatching { Robot() }.getOrNull() }
    @Volatile private var closed = false
    private var dragOwner: Any? = null
    private var dragWindow: Frame? = null
    private val dragTimeout = Timer(1_500) { releaseDrag() }.apply { isRepeats = false }
    private val dragFocusListener = object : WindowAdapter() {
        override fun windowLostFocus(event: WindowEvent) = releaseDrag()
    }

    fun handle(session: Any, message: JsonObject) {
        if (closed || !available) return
        val action = (message["action"] as? JsonPrimitive)?.contentOrNull ?: return
        val dx = (message["dx"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
        val dy = (message["dy"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
        when (action) {
            "move", "wheel" -> if (dx == null || dy == null || (dx == 0.0 && dy == 0.0)) return
            "leftClick", "rightClick", "dragStart", "dragEnd", "dragKeepAlive" -> Unit
            else -> return
        }
        SwingUtilities.invokeLater {
            if (closed) return@invokeLater
            try {
                applyInput(session, action, dx ?: 0.0, dy ?: 0.0)
            } catch (_: RuntimeException) {
                if (dragOwner === session) releaseDrag()
            }
        }
    }

    fun disconnect(session: Any) {
        SwingUtilities.invokeLater { if (dragOwner === session) releaseDrag() }
    }

    fun close() {
        closed = true
        SwingUtilities.invokeLater(::releaseDrag)
    }

    private fun applyInput(session: Any, action: String, dx: Double, dy: Double) {
        if (action == "dragEnd") {
            if (dragOwner === session) releaseDrag()
            return
        }
        if (dragOwner != null && dragOwner !== session) return
        val input = robot ?: return
        val frame = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
        if (frame == null || !frame.isShowing || !frame.isFocused || frame.composeInputComponent() == null ||
            (dragWindow != null && dragWindow !== frame)
        ) {
            releaseDrag()
            return
        }
        val bounds = frame.locationOnScreen.let { location ->
            val insets = frame.insets
            Rectangle(location.x + insets.left, location.y + insets.top,
                frame.width - insets.left - insets.right, frame.height - insets.top - insets.bottom)
        }
        if (bounds.isEmpty) {
            releaseDrag()
            return
        }
        val inside = WindowsPointer.isInside(frame)
        if (!inside && (action != "move" || dragOwner != null)) {
            releaseDrag()
            return
        }
        when (action) {
            "move" -> {
                val pointer = MouseInfo.getPointerInfo()?.location ?: return
                val x = pointer.x.coerceIn(bounds.x, bounds.x + bounds.width - 1)
                val y = pointer.y.coerceIn(bounds.y, bounds.y + bounds.height - 1)
                DesktopRemoteCursorController.onMouseInput()
                input.mouseMove(
                    (x + dx.coerceIn(-256.0, 256.0).roundToInt()).coerceIn(bounds.x, bounds.x + bounds.width - 1),
                    (y + dy.coerceIn(-256.0, 256.0).roundToInt()).coerceIn(bounds.y, bounds.y + bounds.height - 1),
                )
                if (dragOwner != null) {
                    if (WindowsPointer.isInside(frame)) dragTimeout.restart() else releaseDrag()
                }
            }
            "dragKeepAlive" -> if (dragOwner === session) dragTimeout.restart()
            else -> {
                if (dragOwner != null) return
                DesktopRemoteCursorController.onMouseInput()
                when (action) {
                    "leftClick" -> click(input, InputEvent.BUTTON1_DOWN_MASK)
                    "rightClick" -> click(input, InputEvent.BUTTON3_DOWN_MASK)
                    "wheel" -> WindowsPointer.wheel(
                        dx.coerceIn(-960.0, 960.0).roundToInt(),
                        dy.coerceIn(-960.0, 960.0).roundToInt(),
                    )
                    "dragStart" -> {
                        dragOwner = session
                        dragWindow = frame
                        frame.addWindowFocusListener(dragFocusListener)
                        dragTimeout.restart()
                        input.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                    }
                }
            }
        }
    }

    private fun click(input: Robot, button: Int) {
        try {
            input.mousePress(button)
        } finally {
            input.mouseRelease(button)
        }
    }

    private fun releaseDrag() {
        if (dragOwner == null) return
        dragOwner = null
        dragWindow?.removeWindowFocusListener(dragFocusListener)
        dragWindow = null
        dragTimeout.stop()
        robot?.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    }

    companion object {
        val available: Boolean get() = DesktopHostOs.current == DesktopHostOs.WINDOWS
    }
}

/** Hit-test in native screen pixels so occluding windows and mixed display scaling are handled by Windows. */
private object WindowsPointer {
    private const val ROOT_OWNER = 3
    private const val MOUSEEVENTF_WHEEL = 0x0800
    private const val MOUSEEVENTF_HWHEEL = 0x1000

    @Structure.FieldOrder("x", "y")
    open class Point(@JvmField var x: Int = 0, @JvmField var y: Int = 0) : Structure() {
        class ByValue(x: Int = 0, y: Int = 0) : Point(x, y), Structure.ByValue
    }

    interface User32 : StdCallLibrary {
        fun GetForegroundWindow(): Pointer?
        fun GetCursorPos(point: Point): Boolean
        fun WindowFromPoint(point: Point.ByValue): Pointer?
        fun GetAncestor(window: Pointer, flags: Int): Pointer?
        fun ScreenToClient(window: Pointer, point: Point): Boolean
        fun GetClientRect(window: Pointer, rect: IntArray): Boolean
        fun mouse_event(flags: Int, dx: Int, dy: Int, data: Int, extraInfo: Pointer?)
    }

    private val user32 by lazy { runCatching { Native.load("user32", User32::class.java) }.getOrNull() }

    /**
     * Wheel input in Windows units (120 = one notch); fractions of a notch give smooth, high-resolution scrolling,
     * which Robot cannot send. Positive [dx] scrolls right and positive [dy] scrolls down.
     */
    fun wheel(dx: Int, dy: Int) {
        val api = user32 ?: return
        if (dy != 0) api.mouse_event(MOUSEEVENTF_WHEEL, 0, 0, -dy, null)
        if (dx != 0) api.mouse_event(MOUSEEVENTF_HWHEEL, 0, 0, dx, null)
    }

    fun isInside(frame: Frame): Boolean {
        val api = user32 ?: return false
        val hwnd = Native.getWindowPointer(frame)
        if (api.GetForegroundWindow() != hwnd) return false
        val point = Point()
        if (!api.GetCursorPos(point)) return false
        val underPointer = api.WindowFromPoint(Point.ByValue(point.x, point.y)) ?: return false
        if (api.GetAncestor(underPointer, ROOT_OWNER) != hwnd) return false
        val rect = IntArray(4)
        if (!api.ScreenToClient(hwnd, point) || !api.GetClientRect(hwnd, rect)) return false
        return point.x >= rect[0] && point.x < rect[2] && point.y >= rect[1] && point.y < rect[3]
    }
}
