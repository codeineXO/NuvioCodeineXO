package com.nuvio.app.features.player.desktop

import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.player.SubtitleOutlineEffect
import com.nuvio.app.features.player.SubtitleStyleState
import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.util.Locale
import kotlin.math.roundToInt

internal object WindowsMpvSubStyleHelper {
    private var initialized = false
    private var setPropertyStringHandle: MethodHandle? = null
    private var clientNameHandle: MethodHandle? = null

    @Synchronized
    private fun ensureInitialized(): Boolean {
        if (initialized) return setPropertyStringHandle != null
        initialized = true

        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return false

        return runCatching {
            val linker = Linker.nativeLinker()
            val candidates = listOfNotNull(
                File("composeApp/build/native/windows/libmpv-2.dll"),
                File("build/native/windows/libmpv-2.dll"),
                File("composeApp/build/native/windows-runtime/libmpv-2.dll"),
                File("build/native/windows-runtime/libmpv-2.dll"),
                File("src/desktopMain/native/windows/runtime/libmpv-2.dll"),
                File("composeApp/src/desktopMain/native/windows/runtime/libmpv-2.dll"),
                runCatching {
                    File(DesktopStorage.cacheDir.toString()).resolve("native-player-bridge/windows")
                        .walkTopDown().firstOrNull { it.name.equals("libmpv-2.dll", ignoreCase = true) }
                }.getOrNull(),
                File("C:/msys64/ucrt64/bin/libmpv-2.dll"),
                File("C:/Program Files (x86)/Nuvio/app/native/libmpv-2.dll"),
            )
            val dllFile = candidates.firstOrNull { it.exists() }
                ?: System.getProperty("compose.application.resources.dir")
                    ?.takeIf(String::isNotBlank)
                    ?.let { File(it, "native/windows/libmpv-2.dll") }
                    ?.takeIf { it.exists() }
                ?: System.getenv("NUVIO_LIBMPV_PATH")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::File)
                    ?.takeIf { it.exists() }

            if (dllFile == null) return false

            val arena = Arena.ofAuto()
            val lookup = SymbolLookup.libraryLookup(dllFile.toPath(), arena)

            val setPropSym = lookup.find("mpv_set_property_string").orElse(null) ?: return false
            val cNameSym = lookup.find("mpv_client_name").orElse(null) ?: return false

            setPropertyStringHandle = linker.downcallHandle(
                setPropSym,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
            )

            clientNameHandle = linker.downcallHandle(
                cNameSym,
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
            )
            true
        }.getOrDefault(false)
    }

    private fun resolveMpvHandle(bridgeHandle: Long): MemorySegment? {
        if (bridgeHandle == 0L) return null
        return runCatching {
            // handle is std::shared_ptr<WindowsMpvWebPlayer>*
            val playerPtr = MemorySegment.ofAddress(bridgeHandle)
                .reinterpret(16)
                .get(ValueLayout.JAVA_LONG, 0L)
            if (playerPtr == 0L) return null

            // mpv_handle* is at offset 0x130 (304 bytes) inside WindowsMpvWebPlayer
            val mpvPtr = MemorySegment.ofAddress(playerPtr)
                .reinterpret(0x140)
                .get(ValueLayout.JAVA_LONG, 0x130L)
            if (mpvPtr == 0L) return null

            val mpvSeg = MemorySegment.ofAddress(mpvPtr)

            // Validate that this is indeed an mpv_handle by checking client name
            val cNameSeg = clientNameHandle?.invoke(mpvSeg) as? MemorySegment
            if (cNameSeg == null || cNameSeg.address() == 0L) return null
            val name = cNameSeg.reinterpret(64).getUtf8String(0)
            if (name.isNullOrBlank()) return null

            mpvSeg
        }.getOrNull()
    }

    fun applyExtendedSubtitleStyle(bridgeHandle: Long, style: SubtitleStyleState, useLibass: Boolean) {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        if (useLibass) return // libass handles its own internal styling
        if (!ensureInitialized()) return
        val mpvCtx = resolveMpvHandle(bridgeHandle) ?: return

        runCatching {
            Arena.ofConfined().use { arena ->
                fun setProp(name: String, value: String) {
                    val pName = arena.allocateUtf8String(name)
                    val pVal = arena.allocateUtf8String(value)
                    setPropertyStringHandle?.invoke(mpvCtx, pName, pVal)
                }

                // 1. Set font
                if (style.fontName.isNotBlank()) {
                    setProp("sub-font", style.fontName)
                }

                // 2. Set shadow color
                val shadowColorStr = style.outlineColor.toMpvHexColor()
                setProp("sub-shadow-color", shadowColorStr)

                // 3. Set outline effect & thickness
                val width = style.outlineWidth.coerceIn(1, 8)
                when (style.outlineEffect) {
                    SubtitleOutlineEffect.NONE -> {
                        setProp("sub-border-style", "outline-and-shadow")
                        setProp("sub-outline-size", "0")
                        setProp("sub-shadow-offset", "0")
                        setProp("sub-blur", "0")
                    }
                    SubtitleOutlineEffect.OUTLINE -> {
                        val size = if (style.outlineEnabled) width.toString() else "0"
                        setProp("sub-border-style", "outline-and-shadow")
                        setProp("sub-outline-size", size)
                        setProp("sub-shadow-offset", "0")
                        setProp("sub-blur", "0")
                    }
                    SubtitleOutlineEffect.DROP_SHADOW -> {
                        setProp("sub-border-style", "outline-and-shadow")
                        setProp("sub-outline-size", "0")
                        val offset = (width + 1).coerceIn(2, 6).toString()
                        setProp("sub-shadow-offset", offset)
                        setProp("sub-blur", "0")
                    }
                    SubtitleOutlineEffect.SOFT_GLOW -> {
                        setProp("sub-border-style", "outline-and-shadow")
                        setProp("sub-outline-size", (width * 1.5f).toString())
                        setProp("sub-shadow-offset", "0")
                        val blurVal = String.format(Locale.US, "%.1f", (width * 0.8f + 1.2f).coerceIn(1.5f, 7.0f))
                        setProp("sub-blur", blurVal)
                    }
                    SubtitleOutlineEffect.OUTLINE_AND_SHADOW -> {
                        val size = if (style.outlineEnabled) width.toString() else "0"
                        setProp("sub-border-style", "outline-and-shadow")
                        setProp("sub-outline-size", size)
                        val offset = (width + 1).coerceIn(2, 6).toString()
                        setProp("sub-shadow-offset", offset)
                        setProp("sub-blur", "0")
                    }
                    SubtitleOutlineEffect.BACKGROUND_BOX -> {
                        setProp("sub-border-style", "opaque-box")
                        setProp("sub-outline-size", "0")
                        setProp("sub-shadow-offset", "0")
                        setProp("sub-blur", "0")
                    }
                }
            }
        }
    }

    private fun Color.toMpvHexColor(): String {
        val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
        val r = (red * 255f).roundToInt().coerceIn(0, 255)
        val g = (green * 255f).roundToInt().coerceIn(0, 255)
        val b = (blue * 255f).roundToInt().coerceIn(0, 255)
        return String.format(Locale.US, "#%02X%02X%02X%02X", a, r, g, b)
    }
}
