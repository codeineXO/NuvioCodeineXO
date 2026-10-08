package com.nuvio.app.features.player.seekpreview

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal data class SeekrTrackResult(
    val cues: List<SeekrCue>,
    val vttUrl: String,
)

internal object SeekrLookupClient {
    private const val BASE_URL = "https://api.seekr.tv"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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
        }.getOrNull() ?: return@withContext false

        if (response.status !in 200..299) return@withContext false
        runCatching {
            json.decodeFromString<KeyValidation>(response.body).valid
        }.getOrDefault(false)
    }

    suspend fun loadTrackResult(
        content: SeekrContent,
        durationMs: Long,
        apiKey: String,
    ): SeekrTrackResult? = withContext(Dispatchers.Default) {
        val trimmedKey = apiKey.trim()
        val queryParams = mutableListOf<String>()
        if (durationMs > 0L) {
            queryParams += "duration_ms=$durationMs"
        }

        when (content) {
            is SeekrContent.Movie -> {
                content.imdbId?.let { queryParams += "imdb_id=$it" }
                content.tmdbId?.let { queryParams += "tmdb_id=$it" }
            }
            is SeekrContent.Episode -> {
                content.showImdbId?.let { queryParams += "show_imdb_id=$it" }
                content.showTmdbId?.let { queryParams += "show_tmdb_id=$it" }
                queryParams += "season=${content.season}"
                queryParams += "episode=${content.episode}"
            }
        }

        val url = "$BASE_URL/sprites?" + queryParams.joinToString("&")
        val headers = if (trimmedKey.isNotBlank()) mapOf("X-API-Key" to trimmedKey) else emptyMap()

        val response = runCatching {
            httpRequestRaw(
                method = "GET",
                url = url,
                headers = headers,
                body = "",
            )
        }.getOrNull() ?: return@withContext null

        if (response.status !in 200..299) return@withContext null

        val lookup = runCatching {
            json.decodeFromString<SpriteLookup>(response.body)
        }.getOrNull() ?: return@withContext null

        val vttUrl = if (lookup.vttUrl.contains('?')) {
            lookup.vttUrl + "&st=1"
        } else {
            lookup.vttUrl + "?st=1"
        }

        val vttResponse = runCatching {
            httpRequestRaw(
                method = "GET",
                url = vttUrl,
                headers = emptyMap(),
                body = "",
            )
        }.getOrNull() ?: return@withContext null

        if (vttResponse.status !in 200..299 || vttResponse.body.isBlank()) return@withContext null

        val cues = VttParser.parse(vttResponse.body, vttUrl)
        SeekrTrackResult(cues = cues, vttUrl = vttUrl)
    }

    suspend fun loadCues(
        content: SeekrContent,
        durationMs: Long,
        apiKey: String,
    ): List<SeekrCue>? = loadTrackResult(content, durationMs, apiKey)?.cues
}
