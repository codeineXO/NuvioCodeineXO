package com.nuvio.app.features.details

import kotlinx.serialization.Serializable

/** Captured at the successful fetch; retained by metadata copies and the repository cache. */
@Serializable
data class MetadataProvenance(
    val sourceName: String? = null,
    val sourceId: String? = null,
    val requestedId: String? = null,
    val lookupId: String? = null,
    val fetchedAtMs: Long? = null,
    val usedFallback: Boolean = false,
    val enrichmentSources: Set<String> = emptySet(),
    val knownIds: Map<String, String> = emptyMap(),
)

/** Small enough to persist with progress, without storing full metadata or episode lists. */
@Serializable
data class MetadataSnapshot(
    val id: String,
    val provenance: MetadataProvenance,
)

internal fun MetaDetails.metadataSnapshotOrNull(): MetadataSnapshot? {
    if (provenance.sourceName.isNullOrBlank()) return null
    return MetadataSnapshot(id, provenance.copy(
        knownIds = provenance.knownIds + listOfNotNull(imdbId?.let { "IMDb" to it }).toMap(),
    ))
}

/** A numeric ID alone does not establish a namespace. */
internal fun metadataIdProvider(id: String): String? {
    val value = id.trim()
    if (Regex("^tt[0-9]+(?=[:/]|$)", RegexOption.IGNORE_CASE).containsMatchIn(value)) return "IMDb"
    val prefix = value.substringBefore(':').substringBefore('/')
    if (prefix == value || prefix.isBlank() || prefix.all(Char::isDigit)) return null
    if (prefix.lowercase() in setOf("movie", "series", "tv")) {
        return metadataIdProvider(value.substring(prefix.length + 1))
    }
    return when (prefix.lowercase()) {
        "imdb" -> "IMDb"
        "tmdb" -> "TMDB"
        "tvdb" -> "TVDB"
        "kitsu" -> "Kitsu"
        "mal", "myanimelist" -> "MyAnimeList"
        "anilist" -> "AniList"
        "anidb" -> "AniDB"
        else -> prefix
    }
}
