package com.nuvio.app.features.player.desktop

import com.nuvio.app.core.diagnostics.DebugLogSettings
import com.nuvio.app.core.diagnostics.DesktopDebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives mpv log messages from the Windows bridge while debug logging is on. */
internal object NativePlayerLogSink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var nativeEnabled = false

    // Runs of the same message that differ only in numbers (e.g. "hevc: Skipping NAL unit 38",
    // thousands per stream) are written once, then summarised when a different message arrives.
    private val repeatLock = Any()
    private var runShape: String? = null
    private var runTag = ""
    private var runLevel = ""
    private var runLastText = ""
    private var runRepeats = 0

    @JvmStatic
    fun onMpvLog(level: String, prefix: String, text: String) {
        val tag = if (prefix.isBlank()) "mpv" else "mpv/$prefix"
        val letter = mpvLevelLetter(level)
        val message = text.trimEnd()
        val shape = "$letter|$tag|${message.withoutDigits()}"
        synchronized(repeatLock) {
            if (shape == runShape) {
                runRepeats++
                runLastText = message
                return
            }
            flushRepeatsLocked()
            runShape = shape
            runTag = tag
            runLevel = letter
            runLastText = message
        }
        DesktopDebugLog.write(tag = tag, level = letter, message = message)
    }

    private fun flushRepeatsLocked() {
        if (runRepeats > 0) {
            DesktopDebugLog.write(runTag, runLevel, "(repeated $runRepeats more times, last: $runLastText)")
        }
        runRepeats = 0
    }

    // Plain loop, not a regex: this runs on the mpv event thread for every message.
    private fun String.withoutDigits(): String {
        val out = StringBuilder(length)
        var inNumber = false
        for (ch in this) {
            if (ch.isDigit()) {
                if (!inNumber) out.append('#')
                inNumber = true
            } else {
                out.append(ch)
                inNumber = false
            }
        }
        return out.toString()
    }

    fun install() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        scope.launch {
            DebugLogSettings.enabled.collect { enabled ->
                // Don't load the native library at startup just to say "off".
                if (!enabled && !nativeEnabled) return@collect
                if (!enabled) synchronized(repeatLock) { flushRepeatsLocked(); runShape = null }
                runCatching {
                    NativePlayerBridge.setDebugLogging(enabled, NativePlayerLogSink::class.java)
                    nativeEnabled = enabled
                }.onFailure { error ->
                    DesktopDebugLog.write("mpv", "W", "mpv log forwarding unavailable: $error")
                }
            }
        }
    }

    private fun mpvLevelLetter(level: String): String = when (level) {
        "fatal" -> "A"
        "error" -> "E"
        "warn" -> "W"
        "info" -> "I"
        "v", "debug" -> "D"
        else -> "V"
    }
}
