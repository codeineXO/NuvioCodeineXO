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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * FFmpeg's HLS demuxer opens every variant, audio rendition and subtitle playlist of a master
 * playlist, and probes the first segment of each, one request at a time, before playback starts.
 * mpv then plays only the highest variant. Handing mpv a master trimmed to that variant, its own
 * audio group and no subtitle renditions skips most of those requests; the subtitle renditions are
 * offered as stream subtitles instead and fetched only when picked.
 */
/** One playable quality of a master playlist; [index] is its position among the master's variants. */
internal data class HlsVariant(val index: Int, val height: Int, val bandwidth: Long, val codecs: String)

/** One entry of the player's Quality picker; [variantIndex] identifies the HLS variant to play. */
internal data class PlayerControlQualityOption(val variantIndex: Int, val label: String)

internal data class HlsTrimmedMaster(
    val master: String,
    val subtitles: List<StreamSubtitle>,
    val variants: List<HlsVariant> = emptyList(),
    val selectedIndex: Int = -1,
)

internal object HlsMasterTrimmer {
    private val attributePattern = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")
    private val uriAttributePattern = Regex("""URI="([^"]*)"""")

    private class Variant(val infoLine: String, val uri: String, val attributes: Map<String, String>) {
        val height: Int = attributes["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull() ?: 0
        val bandwidth: Long = attributes["BANDWIDTH"]?.toLongOrNull() ?: 0L
        val codecs: String = attributes["CODECS"].orEmpty()
    }

    /**
     * Returns null when [master] is not a master playlist or trimming would not skip anything.
     * Keeps the variant at [variantIndex] when given, else the best one no taller than [maxHeight]
     * (0 = no cap).
     */
    fun trim(master: String, base: URI, maxHeight: Int = 0, variantIndex: Int? = null): HlsTrimmedMaster? {
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
        val best = variantIndex?.let(variants::getOrNull) ?: pickVariant(variants, maxHeight)

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
        val options = variants.mapIndexed { i, v -> HlsVariant(i, v.height, v.bandwidth, v.codecs) }

        output.append(best.infoLine.withoutAttribute("SUBTITLES")).append('\n')
        output.append(resolve(base, best.uri) ?: return null).append('\n')
        return HlsTrimmedMaster(output.toString(), subtitles, options, variants.indexOf(best))
    }

    /** Media playlists a trimmed (absolute-URI) master points at: renditions first, then the variant. */
    fun mediaPlaylistUris(master: String): List<String> {
        val uris = mutableListOf<String>()
        for (raw in master.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA:") ->
                    uriAttributePattern.find(line)?.groupValues?.get(1)?.let(uris::add)
                line.isNotEmpty() && !line.startsWith("#") -> uris += line
            }
        }
        return uris.distinct()
    }

    /** A VOD media playlist with every URI made absolute, plus its encryption key URLs in order. */
    class MediaPlaylist(val text: String, val keyUrls: List<String>)

    /**
     * Returns null for anything that must keep being fetched from its origin: live or event
     * playlists (no `#EXT-X-ENDLIST`, so FFmpeg has to reload them), masters, variable
     * substitution, or URIs that cannot be resolved against [base].
     */
    fun absolutizeMediaPlaylist(text: String, base: URI): MediaPlaylist? {
        val lines = text.removePrefix("﻿").lines().map { it.trimEnd('\r') }
        if (lines.firstOrNull()?.trim() != "#EXTM3U") return null
        if (lines.none { it.trim() == "#EXT-X-ENDLIST" }) return null
        if (lines.any { it.startsWith("#EXT-X-DEFINE") || it.startsWith("#EXT-X-STREAM-INF:") }) return null
        val keyUrls = mutableListOf<String>()
        val output = StringBuilder()
        for (line in lines) {
            when {
                line.isBlank() -> continue
                line.startsWith("#") -> {
                    val absolute = absolutizeUriAttribute(line, base) ?: return null
                    if (absolute.startsWith("#EXT-X-KEY:")) {
                        val attributes = parseAttributes(absolute.substringAfter(':'))
                        val uri = attributes["URI"]
                        val method = attributes["METHOD"]
                        if (method != null && method != "NONE" && uri != null &&
                            (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true))
                        ) {
                            keyUrls += uri
                        }
                    }
                    output.append(absolute).append('\n')
                }
                else -> output.append(resolve(base, line) ?: return null).append('\n')
            }
        }
        return MediaPlaylist(output.toString(), keyUrls.distinct())
    }

    /** Swaps URI lines and `URI="…"` attributes found in [replacements]; everything else is kept. */
    fun replaceUris(playlist: String, replacements: Map<String, String>): String {
        if (replacements.isEmpty()) return playlist
        return playlist.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> line
                !trimmed.startsWith("#") -> replacements[trimmed] ?: line
                else -> {
                    val match = uriAttributePattern.find(line)
                    val replacement = match?.let { replacements[it.groupValues[1]] }
                    if (match == null || replacement == null) line
                    else line.replaceRange(match.range, "URI=\"$replacement\"")
                }
            }
        }
    }

    private fun pickVariant(variants: List<Variant>, maxHeight: Int): Variant {
        val order = compareBy<Variant> { it.height }.thenBy { it.bandwidth }
        if (maxHeight <= 0) return variants.maxWith(order)
        return variants.filter { it.height in 1..maxHeight }.maxWithOrNull(order)
            ?: variants.filter { it.height > 0 }.minByOrNull { it.height }
            ?: variants.maxWith(order)
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

    // Media playlists and keys are tiny; a key can be over 1 KiB only if something is wrong.
    private const val MaxKeyBytes = 1 shl 10
    // Covers the first segment of each playlist (FFmpeg's probe) without fetching a rotating
    // key per segment up front; later keys stay on the origin.
    private const val MaxKeysPerPlaylist = 3
    private val PrefetchBudget: Duration = Duration.ofSeconds(10)

    private val prefetchExecutor = Executors.newCachedThreadPool { task ->
        Thread(task, "nuvio-hls-prefetch").apply { isDaemon = true }
    }

    /** [master]/[base] keep the fetched playlist so a quality switch re-trims it without refetching. */
    class Prepared(
        val localUrl: String,
        val subtitles: List<StreamSubtitle>,
        val variants: List<HlsVariant> = emptyList(),
        val selectedIndex: Int = -1,
        private val master: String = "",
        private val base: URI? = null,
        private val headerLines: List<String> = emptyList(),
    ) {
        /** The same master trimmed to the variant at [variantIndex], published as a new local playlist. */
        fun withVariant(variantIndex: Int): Prepared? {
            val origin = base ?: return null
            if (variantIndex !in variants.indices) return null
            return runCatching {
                val trimmed = HlsMasterTrimmer.trim(master, origin, variantIndex = variantIndex) ?: return null
                val url = LocalPlaylistServer.publish(prefetchMedia(trimmed.master, headerLines)) ?: return null
                Prepared(url, trimmed.subtitles, trimmed.variants, trimmed.selectedIndex, master, origin, headerLines)
            }.onFailure { error -> log.w(error) { "HLS variant re-trim failed" } }.getOrNull()
        }
    }

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
    fun prepare(
        sourceUrl: String,
        headerLines: List<String>,
        knownHls: Boolean = false,
        maxHeight: Int = 0,
    ): Prepared? {
        val lower = sourceUrl.lowercase(Locale.ROOT)
        val isHttp = lower.startsWith("http://") || lower.startsWith("https://")
        if (!(if (knownHls) isHttp else looksLikeHls(sourceUrl))) return null
        return runCatching {
            val master = fetch(sourceUrl, headerLines, MaxPlaylistBytes) ?: return null
            val body = String(master.bytes, Charsets.UTF_8)
            val base = master.uri
            val trimmed = HlsMasterTrimmer.trim(body, base, maxHeight = maxHeight) ?: return null
            val localUrl = LocalPlaylistServer.publish(prefetchMedia(trimmed.master, headerLines)) ?: return null
            log.d {
                "trimmed HLS master: ${trimmed.subtitles.size} subtitle renditions deferred, " +
                    "variant ${trimmed.selectedIndex + 1}/${trimmed.variants.size} cap=$maxHeight"
            }
            Prepared(localUrl, trimmed.subtitles, trimmed.variants, trimmed.selectedIndex, body, base, headerLines)
        }.onFailure { error -> log.w(error) { "HLS master trim skipped" } }.getOrNull()
    }

    /**
     * FFmpeg fetches the media playlists of [master], then the key of each playlist's first
     * segment, strictly one after another; on a slow origin that adds up to many seconds before
     * the first frame. Fetches them all in parallel instead and points [master] at local copies.
     * Anything not ready within [PrefetchBudget], live playlists and responses that set cookies
     * (FFmpeg would carry those to segment requests) keep their origin URL.
     */
    private fun prefetchMedia(master: String, headerLines: List<String>): String {
        val uris = HlsMasterTrimmer.mediaPlaylistUris(master)
        if (uris.isEmpty()) return master
        val started = System.nanoTime()
        val deadline = started + PrefetchBudget.toNanos()
        val pending = uris.associateWith { uri ->
            CompletableFuture.supplyAsync({ prefetchMediaPlaylist(uri, headerLines, deadline) }, prefetchExecutor)
        }
        val local = pending.mapNotNull { (uri, future) -> awaitUntil(future, deadline)?.let { uri to it } }.toMap()
        log.d {
            "HLS prefetch: ${local.size}/${uris.size} media playlists local in " +
                "${(System.nanoTime() - started) / 1_000_000} ms"
        }
        return HlsMasterTrimmer.replaceUris(master, local)
    }

    private fun prefetchMediaPlaylist(uri: String, headerLines: List<String>, deadline: Long): String? {
        val fetched = runCatching { fetch(uri, headerLines, MaxPlaylistBytes) }.getOrNull() ?: return null
        if (fetched.setsCookie) return null
        val media = HlsMasterTrimmer.absolutizeMediaPlaylist(String(fetched.bytes, Charsets.UTF_8), fetched.uri)
            ?: return null
        val keys = media.keyUrls.take(MaxKeysPerPlaylist).associateWith { keyUrl ->
            CompletableFuture.supplyAsync({ prefetchKey(keyUrl, headerLines) }, prefetchExecutor)
        }
        val localKeys = keys.mapNotNull { (keyUrl, future) -> awaitUntil(future, deadline)?.let { keyUrl to it } }.toMap()
        val text = HlsMasterTrimmer.replaceUris(media.text, localKeys)
        return LocalPlaylistServer.publish(text, "media.m3u8", PlaylistContentType)
    }

    private fun prefetchKey(keyUrl: String, headerLines: List<String>): String? {
        val fetched = runCatching { fetch(keyUrl, headerLines, MaxKeyBytes) }.getOrNull() ?: return null
        if (fetched.setsCookie || fetched.bytes.isEmpty()) return null
        return LocalPlaylistServer.publish(fetched.bytes, "key.bin", "application/octet-stream")
    }

    private fun <T> awaitUntil(future: CompletableFuture<T?>, deadline: Long): T? {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0 && !future.isDone) return null
        return runCatching { future.get(remaining.coerceAtLeast(0), TimeUnit.NANOSECONDS) }.getOrNull()
    }

    private class Fetched(val bytes: ByteArray, val uri: URI, val setsCookie: Boolean)

    private fun fetch(url: String, headerLines: List<String>, maxBytes: Int): Fetched? {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(6))
            .GET()
            .withSourceHeaders(headerLines)
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) return null
        var bytes = response.body()
        if (response.headers().firstValue("Content-Encoding").orElse("").equals("gzip", ignoreCase = true)) {
            bytes = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readNBytes(maxBytes + 1) }
        }
        if (bytes.size > maxBytes) return null
        return Fetched(bytes, response.uri(), response.headers().firstValue("Set-Cookie").isPresent)
    }

    private const val PlaylistContentType = "application/vnd.apple.mpegurl"

    private object LocalPlaylistServer {
        private const val MaxEntries = 256
        private const val MaxTotalBytes = 32L shl 20

        private class Entry(val bytes: ByteArray, val contentType: String)

        // Insertion order: the current playback's entries are always the newest, so evicting
        // the oldest never drops a playlist or key the playing stream may still request.
        private var totalBytes = 0L // Guarded by playlists.
        private val playlists = LinkedHashMap<String, Entry>()

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
                                val entry = synchronized(playlists) { playlists[token] }
                                when {
                                    entry == null -> exchange.sendResponseHeaders(404, -1)
                                    exchange.requestMethod.equals("HEAD", ignoreCase = true) -> {
                                        exchange.responseHeaders.add("Content-Type", entry.contentType)
                                        exchange.sendResponseHeaders(200, -1)
                                    }
                                    else -> {
                                        exchange.responseHeaders.add("Content-Type", entry.contentType)
                                        exchange.sendResponseHeaders(200, entry.bytes.size.toLong())
                                        exchange.responseBody.write(entry.bytes)
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

        fun publish(master: String): String? =
            publish(master.toByteArray(Charsets.UTF_8), "master.m3u8", PlaylistContentType)

        fun publish(text: String, name: String, contentType: String): String? =
            publish(text.toByteArray(Charsets.UTF_8), name, contentType)

        fun publish(bytes: ByteArray, name: String, contentType: String): String? {
            val active = server ?: return null
            val token = UUID.randomUUID().toString()
            synchronized(playlists) {
                totalBytes += bytes.size
                playlists[token] = Entry(bytes, contentType)
                val oldest = playlists.entries.iterator()
                while (playlists.size > 1 && (playlists.size > MaxEntries || totalBytes > MaxTotalBytes)) {
                    totalBytes -= oldest.next().value.bytes.size
                    oldest.remove()
                }
            }
            return "http://127.0.0.1:${active.address.port}/hls/$token/$name"
        }
    }
}

/**
 * Picker entries for [variants], highest first. Exact duplicates (same height, bitrate and
 * codecs, e.g. one per audio group) collapse to the first; same-height entries name their codec.
 */
internal fun hlsQualityOptions(variants: List<HlsVariant>): List<PlayerControlQualityOption> {
    val distinct = variants.distinctBy { Triple(it.height, it.bandwidth, it.codecs) }
        .sortedWith(compareByDescending<HlsVariant> { it.height }.thenByDescending { it.bandwidth })
    val heightCounts = distinct.groupingBy { it.height }.eachCount()
    return distinct.map { variant ->
        val parts = mutableListOf<String>()
        if (variant.height > 0) parts += "${variant.height}p"
        if (variant.bandwidth > 0) parts += formatHlsBitrate(variant.bandwidth)
        if ((heightCounts[variant.height] ?: 0) > 1) hlsCodecName(variant.codecs)?.let { parts += it }
        PlayerControlQualityOption(variant.index, parts.joinToString(" · ").ifEmpty { "#${variant.index + 1}" })
    }
}

/** The picker entry standing for the variant [prepared] plays (its duplicate's, if collapsed). */
internal fun selectedQualityOption(prepared: HlsStartupTrim.Prepared): Int {
    val playing = prepared.variants.getOrNull(prepared.selectedIndex) ?: return -1
    return prepared.variants.first {
        it.height == playing.height && it.bandwidth == playing.bandwidth && it.codecs == playing.codecs
    }.index
}

private fun formatHlsBitrate(bitsPerSecond: Long): String =
    if (bitsPerSecond >= 1_000_000L) {
        String.format(Locale.ROOT, "%.1f Mbps", bitsPerSecond / 1_000_000.0)
    } else {
        "${bitsPerSecond / 1_000L} kbps"
    }

private fun hlsCodecName(codecs: String): String? {
    val video = codecs.split(',').map { it.trim().lowercase(Locale.ROOT) }
    return when {
        video.any { it.startsWith("hvc1") || it.startsWith("hev1") } -> "HEVC"
        video.any { it.startsWith("av01") } -> "AV1"
        video.any { it.startsWith("vp09") || it.startsWith("vp9") } -> "VP9"
        video.any { it.startsWith("avc1") || it.startsWith("avc3") } -> "AVC"
        else -> null
    }
}
