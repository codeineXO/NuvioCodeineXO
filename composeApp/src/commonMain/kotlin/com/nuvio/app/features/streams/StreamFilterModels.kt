package com.nuvio.app.features.streams

import com.nuvio.app.features.debrid.DebridStreamAudioChannel
import com.nuvio.app.features.debrid.DebridStreamAudioTag
import com.nuvio.app.features.debrid.DebridStreamEncode
import com.nuvio.app.features.debrid.DebridStreamFacts
import com.nuvio.app.features.debrid.DebridStreamLanguage
import com.nuvio.app.features.debrid.DebridStreamMetadata
import com.nuvio.app.features.debrid.DebridStreamPreferences
import com.nuvio.app.features.debrid.DebridStreamQuality
import com.nuvio.app.features.debrid.DebridStreamResolution
import com.nuvio.app.features.debrid.DebridStreamVisualTag
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class StreamFilterResolution { UHD_4K, QHD_1440, FHD_1080, HD_720, SD, UNKNOWN }

@Serializable
enum class StreamFilterSource { DIRECT, DEBRID, TORRENT, EXTERNAL }

@Serializable
enum class StreamFilterHdr { DOLBY_VISION, HDR10_PLUS, HDR, SDR, UNKNOWN }

@Serializable
enum class StreamFilterFormat { HLS, DASH, MKV, MP4, OTHER, YOUTUBE, UNKNOWN }

@Serializable
enum class StreamSortMode { DEFAULT, BEST_QUALITY, SIZE_DESC, SIZE_ASC, BITRATE_DESC, SEEDERS_DESC }

/**
 * Filter and sort for stream results. Saved on this device only (never synced); [query] is
 * kept for the session and never saved, so a search doesn't follow the user to other titles.
 */
@Serializable
data class StreamFilterSettings(
    val resolutions: Set<StreamFilterResolution> = emptySet(),
    val qualities: Set<DebridStreamQuality> = emptySet(),
    val hideLowQuality: Boolean = false,
    val cachedOnly: Boolean = false,
    val sources: Set<StreamFilterSource> = emptySet(),
    val hdr: Set<StreamFilterHdr> = emptySet(),
    val encodes: Set<DebridStreamEncode> = emptySet(),
    val audio: Set<DebridStreamAudioTag> = emptySet(),
    val channels: Set<DebridStreamAudioChannel> = emptySet(),
    /** Size limits in GB; 0 means no limit. */
    val minSizeGb: Int = 0,
    val maxSizeGb: Int = 0,
    val hideUnknownSize: Boolean = false,
    val languages: Set<DebridStreamLanguage> = emptySet(),
    /** Provider (source) names; see [StreamFilterFacts.provider]. */
    val providers: Set<String> = emptySet(),
    /** Release groups; an empty string stands for streams without one. */
    val releaseGroups: Set<String> = emptySet(),
    val formats: Set<StreamFilterFormat> = emptySet(),
    /** Comma-separated; a stream must contain at least one when set. */
    val includeKeywords: String = "",
    /** Comma-separated; a stream containing any of these is hidden. */
    val excludeKeywords: String = "",
    val sort: StreamSortMode = StreamSortMode.DEFAULT,
    val cachedFirst: Boolean = false,
    val autoPlayRespectsFilters: Boolean = false,
    @Transient val query: String = "",
) {
    val activeFilterCount: Int
        get() = listOf(
            resolutions.isNotEmpty(),
            qualities.isNotEmpty(),
            hideLowQuality,
            cachedOnly,
            sources.isNotEmpty(),
            hdr.isNotEmpty(),
            encodes.isNotEmpty(),
            audio.isNotEmpty(),
            channels.isNotEmpty(),
            minSizeGb > 0 || maxSizeGb > 0,
            hideUnknownSize,
            languages.isNotEmpty(),
            providers.isNotEmpty(),
            releaseGroups.isNotEmpty(),
            formats.isNotEmpty(),
            includeKeywordList.isNotEmpty(),
            excludeKeywordList.isNotEmpty(),
            query.isNotBlank(),
        ).count { it }

    val includeKeywordList: List<String>
        get() = includeKeywords.toKeywordList()

    val excludeKeywordList: List<String>
        get() = excludeKeywords.toKeywordList()

    val hasActiveFilters: Boolean
        get() = activeFilterCount > 0

    val hasActiveSort: Boolean
        get() = sort != StreamSortMode.DEFAULT || cachedFirst

    /** True when filtering or sorting changes what the results show. */
    val changesResults: Boolean
        get() = hasActiveFilters || hasActiveSort

    /** Clears every filter and the sort; the autoplay setting is a preference, not a filter. */
    fun reset(): StreamFilterSettings = StreamFilterSettings(autoPlayRespectsFilters = autoPlayRespectsFilters)
}

internal data class StreamFilterFacts(
    val resolution: StreamFilterResolution,
    val quality: DebridStreamQuality,
    val source: StreamFilterSource,
    val cached: Boolean,
    val hdr: Set<StreamFilterHdr>,
    val encode: DebridStreamEncode,
    val audio: Set<DebridStreamAudioTag>,
    val channels: Set<DebridStreamAudioChannel>,
    val sizeBytes: Long?,
    val languages: Set<DebridStreamLanguage>,
    val provider: String,
    val releaseGroup: String,
    val format: StreamFilterFormat,
    val bitrateKbps: Long?,
    val seeders: Int?,
    val searchText: String,
    val qualityFacts: DebridStreamFacts,
)

internal data class StreamFilterResult(
    val groups: List<AddonStreamGroup>,
    val hiddenCount: Int,
)

/** Values present in a set of results, with how many streams carry each. */
internal data class StreamFilterOptions(
    val resolutions: Map<StreamFilterResolution, Int> = emptyMap(),
    val qualities: Map<DebridStreamQuality, Int> = emptyMap(),
    val sources: Map<StreamFilterSource, Int> = emptyMap(),
    val cached: Int = 0,
    val lowQuality: Int = 0,
    val hdr: Map<StreamFilterHdr, Int> = emptyMap(),
    val encodes: Map<DebridStreamEncode, Int> = emptyMap(),
    val audio: Map<DebridStreamAudioTag, Int> = emptyMap(),
    val channels: Map<DebridStreamAudioChannel, Int> = emptyMap(),
    val unknownSize: Int = 0,
    val languages: Map<DebridStreamLanguage, Int> = emptyMap(),
    val providers: Map<String, Int> = emptyMap(),
    val releaseGroups: Map<String, Int> = emptyMap(),
    val formats: Map<StreamFilterFormat, Int> = emptyMap(),
)

internal object StreamFilterEngine {
    private val defaultPreferences = DebridStreamPreferences()
    private val lowQualities = setOf(
        DebridStreamQuality.CAM,
        DebridStreamQuality.TS,
        DebridStreamQuality.TC,
        DebridStreamQuality.SCR,
    )
    private const val maxCachedFacts = 4000
    private val cacheLock = SynchronizedObject()
    private val factsCache = HashMap<StreamItem, StreamFilterFacts>()

    private val bitrateRegex = Regex(
        """(\d+(?:[.,]\d+)?)\s*(gbps|gb/s|mbps|mb/s|kbps|kb/s)""",
        RegexOption.IGNORE_CASE,
    )
    private val seedersRegex = Regex(
        """(?:👤|\bseed(?:er)?s?\s*[:=]?)\s*(\d+)""",
        RegexOption.IGNORE_CASE,
    )

    fun facts(stream: StreamItem): StreamFilterFacts {
        synchronized(cacheLock) { factsCache[stream] }?.let { return it }
        val computed = computeFacts(stream)
        synchronized(cacheLock) {
            if (factsCache.size >= maxCachedFacts) factsCache.clear()
            factsCache[stream] = computed
        }
        return computed
    }

    fun apply(groups: List<AddonStreamGroup>, settings: StreamFilterSettings, mergeGroups: Boolean = false): StreamFilterResult {
        if (!settings.changesResults) return StreamFilterResult(groups, 0)
        if (mergeGroups) return applyMerged(groups, settings)
        var hidden = 0
        val result = groups.mapNotNull { group ->
            val kept = group.streams
                .map { it to facts(it) }
                .filter { (_, facts) -> matches(facts, settings) }
            hidden += group.streams.size - kept.size
            val sorted = sort(kept, settings).map { it.first }
            when {
                sorted.isNotEmpty() || group.isLoading || group.streams.isEmpty() -> group.copy(streams = sorted)
                else -> null
            }
        }
        return StreamFilterResult(result, hidden)
    }

    /** One list sorted across every addon; ties keep the addons' order. */
    private fun applyMerged(groups: List<AddonStreamGroup>, settings: StreamFilterSettings): StreamFilterResult {
        if (groups.isEmpty()) return StreamFilterResult(groups, 0)
        val all = groups.flatMap { group -> group.streams.map { it to facts(it) } }
        val kept = all.filter { (_, facts) -> matches(facts, settings) }
        val sorted = sort(kept, settings).map { it.first }
        val isLoading = groups.any { it.isLoading }
        val error = if (sorted.isEmpty()) groups.firstNotNullOfOrNull { it.error } else null
        val merged = AddonStreamGroup(
            addonName = "",
            addonId = MERGED_GROUP_ID,
            streams = sorted,
            isLoading = isLoading,
            error = error,
        )
        val result = if (sorted.isEmpty() && !isLoading && error == null) emptyList() else listOf(merged)
        return StreamFilterResult(result, all.size - kept.size)
    }

    const val MERGED_GROUP_ID = "sorted:all"

    /** Filters and sorts a flat list the way the results show it, for autoplay. */
    fun forAutoPlay(streams: List<StreamItem>, settings: StreamFilterSettings): List<StreamItem> {
        if (!settings.changesResults) return streams
        val group = AddonStreamGroup(addonName = "", addonId = "", streams = streams)
        return apply(listOf(group), settings).groups.flatMap { it.streams }
    }

    fun filterStreams(streams: List<StreamItem>, settings: StreamFilterSettings): List<StreamItem> {
        if (!settings.hasActiveFilters) return streams
        return streams.filter { matches(facts(it), settings) }
    }

    fun options(streams: List<StreamItem>): StreamFilterOptions {
        val all = streams.map(::facts)
        fun <T> countOf(values: List<T>): Map<T, Int> = values.groupingBy { it }.eachCount()
        return StreamFilterOptions(
            resolutions = countOf(all.map { it.resolution }),
            qualities = countOf(all.map { it.quality }),
            sources = countOf(all.map { it.source }),
            cached = all.count { it.cached },
            lowQuality = all.count { it.quality in lowQualities },
            hdr = countOf(all.flatMap { it.hdr }),
            encodes = countOf(all.map { it.encode }),
            audio = countOf(all.flatMap { it.audio }),
            channels = countOf(all.flatMap { it.channels }),
            unknownSize = all.count { it.sizeBytes == null },
            languages = countOf(all.flatMap { it.languages }),
            providers = countOf(all.map { it.provider }),
            releaseGroups = countOf(all.map { it.releaseGroup }),
            formats = countOf(all.map { it.format }),
        )
    }

    internal fun matches(facts: StreamFilterFacts, settings: StreamFilterSettings): Boolean {
        if (settings.cachedOnly && !facts.cached) return false
        if (settings.hideLowQuality && facts.quality in lowQualities) return false
        if (!settings.resolutions.acceptsValue(facts.resolution)) return false
        if (!settings.qualities.acceptsValue(facts.quality)) return false
        if (!settings.sources.acceptsValue(facts.source)) return false
        if (!settings.hdr.acceptsAny(facts.hdr)) return false
        if (!settings.encodes.acceptsValue(facts.encode)) return false
        if (!settings.audio.acceptsAny(facts.audio)) return false
        if (!settings.channels.acceptsAny(facts.channels)) return false
        if (!matchesSize(facts.sizeBytes, settings)) return false
        if (!settings.languages.acceptsAny(facts.languages)) return false
        if (!settings.providers.acceptsValue(facts.provider)) return false
        if (!settings.releaseGroups.acceptsValue(facts.releaseGroup)) return false
        if (!settings.formats.acceptsValue(facts.format)) return false
        val include = settings.includeKeywordList
        if (include.isNotEmpty() && include.none { it in facts.searchText }) return false
        if (settings.excludeKeywordList.any { it in facts.searchText }) return false
        val terms = settings.query.lowercase().split(' ').filter { it.isNotBlank() }
        if (terms.any { it !in facts.searchText }) return false
        return true
    }

    private fun <T> Set<T>.acceptsValue(value: T): Boolean = isEmpty() || value in this
    private fun <T> Set<T>.acceptsAny(values: Set<T>): Boolean = isEmpty() || values.any { it in this }

    private fun sort(
        streams: List<Pair<StreamItem, StreamFilterFacts>>,
        settings: StreamFilterSettings,
    ): List<Pair<StreamItem, StreamFilterFacts>> {
        if (!settings.hasActiveSort) return streams
        val modeComparator: Comparator<StreamFilterFacts>? = when (settings.sort) {
            StreamSortMode.DEFAULT -> null
            StreamSortMode.BEST_QUALITY -> compareBy<StreamFilterFacts> { it.qualityFacts.resolutionRank }
                .thenBy { it.qualityFacts.qualityRank }
                .thenBy { it.qualityFacts.visualRank }
                .then(missingLastDescending { it.sizeBytes })
            StreamSortMode.SIZE_DESC -> missingLastDescending { it.sizeBytes }
            StreamSortMode.SIZE_ASC -> missingLastAscending { it.sizeBytes }
            StreamSortMode.BITRATE_DESC -> missingLastDescending { it.bitrateKbps }
            StreamSortMode.SEEDERS_DESC -> missingLastDescending { it.seeders?.toLong() }
        }
        val cachedComparator: Comparator<StreamFilterFacts>? =
            if (settings.cachedFirst) compareBy { if (it.cached) 0 else 1 } else null
        val comparator = listOfNotNull(cachedComparator, modeComparator)
            .reduceOrNull { first, second -> first.then(second) } ?: return streams
        // sortedWith is stable, so streams that compare equal keep the addon's order.
        return streams.sortedWith { left, right -> comparator.compare(left.second, right.second) }
    }

    private fun missingLastDescending(value: (StreamFilterFacts) -> Long?): Comparator<StreamFilterFacts> =
        Comparator { left, right ->
            val l = value(left)
            val r = value(right)
            when {
                l == null && r == null -> 0
                l == null -> 1
                r == null -> -1
                else -> r.compareTo(l)
            }
        }

    private fun missingLastAscending(value: (StreamFilterFacts) -> Long?): Comparator<StreamFilterFacts> =
        Comparator { left, right ->
            val l = value(left)
            val r = value(right)
            when {
                l == null && r == null -> 0
                l == null -> 1
                r == null -> -1
                else -> l.compareTo(r)
            }
        }

    private fun computeFacts(stream: StreamItem): StreamFilterFacts {
        val base = DebridStreamMetadata.facts(stream, defaultPreferences)
        val texts = listOfNotNull(
            stream.name,
            stream.title,
            stream.description,
            stream.behaviorHints.filename,
            stream.clientResolve?.filename,
            stream.clientResolve?.torrentName,
        )
        val sizeBytes = (base.size ?: stream.resolvedVideoSize)?.takeIf { it > 0L }
        return StreamFilterFacts(
            resolution = base.resolution.toFilterResolution(),
            quality = base.quality,
            source = stream.filterSource(),
            cached = stream.isCachedOrInstant(),
            hdr = base.visualTags.toFilterHdr(),
            encode = base.encode,
            audio = base.audioTags.toSet(),
            channels = base.audioChannels.toSet(),
            sizeBytes = sizeBytes,
            languages = base.languages.toSet().ifEmpty { setOf(DebridStreamLanguage.UNKNOWN) },
            provider = stream.sourceName?.takeIf { it.isNotBlank() } ?: stream.addonName,
            releaseGroup = base.releaseGroup.trim(),
            format = stream.filterFormat(),
            bitrateKbps = texts.firstNotNullOfOrNull(::parseBitrateKbps),
            seeders = texts.firstNotNullOfOrNull(::parseSeeders),
            searchText = texts.joinToString(" ").lowercase(),
            qualityFacts = base,
        )
    }

    internal fun parseBitrateKbps(text: String): Long? {
        val match = bitrateRegex.find(text) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        val multiplier = when (match.groupValues[2].lowercase()) {
            "gbps", "gb/s" -> 1_000_000.0
            "mbps", "mb/s" -> 1_000.0
            else -> 1.0
        }
        return (value * multiplier).toLong()
    }

    internal fun parseSeeders(text: String): Int? =
        seedersRegex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun DebridStreamResolution.toFilterResolution(): StreamFilterResolution = when (this) {
        DebridStreamResolution.P2160 -> StreamFilterResolution.UHD_4K
        DebridStreamResolution.P1440 -> StreamFilterResolution.QHD_1440
        DebridStreamResolution.P1080 -> StreamFilterResolution.FHD_1080
        DebridStreamResolution.P720 -> StreamFilterResolution.HD_720
        DebridStreamResolution.P576,
        DebridStreamResolution.P480,
        DebridStreamResolution.P360 -> StreamFilterResolution.SD
        DebridStreamResolution.UNKNOWN -> StreamFilterResolution.UNKNOWN
    }

    private fun List<DebridStreamVisualTag>.toFilterHdr(): Set<StreamFilterHdr> {
        val result = mutableSetOf<StreamFilterHdr>()
        if (any { it == DebridStreamVisualTag.DV || it == DebridStreamVisualTag.HDR_DV || it == DebridStreamVisualTag.DV_ONLY }) {
            result += StreamFilterHdr.DOLBY_VISION
        }
        if (DebridStreamVisualTag.HDR10_PLUS in this) result += StreamFilterHdr.HDR10_PLUS
        if (any {
                it == DebridStreamVisualTag.HDR || it == DebridStreamVisualTag.HDR10 ||
                    it == DebridStreamVisualTag.HDR_ONLY || it == DebridStreamVisualTag.HDR_DV ||
                    it == DebridStreamVisualTag.HLG
            }
        ) {
            result += StreamFilterHdr.HDR
        }
        if (DebridStreamVisualTag.SDR in this) result += StreamFilterHdr.SDR
        return result.ifEmpty { setOf(StreamFilterHdr.UNKNOWN) }
    }

    private const val bytesPerGb = 1024L * 1024L * 1024L

    private fun matchesSize(sizeBytes: Long?, settings: StreamFilterSettings): Boolean {
        if (sizeBytes == null) return !settings.hideUnknownSize
        if (settings.minSizeGb > 0 && sizeBytes < settings.minSizeGb * bytesPerGb) return false
        if (settings.maxSizeGb > 0 && sizeBytes > settings.maxSizeGb * bytesPerGb) return false
        return true
    }

    private fun StreamItem.filterSource(): StreamFilterSource = when {
        isDirectDebridStream || debridResolved || isCachedDebridTorrentStream -> StreamFilterSource.DEBRID
        isTorrentStream -> StreamFilterSource.TORRENT
        shouldOpenExternally -> StreamFilterSource.EXTERNAL
        else -> StreamFilterSource.DIRECT
    }

    private fun StreamItem.filterFormat(): StreamFilterFormat {
        if (youTubeIdToResolve != null) return StreamFilterFormat.YOUTUBE
        val candidates = listOfNotNull(
            playableDirectUrl?.substringBefore('#'),
            behaviorHints.filename,
            clientResolve?.filename,
            clientResolve?.stream?.raw?.filename,
        ).map { it.lowercase() }
        if (candidates.isEmpty()) return StreamFilterFormat.UNKNOWN
        if (candidates.any { it.contains(".m3u8") || it.contains("/hls/") || it.contains("format=hls") }) {
            return StreamFilterFormat.HLS
        }
        if (candidates.any { it.contains(".mpd") }) return StreamFilterFormat.DASH
        val extensions = candidates.map { it.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "") }
        return when {
            "mkv" in extensions -> StreamFilterFormat.MKV
            "mp4" in extensions || "m4v" in extensions -> StreamFilterFormat.MP4
            extensions.any { it in otherVideoExtensions } -> StreamFilterFormat.OTHER
            else -> StreamFilterFormat.UNKNOWN
        }
    }

    private val otherVideoExtensions = setOf("avi", "wmv", "webm", "mov", "ts", "m2ts", "flv", "mpg", "mpeg")

    private fun StreamItem.isCachedOrInstant(): Boolean =
        debridCacheStatus?.state == StreamDebridCacheState.CACHED ||
            clientResolve?.isCached == true ||
            debridResolved ||
            (!isTorrentStream && !isDirectDebridStream && playableDirectUrl != null)
}

private fun String.toKeywordList(): List<String> =
    split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
