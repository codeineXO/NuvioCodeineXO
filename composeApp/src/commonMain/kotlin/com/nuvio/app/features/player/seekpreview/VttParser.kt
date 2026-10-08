package com.nuvio.app.features.player.seekpreview

internal object VttParser {

    fun parse(vttText: String, baseUrl: String? = null): List<SeekrCue> {
        val cues = mutableListOf<SeekrCue>()
        val lines = vttText.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.contains("-->")) {
                val parts = line.split("-->")
                if (parts.size == 2) {
                    val startMs = parseTimestamp(parts[0].trim())
                    val endMs = parseTimestamp(parts[1].trim())
                    val payload = lines.drop(i + 1).firstOrNull { it.isNotBlank() }?.trim()
                    val tile = payload?.let { parseTile(it, baseUrl) }
                    if (tile != null) {
                        cues += SeekrCue(
                            startMs = startMs,
                            endMs = endMs,
                            tile = tile,
                        )
                    }
                }
            }
            i++
        }

        cues.sortBy { it.startMs }
        return cues
    }

    private fun parseTile(payload: String, baseUrl: String?): SeekrTile? {
        val hashIdx = payload.lastIndexOf('#')
        if (hashIdx < 0) return null
        var sheetUrl = payload.substring(0, hashIdx)
        if (baseUrl != null && !sheetUrl.startsWith("http://") && !sheetUrl.startsWith("https://")) {
            val baseWithoutQuery = baseUrl.substringBefore('?')
            val baseDir = baseWithoutQuery.substringBeforeLast('/')
            sheetUrl = "$baseDir/${sheetUrl.removePrefix("/")}"
        }
        val fragment = payload.substring(hashIdx + 1)
        if (!fragment.startsWith("xywh=")) return null
        val coords = fragment.removePrefix("xywh=").split(",")
        if (coords.size < 4) return null
        val x = coords[0].trim().toIntOrNull() ?: return null
        val y = coords[1].trim().toIntOrNull() ?: return null
        val w = coords[2].trim().toIntOrNull() ?: return null
        val h = coords[3].trim().toIntOrNull() ?: return null
        return SeekrTile(sheetUrl = sheetUrl, x = x, y = y, w = w, h = h)
    }

    private fun parseTimestamp(text: String): Long {
        val clean = text.substringBefore(' ').trim()
        val parts = clean.split(':')
        return when (parts.size) {
            3 -> {
                val h = parts[0].toLongOrNull() ?: 0L
                val m = parts[1].toLongOrNull() ?: 0L
                val s = parts[2].toDoubleOrNull() ?: 0.0
                h * 3_600_000L + m * 60_000L + (s * 1000.0).toLong()
            }
            2 -> {
                val m = parts[0].toLongOrNull() ?: 0L
                val s = parts[1].toDoubleOrNull() ?: 0.0
                m * 60_000L + (s * 1000.0).toLong()
            }
            else -> 0L
        }
    }
}
