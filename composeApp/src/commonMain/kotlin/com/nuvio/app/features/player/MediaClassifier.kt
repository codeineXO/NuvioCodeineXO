package com.nuvio.app.features.player

import com.nuvio.app.features.details.MetaDetails

object MediaClassifier {
    private val ANIME_ID_PREFIXES = listOf("kitsu:", "mal:", "anidb:", "anilist:")

    private val ANIME_TAG_REGEX = Regex(
        """(?i)\[\s*(SubsPlease|Erai[-_ ]raws|Judas|HorribleSubs|ASW|SSA|Commie|EMBER|Golumpa|Tsundere|PuyaSubs!?|DB|LostYears|ToonsHub|NanDesuKa|DKB|Yameii|VipAnime|Anime Time)\s*\]"""
    )

    private val ANIME_TAG_SUBSTRINGS = listOf(
        "[SubsPlease]", "[Erai-raws]", "[Judas]", "[HorribleSubs]",
        "[ASW]", "[SSA]", "[Commie]", "[NanDesuKa]", "[DKB]",
        "[EMBER]", "[Golumpa]", "[Tsundere]", "[Yameii]", "[LostYears]",
        "[VipAnime]", "[ToonsHub]", "[Anime Time]", "[PuyaSubs!]", "[DB]",
        "[YND]",
    )

    fun isAnimeRelease(filename: String?, streamTitle: String?): Boolean {
        val f = filename.orEmpty()
        val t = streamTitle.orEmpty()
        if (f.isBlank() && t.isBlank()) return false
        if (ANIME_TAG_REGEX.containsMatchIn(f) || ANIME_TAG_REGEX.containsMatchIn(t)) return true
        return ANIME_TAG_SUBSTRINGS.any { tag ->
            f.contains(tag, ignoreCase = true) || t.contains(tag, ignoreCase = true)
        }
    }

    fun isJapaneseOrigin(country: String?, language: String?): Boolean {
        return country?.equals("JP", ignoreCase = true) == true ||
            country?.contains("Japan", ignoreCase = true) == true ||
            language?.equals("ja", ignoreCase = true) == true ||
            language?.contains("Japanese", ignoreCase = true) == true
    }

    fun classify(
        meta: MetaDetails?,
        fallbackItemType: String? = null,
        fallbackItemId: String? = null,
        torrentFilename: String? = null,
        streamTitle: String? = null,
    ): MediaContentType {
        val hasAnimeTag = isAnimeRelease(torrentFilename, streamTitle)

        if (meta != null) {
            val id = meta.id.trim()
            val type = meta.type.trim()
            val genres = meta.genres

            if (type.equals("anime", ignoreCase = true)) return MediaContentType.ANIME
            if (fallbackItemType?.equals("anime", ignoreCase = true) == true) return MediaContentType.ANIME
            if (ANIME_ID_PREFIXES.any { id.startsWith(it, ignoreCase = true) }) return MediaContentType.ANIME
            if (genres.any { it.equals("Anime", ignoreCase = true) }) return MediaContentType.ANIME

            val hasAnimation = genres.any { it.equals("Animation", ignoreCase = true) }
            if (hasAnimation) {
                val isJapanese = isJapaneseOrigin(meta.country, meta.language)
                return if (isJapanese || hasAnimeTag) {
                    MediaContentType.ANIME
                } else {
                    MediaContentType.CARTOON
                }
            }

            if (hasAnimeTag) return MediaContentType.ANIME

            val isStandardLiveAction = type.equals("movie", ignoreCase = true) ||
                type.equals("series", ignoreCase = true) ||
                type.equals("tv", ignoreCase = true)
            if (isStandardLiveAction) {
                return MediaContentType.LIVE_ACTION
            }
        }

        if (fallbackItemType?.equals("anime", ignoreCase = true) == true) return MediaContentType.ANIME
        if (fallbackItemId != null && ANIME_ID_PREFIXES.any { fallbackItemId.startsWith(it, ignoreCase = true) }) {
            return MediaContentType.ANIME
        }

        if (hasAnimeTag) return MediaContentType.ANIME

        if (fallbackItemType?.equals("movie", ignoreCase = true) == true ||
            fallbackItemType?.equals("series", ignoreCase = true) == true ||
            fallbackItemType?.equals("tv", ignoreCase = true) == true
        ) {
            return MediaContentType.LIVE_ACTION
        }

        return MediaContentType.UNKNOWN
    }
}
