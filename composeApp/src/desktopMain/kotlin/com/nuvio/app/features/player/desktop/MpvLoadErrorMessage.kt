package com.nuvio.app.features.player.desktop

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_error_source_invalid_content
import nuvio.composeapp.generated.resources.player_error_stream_blocked
import nuvio.composeapp.generated.resources.player_error_stream_expired
import nuvio.composeapp.generated.resources.player_error_stream_rate_limited
import nuvio.composeapp.generated.resources.player_error_stream_removed
import nuvio.composeapp.generated.resources.player_error_stream_unavailable
import nuvio.composeapp.generated.resources.player_error_unsupported_format
import nuvio.composeapp.generated.resources.player_unable_to_play_stream
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

// Ported from upstream NuvioMobile (androidMain MpvErrorLog.kt and PlaybackErrorMessages.kt),
// which in turn follows NuvioTV, so a failed libmpv load is worded the same way on every app.

private const val MPV_ERROR_LOG_LINES = 6
private const val MPV_LOG_LEVEL_ERROR = 20
private const val MPV_LOG_LEVEL_WARN = 30
private const val MPV_LOG_LEVEL_INFO = 40

private val MPV_HTTP_ERROR_REGEX = Regex("HTTP error (\\d{3})(?:\\s+(.+))?", RegexOption.IGNORE_CASE)
private val MPV_URL_REGEX = Regex("https?://\\S+")
private val MPV_FFMPEG_ADDR_REGEX = Regex("\\s*@\\s*0x[0-9a-fA-F]+")
private val MPV_DIAGNOSTIC_LOG_REGEX = Regex(
    "codec|decoder|hwdec|mediacodec|hwaccel|profile|pix_fmt|pixel format|unsupported|not supported",
    RegexOption.IGNORE_CASE,
)
private val MPV_INFO_CAUSE_REGEX = Regex(
    "^VO:|^AO:|selected video codec|selected audio codec|using hardware decoding|falling back to software",
    RegexOption.IGNORE_CASE,
)
private val MPV_OUTPUT_CAUSE_REGEX = Regex(
    "video chain|audio chain|swapchain|cannot convert|could not initialize|failed to initialize|failed \\(re\\)creating|hw format|no video|no sound|no audio|" +
        "connection refused|timed out|timeout|handshake|invalid data|unknown format|recognize file format|protocol not found|i/o error|input/output error|" +
        "decoding frame|certificate|moov atom|ebml|avformat_open_input|initcheck|name resolution|unreachable|permission denied|video conversion",
    RegexOption.IGNORE_CASE,
)

/** A plain-language reason for an HTTP status, shown with the technical error. */
internal fun httpStatusExplanation(code: Int): StringResource? = when (code) {
    401, 410 -> Res.string.player_error_stream_expired
    404 -> Res.string.player_error_stream_removed
    429 -> Res.string.player_error_stream_rate_limited
    in 500..599 -> Res.string.player_error_stream_unavailable
    in 400..499 -> Res.string.player_error_stream_blocked
    else -> null
}

/**
 * The error screen text for a failed load, from the bridge's [details]: the libmpv error the file
 * ended with, then its recent "level\tprefix\ttext" messages. [property] reads an mpv property,
 * for the format mpv had picked when the failure has no HTTP status.
 */
internal suspend fun mpvLoadErrorMessage(details: String?, property: (String) -> String?): String {
    val lines = details.orEmpty().lines()
    val fileError = lines.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    var logLine: String? = null
    lines.drop(1).forEach { entry ->
        val parts = entry.split('\t', limit = 3)
        if (parts.size != 3) return@forEach
        val level = parts[0].toIntOrNull() ?: return@forEach
        mpvUsefulLogLine(level, parts[1], parts[2])?.let { logLine = rememberMpvErrorLog(logLine, it) }
    }
    val httpCode = mpvHttpStatusCode(logLine)
    val codecContext = if (httpCode == null) mpvCodecContextLine(property, logLine.orEmpty()) else null
    val fallback = getString(Res.string.player_unable_to_play_stream)
    return mpvErrorMessage(
        fileError = fileError,
        logLine = logLine,
        codecContext = codecContext,
        fallback = fallback,
        httpExplanation = httpCode?.let { code -> httpStatusExplanation(code)?.let { getString(it) } }.orEmpty(),
        statusExplanation = if (httpCode == null) mpvNonHttpExplanationText(fileError, logLine) else "",
    )
}

/** The line kept from one mpv message, or null when it says nothing about the failure. */
internal fun mpvUsefulLogLine(level: Int, prefix: String, text: String): String? {
    val isHttp = MPV_HTTP_ERROR_REGEX.containsMatchIn(text)
    if (level > MPV_LOG_LEVEL_INFO) return null
    val isInfo = level > MPV_LOG_LEVEL_WARN
    if (isInfo && !mpvInfoLineIsUseful(text)) return null
    if (!isInfo && level > MPV_LOG_LEVEL_ERROR && !isHttp && !mpvWarnLineIsUseful(prefix, text)) return null
    val body = text.replace(MPV_URL_REGEX, "").trim().take(300)
    if (body.isEmpty()) return null
    return if (prefix.isBlank()) body else "[${prefix.trim()}] $body"
}

internal fun mpvHttpStatusCode(logLine: String?): Int? {
    return logLine
        ?.let { MPV_HTTP_ERROR_REGEX.find(it) }
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it in 100..599 }
}

private suspend fun mpvNonHttpExplanationText(fileError: String?, logLine: String?): String {
    val raw = when (mpvNonHttpExplanationKind(fileError, logLine)) {
        MpvNonHttpExplanationKind.InvalidContent ->
            getString(Res.string.player_error_source_invalid_content, "")
        MpvNonHttpExplanationKind.UnsupportedFormat ->
            getString(Res.string.player_error_unsupported_format, "")
        MpvNonHttpExplanationKind.OpenFailed ->
            getString(Res.string.player_error_stream_unavailable)
    }
    return raw.trim().removeSuffix("[]").trim()
}

internal fun mpvErrorMessage(
    fileError: String?,
    logLine: String?,
    codecContext: String?,
    fallback: String,
    httpExplanation: String,
    statusExplanation: String,
): String {
    val httpCode = mpvHttpStatusCode(logLine)
    val detail = if (httpCode == null) {
        listOfNotNull(logLine?.takeIf { it.isNotBlank() }, codecContext).joinToString("\n").ifBlank { null }
    } else {
        logLine
    }
    return mpvDirectErrorMessage(
        fileError = fileError,
        logLine = detail,
        fallback = fallback,
        httpExplanation = if (httpCode != null) httpExplanation else "",
        statusExplanation = if (httpCode == null) statusExplanation else "",
    )
}

/** The file, video and audio format mpv had picked, for errors without an HTTP status. */
private fun mpvCodecContextLine(property: (String) -> String?, existing: String): String? {
    fun prop(name: String): String? = runCatching {
        property(name)
            ?.trim()
            ?.substringBefore(" (")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) && !it.equals("no", ignoreCase = true) }
    }.getOrNull()
    fun intProp(name: String): Int? = prop(name)?.toIntOrNull()?.takeIf { it > 0 }
    val width = intProp("width")
    val height = intProp("height")
    val resolution = if (width != null && height != null) "${width}x$height" else null
    val rate = intProp("audio-params/samplerate")
    val channels = intProp("audio-params/channel-count")
    val parts = listOfNotNull(
        fresh(existing, prop("file-format")),
        fresh(existing, prop("current-vo") ?: prop("vo"), label = "vo"),
        fresh(existing, prop("video-codec"), label = "video"),
        fresh(existing, resolution),
        fresh(existing, mpvFpsLabel(prop("container-fps"))),
        fresh(existing, prop("video-format") ?: prop("video-params/pixelformat")),
        fresh(existing, prop("current-tracks/video/decoder-desc"), label = "decoder"),
        fresh(existing, prop("audio-codec"), label = "audio"),
        fresh(existing, rate?.let { "${it}Hz" }),
        fresh(existing, channels?.let { "${it}ch" }),
        fresh(existing, prop("hwdec-current") ?: prop("hwdec"), label = "hwdec"),
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

private fun fresh(existing: String, token: String?, label: String = ""): String? {
    val value = token?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (existing.contains(value, ignoreCase = true)) return null
    return if (label.isEmpty()) value else "$label $value"
}

internal fun rememberMpvErrorLog(current: String?, line: String): String {
    val lines = current
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toMutableList()
        ?: mutableListOf()
    if (MPV_HTTP_ERROR_REGEX.containsMatchIn(line)) {
        lines.removeAll { MPV_HTTP_ERROR_REGEX.containsMatchIn(it) }
        lines.add(0, line)
    } else if (lines.none { it.equals(line, ignoreCase = true) }) {
        lines.add(line)
    }
    val http = lines.filter { MPV_HTTP_ERROR_REGEX.containsMatchIn(it) }
    val pinned = lines.filter { mpvLogTextIsCause(it) && !MPV_HTTP_ERROR_REGEX.containsMatchIn(it) }
        .take(MPV_ERROR_LOG_LINES)
    val other = lines.filter {
        !MPV_HTTP_ERROR_REGEX.containsMatchIn(it) &&
            !mpvLogTextIsCause(it) &&
            !it.contains("ytdl_hook", ignoreCase = true)
    }
    val hook = lines.filter { it.contains("ytdl_hook", ignoreCase = true) }
    val room = (MPV_ERROR_LOG_LINES - pinned.size).coerceAtLeast(0)
    val detail = if (http.isNotEmpty() || pinned.isNotEmpty() || other.isNotEmpty()) {
        pinned + other.takeLast(room)
    } else {
        hook.takeLast(MPV_ERROR_LOG_LINES)
    }
    return (http + detail).joinToString("\n")
}

internal fun mpvDirectErrorMessage(
    fileError: String?,
    logLine: String?,
    fallback: String,
    httpExplanation: String = "",
    statusExplanation: String = "",
): String {
    val lines = logLine
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toList()
        .orEmpty()
    val http = lines.firstNotNullOfOrNull { MPV_HTTP_ERROR_REGEX.find(it) }
    val details = lines.mapNotNull { mpvDisplayDetail(it) }
    val file = fileError?.trim()?.takeIf { it.isNotEmpty() }
    if (http != null) {
        val code = http.groupValues[1]
        val reason = http.groupValues.getOrNull(2)
            ?.replace(MPV_URL_REGEX, "")
            ?.trim()
            ?.trimEnd('?', ':', '-')
            ?.takeIf { it.isNotEmpty() && !it.contains("://") }
        val specific = details.filter { !mpvDetailIsGeneric(it) }.distinctBy { it.lowercase() }
        val explanation = httpExplanation.trim()
        return buildString {
            if (explanation.isNotEmpty()) {
                append(explanation)
                append("\n\n")
            }
            append("HTTP $code")
            if (reason != null) append(" $reason")
            if (!file.isNullOrBlank()) append(" [$file]")
            specific.take(MPV_ERROR_LOG_LINES).forEach { line ->
                if (!file.isNullOrBlank() &&
                    (line.contains(file, ignoreCase = true) || file.contains(line, ignoreCase = true))
                ) {
                    return@forEach
                }
                append('\n')
                append(line)
            }
        }
    }
    val unique = details.distinctBy { it.lowercase() }
    val specific = unique.filter { !mpvDetailIsWrapper(it) }
    val chosen = (if (specific.isNotEmpty()) specific else unique.filter { !mpvDetailIsGeneric(it) }.ifEmpty { unique })
        .take(MPV_ERROR_LOG_LINES)
        .toMutableList()
    if (chosen.isEmpty()) return prependMpvStatusExplanation(statusExplanation, file ?: fallback, fallback)
    if (file != null && chosen.none { it.equals(file, ignoreCase = true) || it.contains(file, ignoreCase = true) }) {
        if (mpvFileErrorHeadsCause(file)) {
            chosen.add(0, file)
        } else {
            chosen[0] = "${chosen[0]} [$file]"
        }
    }
    return prependMpvStatusExplanation(statusExplanation, chosen.joinToString("\n"), fallback)
}

private fun mpvDisplayDetail(line: String): String? {
    if (MPV_HTTP_ERROR_REGEX.containsMatchIn(line)) return null
    if (line.contains("ytdl_hook", ignoreCase = true)) return null
    val stripped = line
        .replace(MPV_URL_REGEX, "")
        .replace(Regex("^\\[[^\\]]+]\\s*"), "")
        .replace(MPV_FFMPEG_ADDR_REGEX, "")
        .trim()
        .trimEnd('?', ':', '-')
        .trim()
    if (stripped.isEmpty() || stripped.contains("://")) return null
    return stripped
}

private fun mpvWarnLineIsUseful(prefix: String, text: String): Boolean {
    if (prefix.contains("ytdl_hook", ignoreCase = true)) return false
    return mpvLogTextIsCause(text)
}

private fun mpvLogTextIsCause(text: String): Boolean {
    return MPV_DIAGNOSTIC_LOG_REGEX.containsMatchIn(text) ||
        MPV_OUTPUT_CAUSE_REGEX.containsMatchIn(text) ||
        mpvInfoLineIsUseful(text)
}

private fun mpvInfoLineIsUseful(text: String): Boolean {
    return MPV_INFO_CAUSE_REGEX.containsMatchIn(text.trim())
}

private fun mpvFpsLabel(raw: String?): String? {
    val value = raw?.toDoubleOrNull() ?: return null
    if (value < 1.0) return null
    val rounded = (value * 1000.0).toInt() / 1000.0
    val text = rounded.toString().trimEnd('0').trimEnd('.')
    return "${text}fps"
}

private fun mpvDetailIsGeneric(detail: String): Boolean {
    return detail.equals("Failed to open", ignoreCase = true) ||
        detail.equals("loading failed", ignoreCase = true)
}

private fun mpvDetailIsWrapper(detail: String): Boolean {
    if (mpvDetailIsGeneric(detail)) return true
    return detail.equals("Could not initialize video chain.", ignoreCase = true) ||
        detail.equals("Could not initialize video chain", ignoreCase = true) ||
        detail.equals("Video: no video", ignoreCase = true) ||
        detail.equals("Could not open/initialize audio device -> no sound.", ignoreCase = true) ||
        detail.equals("Could not open/initialize audio device -> no sound", ignoreCase = true)
}

private fun mpvFileErrorHeadsCause(fileError: String): Boolean {
    return fileError.equals("loading failed", ignoreCase = true) ||
        fileError.equals("audio output initialization failed", ignoreCase = true) ||
        fileError.equals("video output initialization failed", ignoreCase = true) ||
        fileError.equals("unrecognized file format", ignoreCase = true) ||
        fileError.equals("no audio or video data played", ignoreCase = true) ||
        fileError.equals("not supported", ignoreCase = true) ||
        fileError.equals("something happened", ignoreCase = true)
}

internal enum class MpvNonHttpExplanationKind {
    InvalidContent,
    UnsupportedFormat,
    OpenFailed,
}

internal fun mpvNonHttpExplanationKind(fileError: String?, logLine: String?): MpvNonHttpExplanationKind {
    val file = fileError?.trim()?.lowercase().orEmpty()
    val log = logLine?.lowercase().orEmpty()
    if (file == "unrecognized file format" ||
        file == "no audio or video data played" ||
        log.contains("unrecognized file format")
    ) {
        return MpvNonHttpExplanationKind.InvalidContent
    }
    if (file == "audio output initialization failed" ||
        file == "video output initialization failed" ||
        file == "not supported" ||
        log.contains("codec") ||
        log.contains("decoder") ||
        log.contains("hwdec")
    ) {
        return MpvNonHttpExplanationKind.UnsupportedFormat
    }
    return MpvNonHttpExplanationKind.OpenFailed
}

private fun prependMpvStatusExplanation(statusExplanation: String, technical: String, fallback: String): String {
    val explanation = statusExplanation.trim()
    val detail = technical.trim()
    if (explanation.isEmpty()) return detail
    if (detail.isBlank() || detail.equals(fallback, ignoreCase = true)) return explanation
    return "$explanation\n\n$detail"
}
