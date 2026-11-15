package com.nuvio.app.features.player.desktop

import co.touchlab.kermit.Logger
import com.nuvio.app.features.streams.StreamSubtitle
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream

/**
 * FFmpeg's HLS demuxer opens every variant, audio rendition and subtitle playlist of a master
 * playlist, and probes the first segment of each, one request at a time, before playback starts.
 * mpv then plays only the highest variant. Handing mpv a master trimmed to that variant, its own
 * audio group and no subtitle renditions skips most of those requests; the subtitle renditions are
 * offered as stream subtitles instead and fetched only when picked.
 */
internal data class HlsTrimmedMaster(val master: String, val subtitles: List<StreamSubtitle>)

internal object HlsMasterTrimmer {
    private val attributePattern = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")
    private val uriAttributePattern = Regex("""URI="([^"]*)"""")

    private class Variant(val infoLine: String, val uri: String, val attributes: Map<String, String>) {
        val height: Int = attributes["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull() ?: 0
        val bandwidth: Long = attributes["BANDWIDTH"]?.toLongOrNull() ?: 0L
    }

    /** Returns null when [master] is not a master playlist or trimming would not skip anything. */
    fun trim(master: String, base: URI): HlsTrimmedMaster? {
        val lines = master.removePrefix("﻿").lines().map { it.trimEnd('\r') }
        if (lines.firstOrNull()?.trim() != "#EXTM3U") return null
        // Variable substitution would have to be resolved before URIs can be rewritten.
        if (lines.any { it.startsWith("#EXT-X-DEFINE") }) return null

        val variants = mutableListOf<Variant>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                var next = index + 1
                while (next < lines.size && (lines[next].isBlank() || lines[next].startsWith("#"))) next++
                if (next >= lines.size) return null
                variants += Variant(line, lines[next].trim(), parseAttributes(line.substringAfter(':')))
                index = next
            }
            index++
        }
        if (variants.isEmpty()) return null
        val best = variants.maxWith(compareBy<Variant> { it.height }.thenBy { it.bandwidth })

        val subtitleGroup = best.attributes["SUBTITLES"]
        val subtitles = mutableListOf<StreamSubtitle>()
        var droppedRenditions = 0
        val output = StringBuilder()
        for (line in lines) {
            when {
                line.isBlank() -> continue
                line.startsWith("#EXT-X-STREAM-INF:") || line.startsWith("#EXT-X-I-FRAME-STREAM-INF:") -> continue
                line.startsWith("#EXT-X-MEDIA:") -> {
                    val attributes = parseAttributes(line.substringAfter(':'))
                    val type = attributes["TYPE"]
                    val group = attributes["GROUP-ID"]
                    val keep = when (type) {
                        "AUDIO" -> group == best.attributes["AUDIO"]
                        "VIDEO" -> group == best.attributes["VIDEO"]
                        "SUBTITLES" -> {
                            val uri = attributes["URI"]
                            if (group == subtitleGroup && uri != null) {
                                val absolute = resolve(base, uri) ?: return null
                                val name = attributes["NAME"]?.takeIf { it.isNotBlank() }
                                subtitles += StreamSubtitle(
                                    url = absolute,
                                    language = attributes["LANGUAGE"]?.takeIf { it.isNotBlank() } ?: name ?: "und",
                                    name = name,
                                )
                            }
                            false
                        }
                        else -> true
                    }
                    if (keep) output.append(absolutizeUriAttribute(line, base) ?: return null).append('\n')
                    else droppedRenditions++
                }
                line.startsWith("#") -> output.append(absolutizeUriAttribute(line, base) ?: return null).append('\n')
                else -> Unit
            }
        }
        if (variants.size == 1 && droppedRenditions == 0) return null

        output.append(best.infoLine.withoutAttribute("SUBTITLES")).append('\n')
        output.append(resolve(base, best.uri) ?: return null).append('\n')
        return HlsTrimmedMaster(output.toString(), subtitles)
    }

    private fun parseAttributes(text: String): Map<String, String> =
        attributePattern.findAll(text).associate { match ->
            match.groupValues[1] to match.groupValues[2].removeSurrounding("\"")
        }

    private fun String.withoutAttribute(name: String): String {
        val prefix = substringBefore(':') + ":"
        val kept = attributePattern.findAll(substringAfter(':'))
            .filter { it.groupValues[1] != name }
            .joinToString(",") { it.value }
        return prefix + kept
    }

    private fun absolutizeUriAttribute(line: String, base: URI): String? {
        val match = uriAttributePattern.find(line) ?: return line
        val absolute = resolve(base, match.groupValues[1]) ?: return null
        return line.replaceRange(match.range, "URI=\"$absolute\"")
    }

    private fun resolve(base: URI, reference: String): String? {
        val trimmed = reference.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        return runCatching { base.resolve(trimmed).toString() }.getOrNull()
    }
}

private const val DefaultSourceUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private val restrictedSourceHeaders = setOf("connection", "content-length", "expect", "host", "upgrade", "range")

/** Sends the stream's own headers (and mpv's default User-Agent when it has none). */
internal fun HttpRequest.Builder.withSourceHeaders(headerLines: List<String>): HttpRequest.Builder {
    var hasUserAgent = false
    for (line in headerLines) {
        val name = line.substringBefore(':').trim()
        val value = line.substringAfter(':', "").trim()
        if (name.isEmpty() || name.lowercase(Locale.ROOT) in restrictedSourceHeaders) continue
        if (name.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
        runCatching { header(name, value) }
    }
    if (!hasUserAgent) header("User-Agent", DefaultSourceUserAgent)
    return this
}

internal object HlsStartupTrim {
    private val log = Logger.withTag("HlsStartupTrim")
    private const val MaxPlaylistBytes = 1 shl 20

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(4))
            .build()
    }

    data class Prepared(val localUrl: String, val subtitles: List<StreamSubtitle>)

    /** Same test as the native bridge's isHlsSource. */
    fun looksLikeHls(url: String): Boolean {
        val lower = url.substringBefore('#').lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        return "m3u8" in lower || lower.substringBefore('?').endsWith(".m3u")
    }

    /**
     * Fetches [sourceUrl]; returns a local trimmed master, or null to play [sourceUrl] as is.
     * [knownHls] skips the URL test for a source the player already identified as HLS by its
     * response content type.
     */
    fun prepare(sourceUrl: String, headerLines: List<String>, knownHls: Boolean = false): Prepared? {
        val lower = sourceUrl.lowercase(Locale.ROOT)
        val isHttp = lower.startsWith("http://") || lower.startsWith("https://")
        if (!(if (knownHls) isHttp else looksLikeHls(sourceUrl))) return null
        return runCatching {
            val (body, base) = fetch(sourceUrl, headerLines) ?: return null
            val trimmed = HlsMasterTrimmer.trim(body, base) ?: return null
            val localUrl = LocalPlaylistServer.publish(trimmed.master) ?: return null
            log.d { "trimmed HLS master: ${trimmed.subtitles.size} subtitle renditions deferred" }
            Prepared(localUrl, trimmed.subtitles)
        }.onFailure { error -> log.w(error) { "HLS master trim skipped" } }.getOrNull()
    }

    private fun fetch(url: String, headerLines: List<String>): Pair<String, URI>? {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(6))
            .GET()
            .withSourceHeaders(headerLines)
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) return null
        var bytes = response.body()
        if (response.headers().firstValue("Content-Encoding").orElse("").equals("gzip", ignoreCase = true)) {
            bytes = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readNBytes(MaxPlaylistBytes + 1) }
        }
        if (bytes.size > MaxPlaylistBytes) return null
        return String(bytes, Charsets.UTF_8) to response.uri()
    }

    private object LocalPlaylistServer {
        private const val MaxEntries = 16
        private val playlists = object : LinkedHashMap<String, ByteArray>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
                size > MaxEntries
        }

        private val server: HttpServer? by lazy {
            var created: HttpServer? = null
            // HttpServer's dispatcher thread inherits daemon status from the thread that starts it;
            // start it from a daemon thread so it never keeps the app alive on exit.
            val starter = Thread({
                created = runCatching {
                    HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
                        createContext("/hls/") { exchange ->
                            try {
                                val token = exchange.requestURI.path.removePrefix("/hls/").substringBefore('/')
                                val body = synchronized(playlists) { playlists[token] }
                                when {
                                    body == null -> exchange.sendResponseHeaders(404, -1)
                                    exchange.requestMethod.equals("HEAD", ignoreCase = true) -> {
                                        exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
                                        exchange.sendResponseHeaders(200, -1)
                                    }
                                    else -> {
                                        exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
                                        exchange.sendResponseHeaders(200, body.size.toLong())
                                        exchange.responseBody.write(body)
                                    }
                                }
                            } finally {
                                exchange.close()
                            }
                        }
                        start()
                    }
                }.onFailure { error -> log.w(error) { "local HLS playlist server failed to start" } }.getOrNull()
            }, "nuvio-hls-playlist-server-start").apply { isDaemon = true }
            starter.start()
            starter.join()
            created
        }

        fun publish(master: String): String? {
            val active = server ?: return null
            val token = UUID.randomUUID().toString()
            synchronized(playlists) { playlists[token] = master.toByteArray(Charsets.UTF_8) }
            return "http://127.0.0.1:${active.address.port}/hls/$token/master.m3u8"
        }
    }
}
