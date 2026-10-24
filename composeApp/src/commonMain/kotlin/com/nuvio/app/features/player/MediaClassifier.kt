package com.nuvio.app.features.player

import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.streams.StreamItem
import kotlinx.serialization.Serializable

/** Only evidence-backed classifications are written to playback records. */
@Serializable
data class MediaClassificationHint(val contentType: String, val reason: String)

internal enum class MediaClassificationReason(val contentEvidence: Boolean = true) {
    ANIME_TYPE, ANIME_ID, ANIME_GENRE, ANIMATION_ORIGIN, ANIMATION_GENRE,
    METADATA_LIVE_ACTION, RELEASE_TAG(false), TYPE_FALLBACK(false), UNKNOWN(false),
}

internal data class MediaClassification(
    val type: MediaContentType,
    val reason: MediaClassificationReason,
) {
    val isConfirmed: Boolean get() = reason != MediaClassificationReason.TYPE_FALLBACK &&
        reason != MediaClassificationReason.UNKNOWN && type != MediaContentType.UNKNOWN

    fun toHint(): MediaClassificationHint? = if (isConfirmed) {
        MediaClassificationHint(type.value, reason.name)
    } else null

    companion object {
        fun fromHint(hint: MediaClassificationHint?): MediaClassification? {
            val type = MediaContentType.fromValue(hint?.contentType)
            val reason = MediaClassificationReason.entries.firstOrNull { it.name == hint?.reason } ?: return null
            val validType = when (reason) {
                MediaClassificationReason.ANIME_TYPE, MediaClassificationReason.ANIME_ID,
                MediaClassificationReason.ANIME_GENRE, MediaClassificationReason.ANIMATION_ORIGIN,
                MediaClassificationReason.RELEASE_TAG -> type == MediaContentType.ANIME
                MediaClassificationReason.ANIMATION_GENRE -> type == MediaContentType.CARTOON
                MediaClassificationReason.METADATA_LIVE_ACTION -> type == MediaContentType.LIVE_ACTION
                else -> false
            }
            return if (validType) MediaClassification(type, reason) else null
        }
    }
}

internal val StreamItem.classificationFilename: String?
    get() = listOf(behaviorHints.filename, clientResolve?.filename, clientResolve?.stream?.raw?.filename)
        .firstOrNull { !it.isNullOrBlank() }

internal val StreamItem.classificationReleaseText: String
    get() = listOfNotNull(name, title, description, clientResolve?.filename, clientResolve?.torrentName,
        clientResolve?.stream?.raw?.filename, clientResolve?.stream?.raw?.torrentName)
        .filter(String::isNotBlank).distinct().joinToString("\n")

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
        fun tokens(value: String?) = value.orEmpty().trim().lowercase().split(Regex("[,;/|\\s]+"))
        return tokens(country).any { it in setOf("jp", "jpn", "japan") } ||
            tokens(language).any { it.substringBefore('-').substringBefore('_') in setOf("ja", "jpn", "japanese") }
    }

    fun classify(
        meta: MetaDetails?,
        fallbackItemType: String? = null,
        fallbackItemId: String? = null,
        torrentFilename: String? = null,
        streamTitle: String? = null,
    ): MediaContentType = detectPlayback(meta, fallbackItemType, fallbackItemId,
        filename = torrentFilename, streamTitle = streamTitle).type

    internal fun detectPlayback(
        meta: MetaDetails?,
        fallbackItemType: String? = null,
        fallbackItemId: String? = null,
        stream: StreamItem? = null,
        filename: String? = null,
        streamTitle: String? = null,
        streamSubtitle: String? = null,
        hint: MediaClassificationHint? = null,
    ): MediaClassification {
        val detected = detect(meta, fallbackItemType, fallbackItemId,
            listOfNotNull(filename, stream?.classificationFilename).joinToString("\n"),
            listOfNotNull(streamTitle, streamSubtitle, stream?.classificationReleaseText).joinToString("\n"))
        // Current metadata wins; stored evidence can fill a metadata/release-information gap.
        if (detected.isConfirmed && detected.reason.contentEvidence) return detected
        val saved = MediaClassification.fromHint(hint ?: stream?.classificationHint)
        return when {
            saved != null && saved.reason.contentEvidence -> saved
            detected.isConfirmed -> detected
            else -> saved ?: detected
        }
    }

    private fun detect(meta: MetaDetails?, itemType: String?, itemId: String?, filename: String?, text: String?): MediaClassification {
        fun result(type: MediaContentType, reason: MediaClassificationReason) = MediaClassification(type, reason)
        val types = listOfNotNull(meta?.type, itemType).map { it.trim().lowercase() }
        if ("anime" in types) return result(MediaContentType.ANIME, MediaClassificationReason.ANIME_TYPE)
        if (listOfNotNull(meta?.id, itemId).any { id -> ANIME_ID_PREFIXES.any { id.trim().startsWith(it, true) } }) {
            return result(MediaContentType.ANIME, MediaClassificationReason.ANIME_ID)
        }
        val genres = meta?.genres.orEmpty().map { it.trim().lowercase() }
        val hints = meta?.classificationHints
        if ("anime" in genres || hints?.isAnime == true) return result(MediaContentType.ANIME, MediaClassificationReason.ANIME_GENRE)
        val hasAnimeTag = isAnimeRelease(filename, text)
        val animation = hints?.isAnimation ?: ("animation" in genres).takeIf { it }
        if (animation == true) {
            if (isJapaneseOrigin(hints?.country ?: meta?.country, hints?.language ?: meta?.language)) {
                return result(MediaContentType.ANIME, MediaClassificationReason.ANIMATION_ORIGIN)
            }
            if (hasAnimeTag) return result(MediaContentType.ANIME, MediaClassificationReason.RELEASE_TAG)
            return result(MediaContentType.CARTOON, MediaClassificationReason.ANIMATION_GENRE)
        }
        if (animation == false) return result(MediaContentType.LIVE_ACTION, MediaClassificationReason.METADATA_LIVE_ACTION)
        if (hasAnimeTag) return result(MediaContentType.ANIME, MediaClassificationReason.RELEASE_TAG)
        return if (types.any { it in setOf("movie", "series", "tv") }) {
            result(MediaContentType.LIVE_ACTION, MediaClassificationReason.TYPE_FALLBACK)
        } else result(MediaContentType.UNKNOWN, MediaClassificationReason.UNKNOWN)
    }
}

internal data class ClassificationContentKey(val profileId: Int, val type: String, val id: String)

/** Retains title metadata, but never carries a release tag to a different source. */
internal class PlaybackContentClassifier {
    private var contentKey: ClassificationContentKey? = null
    private var sourceKey: String? = null
    private var contentEvidence: MediaClassification? = null
    private var sourceEvidence: MediaClassification? = null

    fun update(content: ClassificationContentKey, source: String, detected: MediaClassification): MediaClassification {
        if (contentKey != content) {
            contentKey = content
            contentEvidence = null
            sourceEvidence = null
            sourceKey = null
        }
        if (sourceKey != source) {
            sourceKey = source
            sourceEvidence = null
        }
        if (detected.isConfirmed) {
            if (detected.reason.contentEvidence) contentEvidence = detected else sourceEvidence = detected
            return detected
        }
        return contentEvidence ?: sourceEvidence ?: detected
    }
}
