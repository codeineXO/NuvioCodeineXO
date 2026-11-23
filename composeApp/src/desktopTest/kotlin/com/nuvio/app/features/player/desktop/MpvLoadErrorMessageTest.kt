package com.nuvio.app.features.player.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MpvLoadErrorMessageTest {
    private fun logOf(vararg entries: Triple<Int, String, String>): String? {
        var log: String? = null
        entries.forEach { (level, prefix, text) ->
            mpvUsefulLogLine(level, prefix, text)?.let { log = rememberMpvErrorLog(log, it) }
        }
        return log
    }

    @Test
    fun `http failure leads with the explanation, status and file error`() {
        val log = logOf(
            Triple(20, "ffmpeg", "https: HTTP error 403 Forbidden"),
            Triple(20, "stream", "Failed to open https://cdn.example/video.mkv."),
        )
        assertEquals(403, mpvHttpStatusCode(log))
        val message = mpvErrorMessage(
            fileError = "loading failed",
            logLine = log,
            codecContext = null,
            fallback = "Unable to play this stream.",
            httpExplanation = "The stream source is blocked or restricted. Try a different source.",
            statusExplanation = "",
        )
        assertEquals(
            "The stream source is blocked or restricted. Try a different source.\n\nHTTP 403 Forbidden [loading failed]",
            message,
        )
    }

    @Test
    fun `info lines that do not name a cause are dropped and urls are stripped`() {
        assertNull(mpvUsefulLogLine(40, "cplayer", "Playing: https://cdn.example/video.mkv"))
        assertNull(mpvUsefulLogLine(50, "ffmpeg", "https: HTTP error 404 Not Found"))
        assertEquals("[stream] Failed to open", mpvUsefulLogLine(20, "stream", "Failed to open https://cdn.example/a.mkv."))
    }

    @Test
    fun `non http failure is classified from the file error`() {
        assertEquals(MpvNonHttpExplanationKind.InvalidContent, mpvNonHttpExplanationKind("unrecognized file format", null))
        assertEquals(MpvNonHttpExplanationKind.UnsupportedFormat, mpvNonHttpExplanationKind("loading failed", "[vd] decoder init failed"))
        assertEquals(MpvNonHttpExplanationKind.OpenFailed, mpvNonHttpExplanationKind("loading failed", null))
    }
}
