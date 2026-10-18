package com.nuvio.app.features.player.desktop

import androidx.compose.ui.graphics.Color
import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.player.PlayerPlaybackStats
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
    private var getPropertyHandle: MethodHandle? = null
    private var getPropertyStringHandle: MethodHandle? = null
    private var freeHandle: MethodHandle? = null
    private var clientNameHandle: MethodHandle? = null
    private var lastSpeed = 0L
    private var lastSpeedTimestamp = 0L

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
            val getPropSym = lookup.find("mpv_get_property").orElse(null)

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

            if (getPropSym != null) {
                getPropertyHandle = linker.downcallHandle(
                    getPropSym,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                    ),
                )
            }

            val getPropStrSym = lookup.find("mpv_get_property_string").orElse(null)
            val freeSym = lookup.find("mpv_free").orElse(null)
            if (getPropStrSym != null) {
                getPropertyStringHandle = linker.downcallHandle(
                    getPropStrSym,
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                )
            }
            if (freeSym != null) {
                freeHandle = linker.downcallHandle(
                    freeSym,
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
                )
            }
            true
        }.getOrDefault(false)
    }

    private fun getStringProperty(mpvCtx: MemorySegment, name: String): String? {
        val getPropStr = getPropertyStringHandle ?: return null
        val free = freeHandle
        return runCatching {
            Arena.ofConfined().use { arena ->
                val pName = arena.allocateUtf8String(name)
                val res = getPropStr.invoke(mpvCtx, pName) as? MemorySegment
                if (res != null && res.address() != 0L) {
                    val str = res.reinterpret(1024).getUtf8String(0)
                    free?.invoke(res)
                    str
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun getLongProperty(mpvCtx: MemorySegment, name: String): Long {
        val getProp = getPropertyHandle ?: return 0L
        return runCatching {
            Arena.ofConfined().use { arena ->
                val pName = arena.allocateUtf8String(name)
                val pOut = arena.allocate(ValueLayout.JAVA_LONG)
                val res = getProp.invoke(mpvCtx, pName, 4 /* MPV_FORMAT_INT64 */, pOut) as Int
                if (res >= 0) pOut.get(ValueLayout.JAVA_LONG, 0L) else 0L
            }
        }.getOrDefault(0L)
    }

    private fun getDoubleProperty(mpvCtx: MemorySegment, name: String): Double {
        val getProp = getPropertyHandle ?: return 0.0
        return runCatching {
            Arena.ofConfined().use { arena ->
                val pName = arena.allocateUtf8String(name)
                val pOut = arena.allocate(ValueLayout.JAVA_DOUBLE)
                val res = getProp.invoke(mpvCtx, pName, 5 /* MPV_FORMAT_DOUBLE */, pOut) as Int
                if (res >= 0) pOut.get(ValueLayout.JAVA_DOUBLE, 0L) else 0.0
            }
        }.getOrDefault(0.0)
    }

    fun supportsCacheSpeed(): Boolean = DesktopHostOs.current == DesktopHostOs.WINDOWS

    fun getCacheSpeed(bridgeHandle: Long): Long {
        if (!supportsCacheSpeed()) return 0L
        if (!ensureInitialized()) return 0L
        val getProp = getPropertyHandle ?: return 0L
        val mpvCtx = resolveMpvHandle(bridgeHandle) ?: return 0L

        val speed = runCatching {
            Arena.ofConfined().use { arena ->
                val pName = arena.allocateUtf8String("cache-speed")
                val pOut = arena.allocate(ValueLayout.JAVA_LONG)
                val res = getProp.invoke(mpvCtx, pName, 4 /* MPV_FORMAT_INT64 */, pOut) as Int
                if (res >= 0) {
                    pOut.get(ValueLayout.JAVA_LONG, 0L).coerceAtLeast(0L)
                } else {
                    0L
                }
            }
        }.getOrDefault(0L)

        val now = System.currentTimeMillis()
        if (speed > 0L) {
            lastSpeed = speed
            lastSpeedTimestamp = now
            return speed
        }
        if (now - lastSpeedTimestamp < 2000L && lastSpeed > 0L) {
            return lastSpeed
        }
        return 0L
    }

    fun getPlaybackStats(bridgeHandle: Long): PlayerPlaybackStats {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return PlayerPlaybackStats()
        if (!ensureInitialized()) return PlayerPlaybackStats()
        val mpvCtx = resolveMpvHandle(bridgeHandle) ?: return PlayerPlaybackStats()

        val videoCodec = (getStringProperty(mpvCtx, "video-codec") ?: getStringProperty(mpvCtx, "video-format")).orEmpty()
        val hwdec = getStringProperty(mpvCtx, "hwdec-current").orEmpty()
        val width = getLongProperty(mpvCtx, "video-params/w").takeIf { it > 0 } ?: getLongProperty(mpvCtx, "width")
        val height = getLongProperty(mpvCtx, "video-params/h").takeIf { it > 0 } ?: getLongProperty(mpvCtx, "height")
        val resolution = if (width > 0 && height > 0) "${width}x${height}" else ""
        val pixelFormat = getStringProperty(mpvCtx, "video-params/pixelformat").orEmpty()
        val primaries = getStringProperty(mpvCtx, "video-params/primaries").orEmpty()
        val gamma = getStringProperty(mpvCtx, "video-params/gamma").orEmpty()
        val colorInfo = listOf(pixelFormat, primaries, gamma).filter { it.isNotBlank() }.joinToString(" · ")
        val containerFps = getDoubleProperty(mpvCtx, "video-fps").takeIf { it > 0.0 }
            ?: getDoubleProperty(mpvCtx, "container-fps")
        val outputFps = getDoubleProperty(mpvCtx, "estimated-vf-fps")
        val displayFps = getDoubleProperty(mpvCtx, "estimated-display-fps").takeIf { it > 0.0 }
            ?: getDoubleProperty(mpvCtx, "display-fps")
        val videoBitrate = getLongProperty(mpvCtx, "video-bitrate") / 1000L
        val droppedFrames = getLongProperty(mpvCtx, "decoder-frame-drop-count").takeIf { it > 0 }
            ?: getLongProperty(mpvCtx, "drop-frame-count")
        val voDroppedFrames = getLongProperty(mpvCtx, "vo-drop-frame-count").takeIf { it > 0 }
            ?: getLongProperty(mpvCtx, "frame-drop-count")
        val audioCodec = (getStringProperty(mpvCtx, "audio-codec-name") ?: getStringProperty(mpvCtx, "audio-codec") ?: getStringProperty(mpvCtx, "audio-format")).orEmpty()
        val audioChannels = getStringProperty(mpvCtx, "audio-params/channels").orEmpty()
        val audioSampleRate = getLongProperty(mpvCtx, "audio-params/samplerate")
        val audioBitrate = getLongProperty(mpvCtx, "audio-bitrate") / 1000L
        val cacheDuration = getDoubleProperty(mpvCtx, "demuxer-cache-duration")
        val cacheUsed = getLongProperty(mpvCtx, "demuxer-cache-state/cache-used").takeIf { it > 0 }
            ?: getLongProperty(mpvCtx, "cache-used")
        val cacheSpeed = getCacheSpeed(bridgeHandle)

        return PlayerPlaybackStats(
            videoCodec = videoCodec,
            videoDecoder = hwdec,
            videoResolution = resolution,
            videoColor = colorInfo,
            containerFps = containerFps,
            outputFps = outputFps,
            displayFps = displayFps,
            videoBitrateKbps = videoBitrate,
            droppedFrames = droppedFrames,
            voDroppedFrames = voDroppedFrames,
            audioCodec = audioCodec,
            audioChannels = audioChannels,
            audioSampleRateHz = audioSampleRate,
            audioBitrateKbps = audioBitrate,
            cacheDurationSec = cacheDuration,
            cacheUsedBytes = cacheUsed,
            cacheSpeedBytes = cacheSpeed,
        )
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
                        val size = if (style.outlineEffect.needsOutline) width.toString() else "0"
                        setProp("sub-border-style", "outline-and-shadow")
                        setProp("sub-outline-size", size)
                        setProp("sub-shadow-offset", "0")
                        // No blur: mpv applies sub-blur to the whole composited
                        // subtitle image, glyphs included, so any value above 0
                        // softens the text itself and not just the outline.
                        setProp("sub-blur", "0")
                    }
                    SubtitleOutlineEffect.SOFT_GLOW -> {
                        setProp("sub-border-style", "outline-and-shadow")
                        // Wide spread outline acts as the glow halo
                        val glowSize = String.format(Locale.US, "%.1f", (width * 2.0f).coerceIn(2f, 12f))
                        setProp("sub-outline-size", glowSize)
                        setProp("sub-shadow-offset", "0")
                        // Heavy blur makes it bloom softly like a backlit glow
                        val blurVal = String.format(Locale.US, "%.1f", (width * 1.2f + 2.0f).coerceIn(2.5f, 9.0f))
                        setProp("sub-blur", blurVal)
                    }
                    SubtitleOutlineEffect.OUTLINE_AND_SHADOW -> {
                        val size = if (style.outlineEffect.needsOutline) width.toString() else "0"
                        setProp("sub-border-style", "outline-and-shadow")
                        setProp("sub-outline-size", size)
                        val offset = (width + 1).coerceIn(2, 5).toString()
                        setProp("sub-shadow-offset", offset)
                        // The halo look depends on this blur. Note that mpv applies
                        // sub-blur as a gaussian pass over the whole composited
                        // subtitle image, glyphs included, so the text softens
                        // along with the border. That trade is intentional here and
                        // is why OUTLINE above stays at 0 for crisp text.
                        val blurVal = String.format(Locale.US, "%.1f", (width * 0.5f + 0.8f).coerceIn(1.0f, 4.0f))
                        setProp("sub-blur", blurVal)
                    }
                    SubtitleOutlineEffect.BACKGROUND_BOX -> {
                        // Use one padded panel for the whole caption block instead of
                        // drawing separate boxes tightly around each line.
                        val boxColor = if (style.backgroundColor.alpha == 0f) {
                            Color.Black.copy(alpha = 0.65f)
                        } else {
                            style.backgroundColor
                        }
                        setProp("sub-border-style", "background-box")
                        setProp("sub-outline-color", boxColor.toMpvHexColor())
                        setProp("sub-back-color", boxColor.toMpvHexColor())
                        setProp("sub-outline-size", "0")
                        setProp("sub-shadow-offset", "4")
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
