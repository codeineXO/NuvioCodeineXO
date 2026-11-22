package com.nuvio.app.features.player.seekpreview

internal object SeekrContentMapping {
    private val kitsuPattern = Regex("kitsu:(\\d+)(?::(\\d+))?", RegexOption.IGNORE_CASE)

    fun from(
        parentMetaId: String?,
        videoId: String?,
        contentType: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        explicitImdbId: String? = null,
        explicitTmdbId: Int? = null,
    ): SeekrContent? {
        val candidates = listOfNotNull(explicitImdbId, videoId, parentMetaId)
        val imdbPattern = Regex("tt\\d+")

        var imdbId: String? = explicitImdbId
        var tmdbId: Int? = explicitTmdbId

        for (raw in candidates) {
            val cleaned = raw.trim()
            if (imdbId == null) {
                val match = imdbPattern.find(cleaned)
                if (match != null) {
                    imdbId = match.value
                }
            }
            if (tmdbId == null) {
                val stripped = cleaned
                    .removePrefix("tmdb:")
                    .removePrefix("movie:")
                    .removePrefix("series:")
                    .substringBefore(':')
                    .substringBefore('/')
                    .trim()
                val parsed = stripped.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    tmdbId = parsed
                }
            }
        }

        if (imdbId == null && tmdbId == null) {
            // Documented ids failed to resolve; fall back to Kitsu (`kitsu:<id>[:<episode>]`).
            val kitsu = candidates.firstNotNullOfOrNull { kitsuPattern.matchEntire(it.trim()) } ?: return null
            val kitsuId = kitsu.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val kitsuEpisode = kitsu.groupValues[2].toIntOrNull()?.takeIf { it > 0 }
                ?: episodeNumber?.takeIf { it > 0 }
            return SeekrContent.Anime(kitsuId = kitsuId, episode = kitsuEpisode)
        }

        val isSeries = (seasonNumber != null && episodeNumber != null) ||
            contentType?.lowercase() in setOf("series", "tv", "show", "episode")

        return if (isSeries && seasonNumber != null && episodeNumber != null) {
            SeekrContent.Episode(
                showImdbId = imdbId,
                showTmdbId = tmdbId,
                season = seasonNumber,
                episode = episodeNumber,
            )
        } else {
            SeekrContent.Movie(
                imdbId = imdbId,
                tmdbId = tmdbId,
            )
        }
    }
}
