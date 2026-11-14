package com.nuvio.app.features.player.desktop

import com.nuvio.app.core.storage.DesktopStorage
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal class HlsSegmentRetryProxy private constructor(
    private val origin: HttpUrl,
    private val headers: Map<String, String>,
    private val client: OkHttpClient,
    private val logPath: Path?,
) : AutoCloseable {
    private enum class Kind { Playlist, Key, Segment, Other }
    private data class Resource(val url: HttpUrl, val kind: Kind)
    private data class Payload(val bytes: ByteArray, val contentType: String?, val contentRange: String?, val status: Int)
    private class ResponseFailure(val reason: String) : IOException(reason)

    private val token = UUID.randomUUID().toString()
    private val workers = ThreadPoolExecutor(
        4, 4, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(8),
        { task -> Thread(task, "nuvio-hls-retry").apply { isDaemon = true } },
    )
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
    private val resources = ConcurrentHashMap<String, Resource>()
    private val routes = HashMap<Resource, String>()
    val playbackUrl = "http://127.0.0.1:${server.address.port}/$token/start.m3u8"

    init {
        resources["/$token/start.m3u8"] = Resource(origin, Kind.Playlist)
        server.createContext("/") { exchange ->
            val resource = resources[exchange.requestURI.rawPath]
            when {
                resource == null -> reject(exchange, 404)
                exchange.requestMethod != "GET" -> reject(exchange, 405)
                else -> try {
                    workers.execute { serve(exchange, resource) }
                } catch (_: RejectedExecutionException) {
                    if (workers.isShutdown) {
                        exchange.close()
                    } else {
                        log("HLS proxy rejected reason=queue_full kind=${resource.kind} localStatus=503")
                        reject(exchange, 503)
                    }
                }
            }
        }
        server.start()
    }

    private fun reject(exchange: HttpExchange, status: Int) {
        exchange.use { it.sendResponseHeaders(status, -1) }
    }

    private fun serve(exchange: HttpExchange, resource: Resource) {
        exchange.use {
            val range = exchange.requestHeaders.getFirst("Range")
            val target = if (resource.kind == Kind.Playlist) withReloadParameters(resource, exchange) else resource
            val payload = try {
                fetch(target, range)
            } catch (_: IOException) {
                exchange.sendResponseHeaders(502, -1)
                return
            }
            payload.contentType?.let { exchange.responseHeaders.set("Content-Type", it) }
            payload.contentRange?.let { exchange.responseHeaders.set("Content-Range", it) }
            try {
                exchange.sendResponseHeaders(payload.status, payload.bytes.size.toLong().takeIf { it > 0 } ?: -1)
                if (payload.bytes.isNotEmpty()) exchange.responseBody.write(payload.bytes)
            } catch (_: IOException) {
                // mpv can close the local request when seeking or changing source.
            }
        }
    }

    private fun withReloadParameters(resource: Resource, exchange: HttpExchange): Resource {
        val query = exchange.requestURI.rawQuery ?: return resource
        val local = (playbackUrl.substringBeforeLast('/') + "/reload?" + query).toHttpUrlOrNull() ?: return resource
        val url = resource.url.newBuilder().apply {
            for (name in listOf("_HLS_msn", "_HLS_part", "_HLS_skip")) {
                local.queryParameter(name)?.let { setQueryParameter(name, it) }
            }
        }.build()
        return resource.copy(url = url)
    }

    private fun fetch(resource: Resource, range: String?): Payload {
        val request = request(resource.url, headers, range.takeUnless { resource.kind == Kind.Playlist })
        val attempts = if (resource.kind == Kind.Segment) 2 else 1
        val requestId = if (logPath != null) nextRequestId.incrementAndGet() else 0L
        repeat(attempts) { attempt ->
            var status: Int? = null
            var contentRange: String? = null
            var declaredBytes: Long? = null
            var receivedBytes: Long? = null
            fun report(outcome: String, reason: String) {
                if (logPath == null) return
                val requested = safeRange(request.header("Range"), loggedRequestRange)
                val returned = safeRange(contentRange, loggedResponseRange)
                log(
                    "HLS request id=$requestId kind=${resource.kind} attempt=${attempt + 1}/$attempts " +
                        "outcome=$outcome reason=$reason upstreamStatus=${status ?: "-"} " +
                        "requestedRange=$requested returnedRange=$returned " +
                        "declaredBytes=${declaredBytes ?: "-"} receivedBytes=${receivedBytes ?: "-"}",
                )
            }
            try {
                val payload = client.newCall(request).execute().use { response ->
                    status = response.code
                    contentRange = response.header("Content-Range")
                    if (!response.isSuccessful) {
                        if (resource.kind == Kind.Segment && response.code in retryStatusCodes) {
                            throw ResponseFailure("upstream_http_error")
                        }
                        report("failed", "upstream_http_error")
                        return@use Payload(ByteArray(0), null, null, response.code)
                    }
                    declaredBytes = response.body?.contentLength()?.takeIf { it >= 0 }
                    val bytes = readBody(response, if (resource.kind == Kind.Playlist) playlistLimit else segmentLimit) {
                        receivedBytes = it
                    }
                    if (resource.kind == Kind.Segment) verifySegment(bytes, range, response.code, contentRange)
                    if (resource.kind == Kind.Playlist) {
                        val playlist = rewritePlaylist(bytes.toString(Charsets.UTF_8), response.request.url)
                        Payload(playlist.toByteArray(), "application/vnd.apple.mpegurl", null, 200)
                    } else {
                        Payload(bytes, response.header("Content-Type"), contentRange, response.code)
                    }
                }
                if (attempt > 0 && payload.status in 200..299) report("recovered", "complete_response")
                return payload
            } catch (error: IOException) {
                val expected = declaredBytes
                val received = receivedBytes
                val reason = when {
                    error is ResponseFailure -> error.reason
                    error is InterruptedIOException -> "request_timeout_or_interrupted"
                    status == null -> "upstream_connection_failed"
                    expected != null && received != null && received < expected -> "incomplete_response_body"
                    else -> "upstream_read_failed"
                }
                val retrying = attempt + 1 < attempts
                report(if (retrying) "retrying_from_range_start" else "failed", reason)
                if (!retrying) throw error
            }
        }
        error("No HLS request attempts")
    }

    private fun rewritePlaylist(text: String, base: HttpUrl): String {
        if (!isPlaylist(text)) throw ResponseFailure("invalid_playlist")
        val master = text.lineSequence().any { it.startsWith("#EXT-X-STREAM-INF:") }
        return text.trimStart('\uFEFF').lineSequence().joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                !line.startsWith("#") -> route(base, line.trim(), if (master) Kind.Playlist else Kind.Segment)
                else -> {
                    val kind = when (line.substringBefore(':')) {
                        "#EXT-X-KEY", "#EXT-X-SESSION-KEY" -> Kind.Key
                        "#EXT-X-MAP", "#EXT-X-PART", "#EXT-X-PRELOAD-HINT" -> Kind.Segment
                        "#EXT-X-MEDIA", "#EXT-X-I-FRAME-STREAM-INF", "#EXT-X-RENDITION-REPORT" -> Kind.Playlist
                        else -> Kind.Other
                    }
                    if (kind == Kind.Other) line else uriAttribute.replace(line) { match ->
                        val ref = match.groupValues[2]
                        val target = route(base, ref, kind)
                        "${match.groupValues[1]}\"$target\""
                    }
                }
            }
        }
    }

    private fun route(base: HttpUrl, reference: String, kind: Kind): String {
        val url = base.resolve(reference) ?: return reference
        val resource = Resource(url, kind)
        return synchronized(routes) {
            routes.getOrPut(resource) {
                if (routes.size >= 100_000) throw ResponseFailure("playlist_route_limit")
                val suffix = when (kind) {
                    Kind.Playlist -> ".m3u8"
                    Kind.Key -> ".key"
                    // FFmpeg checks the extension of segment URLs when probing.
                    else -> url.pathSegments.last().substringAfterLast('.', "")
                        .takeIf { extension.matches(it) }?.let { ".$it" } ?: ".ts"
                }
                "/$token/${routes.size}$suffix".also { resources[it] = resource }
            }
        }
    }

    override fun close() {
        workers.shutdownNow()
        client.dispatcher.cancelAll()
        server.stop(0)
        client.connectionPool.evictAll()
    }

    private fun log(message: String) = writeLog(logPath, message)

    companion object {
        private const val playlistLimit = 4 * 1024 * 1024
        private const val segmentLimit = 32 * 1024 * 1024
        private val logLock = Any()
        private val nextRequestId = AtomicLong()
        private val retryStatusCodes = setOf(408, 429, 500, 502, 503, 504)
        private val rangeRequest = Regex("""bytes=(\d+)-(\d*)""")
        private val rangeResponse = Regex("""bytes (\d+)-(\d+)/(?:\d+|\*)""")
        private val loggedRequestRange = Regex("""bytes=(?:\d{1,20}-\d{0,20}|-\d{1,20})""", RegexOption.IGNORE_CASE)
        private val loggedResponseRange = Regex("""bytes (?:\d{1,20}-\d{1,20}/(?:\d{1,20}|\*)|\*/\d{1,20})""", RegexOption.IGNORE_CASE)
        private val uriAttribute = Regex("""((?:[:,])URI=)"([^"]+)"""")
        private val extension = Regex("[a-zA-Z0-9]{1,8}")
        private val ignoredHeaders = setOf("host", "connection", "content-length", "transfer-encoding", "range", "accept-encoding")
        private const val defaultUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        fun open(source: String, headers: Map<String, String>, logging: Boolean): HlsSegmentRetryProxy? =
            open(source, headers, if (logging) DesktopStorage.rootDir.resolve("hls-retry.log") else null)

        fun logStartupFallback() {
            writeLog(DesktopStorage.rootDir.resolve("hls-retry.log"), "HLS proxy bypassed reason=startup_timeout")
        }

        internal fun open(source: String, headers: Map<String, String>, logPath: Path?): HlsSegmentRetryProxy? {
            val url = source.toHttpUrlOrNull()
            if (url == null) {
                writeLog(logPath, "HLS proxy bypassed reason=non_http_source")
                return null
            }
            val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS)
                .addNetworkInterceptor { chain ->
                    val request = chain.request()
                    val destination = request.url
                    val sameOrigin = destination.host == url.host && destination.port == url.port &&
                        destination.scheme == url.scheme
                    chain.proceed(if (sameOrigin) request else request.newBuilder()
                        .removeHeader("Authorization").removeHeader("Cookie").build())
                }.build()
            var upstreamStatus: Int? = null
            val probe = runCatching {
                // Probe the playlist signature so extensionless HLS links work too.
                client.newCall(request(url, headers, null)).execute().use { response ->
                    upstreamStatus = response.code
                    response.isSuccessful && isPlaylist(response.peekBody(64).string())
                }
            }
            val proxy = if (probe.getOrDefault(false)) {
                runCatching { HlsSegmentRetryProxy(url, headers, client, logPath) }.getOrNull()
            } else {
                null
            }
            val status = upstreamStatus
            if (proxy == null) {
                client.connectionPool.evictAll()
                val reason = when {
                    probe.isFailure -> "probe_failed"
                    status != null && status !in 200..299 -> "upstream_http_error"
                    probe.getOrDefault(false) -> "proxy_start_failed"
                    else -> "not_hls"
                }
                writeLog(logPath, "HLS proxy bypassed reason=$reason upstreamStatus=${status ?: "-"}")
            } else {
                writeLog(logPath, "HLS proxy active upstreamStatus=$status")
            }
            return proxy
        }

        private fun writeLog(path: Path?, message: String) {
            if (path == null) return
            synchronized(logLock) {
                runCatching {
                    val options = if (Files.exists(path) && Files.size(path) >= 512 * 1024) {
                        StandardOpenOption.TRUNCATE_EXISTING
                    } else {
                        StandardOpenOption.APPEND
                    }
                    Files.writeString(path, "${Instant.now()} $message\n", StandardOpenOption.CREATE, options)
                }
            }
        }

        private fun request(url: HttpUrl, headers: Map<String, String>, range: String?): Request =
            Request.Builder().url(url).apply {
                header("User-Agent", defaultUserAgent)
                headers.forEach { (name, value) ->
                    if (name.lowercase() !in ignoredHeaders) header(name, value)
                }
                // Content-Length must describe the same bytes that we read.
                header("Accept-Encoding", "identity")
                range?.let { header("Range", it) }
            }.build()

        private fun isPlaylist(text: String): Boolean =
            text.trimStart('\uFEFF').lineSequence().firstOrNull()?.trimEnd() == "#EXTM3U"

        private fun safeRange(value: String?, format: Regex): String = when {
            value == null -> "-"
            format.matches(value) -> value.replace(' ', '=')
            else -> "invalid"
        }

        private fun readBody(response: Response, limit: Int, recordBytes: (Long) -> Unit): ByteArray {
            val body = response.body ?: throw ResponseFailure("missing_response_body")
            val expected = body.contentLength()
            if (expected > limit) throw ResponseFailure("response_exceeds_buffer_limit")
            val source = body.source()
            try {
                if (source.request(limit.toLong() + 1)) throw ResponseFailure("response_exceeds_buffer_limit")
            } finally {
                recordBytes(source.buffer.size)
            }
            val bytes = source.readByteArray()
            if (expected >= 0 && bytes.size.toLong() != expected) throw ResponseFailure("incomplete_response_body")
            return bytes
        }

        private fun verifySegment(bytes: ByteArray, range: String?, status: Int, contentRange: String?) {
            if (bytes.isEmpty()) throw ResponseFailure("empty_segment")
            val requested = range?.let(rangeRequest::matchEntire) ?: return
            if (status == 200 && range.equals("bytes=0-", ignoreCase = true)) return
            if (status != 206) throw ResponseFailure("upstream_ignored_range")
            if (contentRange == null) throw ResponseFailure("missing_content_range")
            val received = rangeResponse.matchEntire(contentRange) ?: throw ResponseFailure("invalid_content_range")
            val start = received.groupValues[1].toLongOrNull() ?: throw ResponseFailure("invalid_content_range")
            val end = received.groupValues[2].toLongOrNull() ?: throw ResponseFailure("invalid_content_range")
            val requestedEnd = requested.groupValues[2].toLongOrNull()
            if (end < start) throw ResponseFailure("invalid_content_range")
            if (start != requested.groupValues[1].toLongOrNull() || requestedEnd != null && end != requestedEnd) {
                throw ResponseFailure("returned_range_mismatch")
            }
            if (end - start != bytes.size.toLong() - 1) throw ResponseFailure("range_body_length_mismatch")
        }
    }
}
