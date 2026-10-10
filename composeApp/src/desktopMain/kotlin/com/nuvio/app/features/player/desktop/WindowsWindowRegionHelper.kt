package com.nuvio.app.features.player.desktop

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

internal object WindowsWindowRegionHelper {
    private interface User32Library : Library {
        fun SetWindowRgn(hWnd: Pointer?, hRgn: Pointer?, bRedraw: Boolean): Int
        fun EnumChildWindows(hWndParent: Pointer?, lpEnumFunc: WNDENUMPROC?, lParam: Pointer?): Boolean
        fun GetClientRect(hWnd: Pointer?, lpRect: IntArray): Boolean
    }

    private fun interface WNDENUMPROC : Callback {
        fun callback(hWnd: Pointer?, lParam: Pointer?): Boolean
    }

    private interface Gdi32Library : Library {
        fun CreateRoundRectRgn(x1: Int, y1: Int, x2: Int, y2: Int, w: Int, h: Int): Pointer?
        fun CreateRectRgn(x1: Int, y1: Int, x2: Int, y2: Int): Pointer?
        fun CombineRgn(hrgnDst: Pointer?, hrgnSrc1: Pointer?, hrgnSrc2: Pointer?, fnCombineMode: Int): Int
        fun DeleteObject(hObject: Pointer?): Boolean
    }

    private val user32: User32Library? by lazy {
        runCatching { Native.load("user32", User32Library::class.java) }.getOrNull()
    }

    private val gdi32: Gdi32Library? by lazy {
        runCatching { Native.load("gdi32", Gdi32Library::class.java) }.getOrNull()
    }

    private const val RGN_OR = 2

    fun applyRoundedTopCorners(hwndVal: Long, width: Int, height: Int, radiusPx: Int) {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS || hwndVal == 0L || width <= 1 || height <= 1 || radiusPx <= 0) return
        val u32 = user32 ?: return
        val g32 = gdi32 ?: return

        runCatching {
            val hwndPtr = Pointer.createConstant(hwndVal)
            applyRegion(u32, g32, hwndPtr, width, height, radiusPx)

            u32.EnumChildWindows(hwndPtr, { childHwnd, _ ->
                if (childHwnd != null) {
                    val rect = IntArray(4)
                    if (u32.GetClientRect(childHwnd, rect)) {
                        val childW = rect[2] - rect[0]
                        val childH = rect[3] - rect[1]
                        if (childW > 1 && childH > 1) {
                            applyRegion(u32, g32, childHwnd, childW, childH, radiusPx)
                        }
                    }
                }
                true
            }, null)
        }
    }

    fun clearWindowRegion(hwndVal: Long) {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS || hwndVal == 0L) return
        val u32 = user32 ?: return

        runCatching {
            val hwndPtr = Pointer.createConstant(hwndVal)
            u32.SetWindowRgn(hwndPtr, null, true)
            u32.EnumChildWindows(hwndPtr, { childHwnd, _ ->
                if (childHwnd != null) {
                    u32.SetWindowRgn(childHwnd, null, true)
                }
                true
            }, null)
        }
    }

    private fun applyRegion(
        u32: User32Library,
        g32: Gdi32Library,
        targetHwnd: Pointer,
        width: Int,
        height: Int,
        radiusPx: Int,
    ) {
        val rgnRound = g32.CreateRoundRectRgn(0, 0, width + 1, height + 1, radiusPx * 2, radiusPx * 2) ?: return
        val rgnBottom = g32.CreateRectRgn(0, height / 2, width + 1, height + 1)
        val rgnCombined = g32.CreateRectRgn(0, 0, 0, 0)

        if (rgnBottom != null && rgnCombined != null) {
            g32.CombineRgn(rgnCombined, rgnRound, rgnBottom, RGN_OR)
            u32.SetWindowRgn(targetHwnd, rgnCombined, true)
            g32.DeleteObject(rgnRound)
            g32.DeleteObject(rgnBottom)
        } else {
            u32.SetWindowRgn(targetHwnd, rgnRound, true)
            rgnBottom?.let { g32.DeleteObject(it) }
            rgnCombined?.let { g32.DeleteObject(it) }
        }
    }
}
