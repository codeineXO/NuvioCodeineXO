package com.nuvio.app.features.player.desktop

import co.touchlab.kermit.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Some addons hand out a gateway URL that answers every request with a short-lived temporary
 * redirect to the real file host (e.g. a 307 cacheable for 30 s pointing at a signed storage URL).
 * FFmpeg follows it again on every open and seek, so an MKV resume (head, index at the file end,
 * back, resume point) pays the gateway several times, at 0.5-1 s or more each. Resolving the
 * redirect once and handing mpv the final URL keeps every later request on the file host.
 *
 * Hosts that keep answering directly (and never redirected) are skipped for the rest of the
 * session.
 */
internal object DirectSourceRedirect {
    private val log = Logger.withTag("DirectSourceRedirect")
    // A gateway answers in well under a second; when it stalls, mpv's own request would stall
    // too, so stop waiting early and let mpv open the original URL.
    private val TotalBudget: Duration = Duration.ofSeconds(3)
    private val directHosts = ConcurrentHashMap.newKeySet<String>()
    // A gateway may serve some files itself and redirect others; once a host has redirected,
    // it is always checked.
    private val redirectingHosts = ConcurrentHashMap.newKeySet<String>()
    // Skip the check for a host only after it answered directly a few times and never redirected.
    private const val DirectAnswersToSkip = 3
    private val directAnswers = ConcurrentHashMap<String, Int>()

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(3))
            .build()
    }

    /** Returns the URL mpv should open: the redirect target, or [sourceUrl] unchanged. */
    fun resolve(sourceUrl: String, headerLines: List<String>): String {
        val start = runCatching { URI.create(sourceUrl) }.getOrNull() ?: return sourceUrl
        val startHost = start.host?.lowercase(Locale.ROOT) ?: return sourceUrl
        if (!start.isHttp() || isLoopback(startHost) || startHost in directHosts) return sourceUrl
        if (HlsStartupTrim.looksLikeHls(sourceUrl)) return sourceUrl

        // One request to the gateway only. The target is not requested here: mpv's first read
        // goes there anyway (and follows any further redirect itself), so checking it would
        // only add a round trip, over a second on slow file hosts.
        val response = runCatching { probe(start, headerLines, TotalBudget) }
            .getOrElse { error -> return fallback(sourceUrl, error.javaClass.simpleName) }
        val status = response.statusCode()
        if (status !in 300..399) {
            if (status in 200..299 && startHost !in redirectingHosts &&
                directAnswers.merge(startHost, 1, Int::plus)!! >= DirectAnswersToSkip
            ) {
                directHosts += startHost
            }
            log.d { "no redirect from $startHost (status $status)" }
            return sourceUrl
        }
        redirectingHosts += startHost
        directHosts -= startHost
        val location = response.headers().firstValue("Location").orElse(null)
            ?: return fallback(sourceUrl, "redirect without Location")
        val target = runCatching { start.resolve(location.trim()) }.getOrNull()
            ?.takeIf { it.isHttp() }
            ?: return fallback(sourceUrl, "unusable Location")
        logResolved(start, target)
        return target.toString()
    }

    private fun probe(uri: URI, headerLines: List<String>, timeout: Duration): HttpResponse<Void> {
        val request = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .GET()
            .withSourceHeaders(headerLines)
            .header("Range", "bytes=0-0")
            .build()
        return client.send(request, HttpResponse.BodyHandlers.discarding())
    }

    private fun fallback(sourceUrl: String, reason: String): String {
        log.d { "redirect not resolved ($reason); playing the original URL" }
        return sourceUrl
    }

    private fun logResolved(from: URI, to: URI) {
        log.d { "resolved redirect ${from.host} -> ${to.host}" }
    }

    private fun URI.isHttp(): Boolean =
        scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)

    // Literal check only: a DNS lookup here would delay every start.
    private fun isLoopback(host: String): Boolean =
        host == "localhost" || host.endsWith(".localhost") || host.startsWith("127.") ||
            host == "::1" || host == "[::1]"
}
