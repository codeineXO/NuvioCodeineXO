package com.nuvio.app.features.mdblist

import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_AUDIENCE
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_IMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_LETTERBOXD
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_MAL
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_METACRITIC
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TOMATOES
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TRAKT
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val ratingJson = Json { ignoreUnknownKeys = true }

internal fun parseMdbListRatings(payload: String): List<MetaExternalRating> =
    ratingJson.decodeFromString<MediaRatings>(payload).toRatings()

internal fun parseMdbListRatingsBatch(payload: String): Map<String, List<MetaExternalRating>> =
    ratingJson.decodeFromString<List<MediaRatings>>(payload).mapNotNull { media ->
        val imdbId = media.imdbId ?: media.imdbid ?: media.ids?.get("imdb")?.contentOrNull
        imdbId?.let { it to media.toRatings() }
    }.toMap()

@Serializable
private data class MediaRatings(
    @SerialName("imdb_id") val imdbId: String? = null,
    val imdbid: String? = null,
    val type: String? = null,
    val ids: Map<String, JsonPrimitive>? = null,
    val ratings: List<SourceRating>? = null,
    val keywords: List<JsonElement>? = null,
) {
    fun toRatings(): List<MetaExternalRating> {
        val keywordNames = keywords.orEmpty().mapNotNull { keyword ->
            val name = when (keyword) {
                is JsonObject -> keyword["name"] as? JsonPrimitive
                is JsonPrimitive -> keyword
                else -> null
            }
            name?.contentOrNull?.substringAfterLast('.')
        }.toSet()
        val tomatoesUrl = ratings.orEmpty()
            .firstOrNull { it.source == PROVIDER_TOMATOES || it.source == "rottentomatoes" }
            ?.let { normalizeRatingUrl(PROVIDER_TOMATOES, it.url, type) }
        val tmdbId = ids?.get("tmdb")?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        val tmdbUrl = tmdbId?.let { id ->
            val segment = if (type == "show" || type == "series") "tv" else "movie"
            "https://www.themoviedb.org/$segment/$id"
        }
        val malId = ids?.get("mal")?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" && it != "0" }
        val malUrl = malId?.let { if (it.all { c -> c.isDigit() }) "https://myanimelist.net/anime/$it" else null }
        val resolvedImdbId = imdbId ?: imdbid ?: ids?.get("imdb")?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
        val traktId = ids?.get("trakt")?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" && it != "0" }
        val traktUrl = traktId?.let { id ->
            val segment = if (type == "show" || type == "series") "shows" else "movies"
            "https://trakt.tv/$segment/$id"
        } ?: resolvedImdbId?.let { "https://trakt.tv/search/imdb/$it" }
        val letterboxdUrl = resolvedImdbId?.let { "https://letterboxd.com/imdb/$it" }
        val imdbUrl = resolvedImdbId?.let { id ->
            val clean = id.substringBefore(":").trim()
            val fullId = if (clean.startsWith("tt", ignoreCase = true)) clean else "tt$clean"
            "https://www.imdb.com/title/$fullId/"
        }
        return ratings.orEmpty().mapNotNull { rating ->
            val source = when (rating.source) {
                "popcorn", "audience", "tomatoesaudience" -> PROVIDER_AUDIENCE
                "myanimelist", "mal" -> PROVIDER_MAL
                PROVIDER_IMDB, PROVIDER_TMDB, PROVIDER_TOMATOES, PROVIDER_METACRITIC,
                PROVIDER_TRAKT, PROVIDER_LETTERBOXD -> rating.source
                else -> return@mapNotNull null
            }
            val maximum = when (source) {
                PROVIDER_IMDB, PROVIDER_MAL -> 10.0
                PROVIDER_LETTERBOXD -> 5.0
                else -> 100.0
            }
            val rawValue = rating.value?.takeIf { it >= 0 } ?: return@mapNotNull null
            val value = if (source == PROVIDER_LETTERBOXD && rating.score != null) rating.score / 20.0 else rawValue
            if (value !in 0.0..maximum) return@mapNotNull null
            val normalizedUrl = normalizeRatingUrl(source, rating.url, type)
            val resolvedUrl = normalizedUrl
                ?: if (source == PROVIDER_AUDIENCE) tomatoesUrl
                else if (source == PROVIDER_TMDB) tmdbUrl
                else if (source == PROVIDER_MAL) malUrl
                else if (source == PROVIDER_TRAKT) traktUrl
                else if (source == PROVIDER_LETTERBOXD) letterboxdUrl
                else if (source == PROVIDER_IMDB) imdbUrl
                else null
            MetaExternalRating(
                source = source,
                value = value,
                isCertified = when (source) {
                    PROVIDER_TOMATOES -> "certified-fresh" in keywordNames
                    PROVIDER_AUDIENCE -> "certified-hot" in keywordNames
                    else -> false
                },
                url = resolvedUrl,
            )
        }.distinctBy { it.source }
    }
}

@Serializable
private data class SourceRating(
    val source: String,
    val value: Double? = null,
    val score: Double? = null,
    val url: JsonPrimitive? = null,
)

internal fun normalizeRatingUrl(source: String, urlPrimitive: JsonPrimitive?, mediaType: String?): String? {
    val raw = urlPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
    if (source == PROVIDER_MAL && raw.all { it.isDigit() }) {
        return "https://myanimelist.net/anime/$raw"
    }
    if (source == PROVIDER_IMDB && raw.all { it.isDigit() }) return null

    return when (source) {
        PROVIDER_TOMATOES, PROVIDER_AUDIENCE -> {
            val path = if (raw.startsWith("/")) raw else "/$raw"
            "https://www.rottentomatoes.com$path"
        }
        PROVIDER_LETTERBOXD -> {
            val path = if (raw.startsWith("/")) raw else "/$raw"
            "https://letterboxd.com$path"
        }
        PROVIDER_METACRITIC -> {
            val normalized = if (raw.startsWith("/")) raw else "/$raw"
            if (normalized.startsWith("/movie/") || normalized.startsWith("/tv/")) {
                "https://www.metacritic.com$normalized"
            } else {
                val segment = if (mediaType == "show" || mediaType == "series") "/tv" else "/movie"
                "https://www.metacritic.com$segment$normalized"
            }
        }
        PROVIDER_TRAKT -> {
            if (raw.all { it.isDigit() }) {
                val segment = if (mediaType == "show" || mediaType == "series") "shows" else "movies"
                "https://trakt.tv/$segment/$raw"
            } else {
                val path = if (raw.startsWith("/")) raw else "/$raw"
                "https://trakt.tv$path"
            }
        }
        PROVIDER_MAL -> {
            val path = if (raw.startsWith("/")) raw else "/$raw"
            if (path.startsWith("/anime/")) "https://myanimelist.net$path" else "https://myanimelist.net/anime$path"
        }
        else -> null
    }
}
