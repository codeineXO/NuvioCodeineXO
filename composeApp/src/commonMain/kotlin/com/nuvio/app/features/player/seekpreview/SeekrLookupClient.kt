package com.nuvio.app.features.player.seekpreview

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** Outcome of a `/sprites` lookup; every failure maps to "no previews" for the UI. */
internal sealed interface SeekrLookupResult {
    /** [cues] is empty when the caller asked to skip the VTT download. */
    data class Loaded(val vttUrl: String, val cues: List<SeekrCue>, val exact: Boolean) : SeekrLookupResult
    data object NoPreviews : SeekrLookupResult
    data object Unauthorized : SeekrLookupResult
    data class RateLimited(val retryAfterSec: Int?) : SeekrLookupResult
    data class Rejected(val status: Int, val error: String) : SeekrLookupResult
    data object Unavailable : SeekrLookupResult
}

internal object SeekrLookupClient {
    private const val BASE_URL = "https://api.seekr.tv"
    private const val MAX_RETRY_AFTER_SEC = 60
    private const val DEFAULT_RETRY_AFTER_SEC = 5
    private const val TRANSIENT_RETRY_DELAY_MS = 3_000L
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    suspend fun validateKey(apiKey: String): Boolean = withContext(Dispatchers.Default) {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) return@withContext false
        val response = runCatching {
            httpRequestRaw(
                method = "GET",
                url = "$BASE_URL/v1/keys/validate",
                headers = mapOf("X-API-Key" to trimmed),
                body = "",
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            return@withContext false
        }

        if (response.status !in 200..299) return@withContext false
        runCatching {
            json.decodeFromString<KeyValidation>(response.body).valid
        }.getOrDefault(false)
    }

    /** One lookup, plus a single retry for 429 (after Retry-After) and 5xx/network failures. */
    suspend fun lookupWithRetry(
        content: SeekrContent,
        durationMs: Long,
        apiKey: String,
        fetchCues: Boolean,
    ): SeekrLookupResult {
        val first = lookup(content, durationMs, apiKey, fetchCues)
        val retryDelayMs = retryDelayMs(first) ?: return first
        delay(retryDelayMs)
        return lookup(content, durationMs, apiKey, fetchCues)
    }

    private fun retryDelayMs(result: SeekrLookupResult): Long? =
        when (result) {
            is SeekrLookupResult.RateLimited ->
                (result.retryAfterSec ?: DEFAULT_RETRY_AFTER_SEC).coerceIn(1, MAX_RETRY_AFTER_SEC) * 1_000L
            SeekrLookupResult.Unavailable -> TRANSIENT_RETRY_DELAY_MS + Random.nextLong(0, 1_000)
            else -> null
        }

    suspend fun lookup(
        content: SeekrContent,
        durationMs: Long,
        apiKey: String,
        fetchCues: Boolean,
    ): SeekrLookupResult = withContext(Dispatchers.Default) {
        val trimmedKey = apiKey.trim()
        if (durationMs <= 0L || trimmedKey.isBlank()) return@withContext SeekrLookupResult.Unauthorized

        val response = runCatching {
            httpRequestRaw(
                method = "GET",
                url = spritesUrl(content, durationMs),
                headers = mapOf("X-API-Key" to trimmedKey),
                body = "",
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            return@withContext SeekrLookupResult.Unavailable
        }

        if (response.status !in 200..299) {
            return@withContext classifyFailure(response.status, response.body, response.headers)
        }

        val lookup = runCatching {
            json.decodeFromString<SpriteLookup>(response.body)
        }.getOrNull()?.takeIf { it.vttUrl.isNotBlank() } ?: return@withContext SeekrLookupResult.Unavailable

        // st=1 makes the sprites worker return cue times on the client timeline (applies `scale`).
        val vttUrl = lookup.vttUrl + if (lookup.vttUrl.contains('?')) "&st=1" else "?st=1"
        if (!fetchCues) return@withContext SeekrLookupResult.Loaded(vttUrl, emptyList(), lookup.exact)

        val vttResponse = runCatching {
            httpRequestRaw(
                method = "GET",
                url = vttUrl,
                headers = emptyMap(),
                body = "",
                maxResponseBodyBytes = 4 * 1024 * 1024,
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            return@withContext SeekrLookupResult.Unavailable
        }
        if (vttResponse.status !in 200..299 || vttResponse.body.isBlank()) {
            return@withContext SeekrLookupResult.Unavailable
        }

        val cues = VttParser.parse(vttResponse.body, vttUrl)
        if (cues.isEmpty()) SeekrLookupResult.NoPreviews else SeekrLookupResult.Loaded(vttUrl, cues, lookup.exact)
    }

    /** Ids are digits or `tt\d+` (see [SeekrContentMapping]), so no URL encoding is needed. */
    private fun spritesUrl(content: SeekrContent, durationMs: Long): String {
        val params = mutableListOf("duration_ms=$durationMs")
        when (content) {
            is SeekrContent.Movie -> {
                content.imdbId?.let { params += "imdb_id=$it" }
                content.tmdbId?.let { params += "tmdb_id=$it" }
            }
            is SeekrContent.Episode -> {
                content.showImdbId?.let { params += "show_imdb_id=$it" }
                content.showTmdbId?.let { params += "show_tmdb_id=$it" }
                params += "season=${content.season}"
                params += "episode=${content.episode}"
            }
            is SeekrContent.Anime -> {
                params += "kitsu_id=${content.kitsuId}"
                content.episode?.let { params += "episode=$it" }
            }
        }
        return "$BASE_URL/sprites?" + params.joinToString("&")
    }

    private fun classifyFailure(status: Int, body: String, headers: Map<String, String>): SeekrLookupResult {
        val error = runCatching { json.decodeFromString<SeekrErrorBody>(body).error }.getOrDefault("")
        return when (status) {
            404 -> SeekrLookupResult.NoPreviews
            401 -> SeekrLookupResult.Unauthorized
            429 -> SeekrLookupResult.RateLimited(
                headers.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
                    ?.value?.trim()?.toIntOrNull(),
            )
            in 400..499 -> SeekrLookupResult.Rejected(status, error)
            else -> SeekrLookupResult.Unavailable
        }
    }
}
