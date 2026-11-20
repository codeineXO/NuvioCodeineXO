package com.nuvio.app.features.player.desktop

import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.languageMatchesPreference
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
/** One playable quality of a master playlist; [index] is its position among the master's variants. */
internal data class HlsVariant(val index: Int, val height: Int, val bandwidth: Long, val codecs: String)

/** One entry of the player's Quality picker; [variantIndex] identifies the HLS variant to play. */
internal data class PlayerControlQualityOption(val variantIndex: Int, val label: String)

/** One audio rendition of the playing variant's group; [index] is its position in that group. */
internal data class HlsAudioRendition(val index: Int, val language: String?, val name: String?)

internal data class HlsTrimmedMaster(
    val master: String,
    val subtitles: List<StreamSubtitle>,
    val variants: List<HlsVariant> = emptyList(),
    val selectedIndex: Int = -1,
    val audioRenditions: List<HlsAudioRendition> = emptyList(),
    val selectedAudioIndex: Int = -1,
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
     * Returns null when [master] is not a master playlist (or cannot be rewritten safely).
     * Keeps the variant at [variantIndex] when given, else the best one no taller than [maxHeight]
     * (0 = no cap).
     */
    fun trim(
        master: String,
        base: URI,
        maxHeight: Int = 0,
        variantIndex: Int? = null,
        audioLanguages: List<String> = emptyList(),
        audioName: String? = null,
    ): HlsTrimmedMaster? {
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

        // FFmpeg opens (and probes the first segment of) every audio rendition in the group, so
        // keep only the one that will play, like ExoPlayer on mobile. Renditions without a URI
        // are muxed into the variant and cost nothing, so they stay.
        val audioGroup = best.attributes["AUDIO"]
        val audioRenditions = lines.filter { it.startsWith("#EXT-X-MEDIA:") }
            .map { parseAttributes(it.substringAfter(':')) }
            .filter { it["TYPE"] == "AUDIO" && it["GROUP-ID"] == audioGroup && it["URI"] != null }
        val chosenAudio = pickAudioRendition(audioRenditions, audioName, audioLanguages)
        val audioOptions = audioRenditions.mapIndexed { i, attributes ->
            HlsAudioRendition(
                index = i,
                language = attributes["LANGUAGE"]?.takeIf { it.isNotBlank() },
                name = attributes["NAME"]?.takeIf { it.isNotBlank() },
            )
        }

        val subtitleGroup = best.attributes["SUBTITLES"]
        val subtitles = mutableListOf<StreamSubtitle>()
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
                        "AUDIO" -> group == audioGroup &&
                            (attributes["URI"] == null || chosenAudio == null || attributes == chosenAudio)
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
                }
                line.startsWith("#") -> output.append(absolutizeUriAttribute(line, base) ?: return null).append('\n')
                else -> Unit
            }
        }
        // Nothing dropped still returns the (absolutized) master: the caller already downloaded
        // it, and serving that copy locally spares mpv fetching it again, twice (its own format
        // probe, then FFmpeg's demuxer), which on slow origins took several seconds.
        val options = variants.mapIndexed { i, v -> HlsVariant(i, v.height, v.bandwidth, v.codecs) }

        output.append(best.infoLine.withoutAttribute("SUBTITLES")).append('\n')
        output.append(resolve(base, best.uri) ?: return null).append('\n')
        return HlsTrimmedMaster(
            master = output.toString(),
            subtitles = subtitles,
            variants = options,
            selectedIndex = variants.indexOf(best),
            audioRenditions = audioOptions,
            selectedAudioIndex = chosenAudio?.let(audioRenditions::indexOf) ?: -1,
        )
    }

    /** The saved track's name, then the preferred languages in order, then the stream's default. */
    private fun pickAudioRendition(
        renditions: List<Map<String, String>>,
        audioName: String?,
        audioLanguages: List<String>,
    ): Map<String, String>? {
        if (renditions.isEmpty()) return null
        audioName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            renditions.firstOrNull { it["NAME"].equals(name, ignoreCase = true) }?.let { return it }
        }
        for (language in audioLanguages) {
            renditions.firstOrNull { languageMatchesPreference(it["LANGUAGE"], language) }?.let { return it }
        }
        return renditions.firstOrNull { it["DEFAULT"] == "YES" } ?: renditions.first()
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

    /** [master]/[base] keep the fetched playlist so a quality switch re-trims it without refetching. */
    class Prepared(
        val localUrl: String,
        val subtitles: List<StreamSubtitle>,
        val variants: List<HlsVariant> = emptyList(),
        val selectedIndex: Int = -1,
        private val master: String = "",
        private val base: URI? = null,
        val audioRenditions: List<HlsAudioRendition> = emptyList(),
        val selectedAudioIndex: Int = -1,
    ) {
        val selectedAudio: HlsAudioRendition? get() = audioRenditions.getOrNull(selectedAudioIndex)

        /** The same master trimmed to the variant at [variantIndex], keeping the playing audio. */
        fun withVariant(variantIndex: Int): Prepared? {
            if (variantIndex !in variants.indices) return null
            return retrim(variantIndex, selectedAudio)
        }

        /** The same variant with the audio rendition at [audioIndex] instead. */
        fun withAudio(audioIndex: Int): Prepared? {
            val audio = audioRenditions.getOrNull(audioIndex) ?: return null
            return retrim(selectedIndex, audio)
        }

        private fun retrim(variantIndex: Int, audio: HlsAudioRendition?): Prepared? {
            val origin = base ?: return null
            return runCatching {
                // A variant may use another audio group: carry the choice over by name and language.
                val trimmed = HlsMasterTrimmer.trim(
                    master,
                    origin,
                    variantIndex = variantIndex,
                    audioLanguages = listOfNotNull(audio?.language),
                    audioName = audio?.name,
                ) ?: return null
                val url = LocalPlaylistServer.publish(trimmed.master) ?: return null
                trimmed.toPrepared(url, master, origin)
            }.onFailure { error -> log.w(error) { "HLS re-trim failed" } }.getOrNull()
        }
    }

    private fun HlsTrimmedMaster.toPrepared(localUrl: String, master: String, base: URI) = Prepared(
        localUrl = localUrl,
        subtitles = subtitles,
        variants = variants,
        selectedIndex = selectedIndex,
        master = master,
        base = base,
        audioRenditions = audioRenditions,
        selectedAudioIndex = selectedAudioIndex,
    )

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
        audioLanguages: List<String> = emptyList(),
    ): Prepared? {
        val lower = sourceUrl.lowercase(Locale.ROOT)
        val isHttp = lower.startsWith("http://") || lower.startsWith("https://")
        if (!(if (knownHls) isHttp else looksLikeHls(sourceUrl))) return null
        return runCatching {
            val (body, base) = fetch(sourceUrl, headerLines) ?: return null
            val trimmed = HlsMasterTrimmer.trim(body, base, maxHeight = maxHeight, audioLanguages = audioLanguages)
                ?: return null
            val localUrl = LocalPlaylistServer.publish(trimmed.master) ?: return null
            log.d {
                "trimmed HLS master: ${trimmed.subtitles.size} subtitle renditions deferred, " +
                    "variant ${trimmed.selectedIndex + 1}/${trimmed.variants.size} cap=$maxHeight, " +
                    "audio ${trimmed.selectedAudioIndex + 1}/${trimmed.audioRenditions.size}"
            }
            trimmed.toPrepared(localUrl, body, base)
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
