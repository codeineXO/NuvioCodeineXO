package com.nuvio.app.features.streams

import com.nuvio.app.features.debrid.DebridStreamQuality
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamFilterEngineTest {
    private fun stream(name: String, description: String? = null, url: String = "https://example.com/$name.mkv") =
        StreamItem(name = name, description = description, url = url, addonName = "Addon", addonId = "addon")

    private val uhd = stream("4K HDR", "Movie.2160p.HDR.WEB-DL.x265 💾 20 GB 👤 50 12 Mbps")
    private val fhd = stream("1080p", "Movie.1080p.BluRay.x264 💾 8 GB 👤 300 6 Mbps")
    private val cam = stream("CAM", "Movie.720p.CAM 💾 1 GB")
    private val unknown = stream("Mystery", "Movie")

    private fun group(vararg streams: StreamItem) =
        listOf(AddonStreamGroup(addonName = "Addon", addonId = "addon", streams = streams.toList()))

    private fun names(result: StreamFilterResult) = result.groups.flatMap { it.streams }.map { it.name }

    @Test
    fun defaultSettingsLeaveResultsUnchanged() {
        val result = StreamFilterEngine.apply(group(uhd, fhd, cam), StreamFilterSettings())
        assertEquals(listOf("4K HDR", "1080p", "CAM"), names(result))
        assertEquals(0, result.hiddenCount)
    }

    @Test
    fun resolutionFilterHidesOtherResolutionsAndUnknownsUnlessSelected() {
        val only1080 = StreamFilterSettings(resolutions = setOf(StreamFilterResolution.FHD_1080))
        val result = StreamFilterEngine.apply(group(uhd, fhd, unknown), only1080)
        assertEquals(listOf("1080p"), names(result))
        assertEquals(2, result.hiddenCount)

        val withUnknown = only1080.copy(resolutions = only1080.resolutions + StreamFilterResolution.UNKNOWN)
        assertEquals(listOf("1080p", "Mystery"), names(StreamFilterEngine.apply(group(uhd, fhd, unknown), withUnknown)))
    }

    @Test
    fun hideLowQualityAndQualityFiltersCombine() {
        val settings = StreamFilterSettings(hideLowQuality = true)
        assertEquals(listOf("4K HDR", "1080p"), names(StreamFilterEngine.apply(group(uhd, fhd, cam), settings)))

        val bluRay = StreamFilterSettings(qualities = setOf(DebridStreamQuality.BLURAY))
        assertEquals(listOf("1080p"), names(StreamFilterEngine.apply(group(uhd, fhd, cam), bluRay)))
    }

    @Test
    fun hdrAndSizeFilters() {
        val hdr = StreamFilterSettings(hdr = setOf(StreamFilterHdr.HDR))
        assertEquals(listOf("4K HDR"), names(StreamFilterEngine.apply(group(uhd, fhd), hdr)))

        val small = StreamFilterSettings(maxSizeGb = 2.0)
        assertEquals(listOf("CAM", "Mystery"), names(StreamFilterEngine.apply(group(uhd, fhd, cam, unknown), small)))
        val range = StreamFilterSettings(minSizeGb = 5.0, maxSizeGb = 10.0, hideUnknownSize = true)
        assertEquals(listOf("1080p"), names(StreamFilterEngine.apply(group(uhd, fhd, cam, unknown), range)))
        assertEquals(2, range.activeFilterCount)
    }

    @Test
    fun searchMatchesEveryTerm() {
        val settings = StreamFilterSettings(query = "bluray x264")
        assertEquals(listOf("1080p"), names(StreamFilterEngine.apply(group(uhd, fhd, cam), settings)))
    }

    @Test
    fun sortsPutMissingValuesLast() {
        val streams = group(unknown, fhd, cam, uhd)
        assertEquals(
            listOf("4K HDR", "1080p", "CAM", "Mystery"),
            names(StreamFilterEngine.apply(streams, StreamFilterSettings(sort = StreamSortMode.SIZE_DESC))),
        )
        assertEquals(
            listOf("CAM", "1080p", "4K HDR", "Mystery"),
            names(StreamFilterEngine.apply(streams, StreamFilterSettings(sort = StreamSortMode.SIZE_ASC))),
        )
        assertEquals(
            listOf("1080p", "4K HDR", "Mystery", "CAM"),
            names(StreamFilterEngine.apply(streams, StreamFilterSettings(sort = StreamSortMode.SEEDERS_DESC))),
        )
        assertEquals(
            listOf("4K HDR", "1080p", "Mystery", "CAM"),
            names(StreamFilterEngine.apply(streams, StreamFilterSettings(sort = StreamSortMode.BITRATE_DESC))),
        )
        assertEquals(
            "4K HDR",
            names(StreamFilterEngine.apply(streams, StreamFilterSettings(sort = StreamSortMode.BEST_QUALITY))).first(),
        )
    }

    @Test
    fun cachedFirstKeepsOrderWithinEachPart() {
        val torrent = StreamItem(name = "Torrent", infoHash = "a".repeat(40), addonName = "Addon", addonId = "addon")
        val result = StreamFilterEngine.apply(group(torrent, fhd), StreamFilterSettings(cachedFirst = true))
        assertEquals(listOf("1080p", "Torrent"), names(result))
    }

    @Test
    fun mergedSortOrdersAcrossAddons() {
        val groups = listOf(
            AddonStreamGroup(addonName = "A", addonId = "a", streams = listOf(cam, fhd)),
            AddonStreamGroup(addonName = "B", addonId = "b", streams = listOf(uhd), isLoading = true),
        )
        val sorted = StreamFilterSettings(sort = StreamSortMode.SIZE_DESC)
        val merged = StreamFilterEngine.apply(groups, sorted, mergeGroups = true)
        assertEquals(listOf(StreamFilterEngine.MERGED_GROUP_ID), merged.groups.map { it.addonId })
        assertEquals(listOf("4K HDR", "1080p", "CAM"), names(merged))
        assertTrue(merged.groups.single().isLoading)

        val state = StreamsUiState(groups = groups, streamFilter = sorted)
        assertTrue(state.isMergedSort)
        assertFalse(state.copy(selectedFilter = "a").isMergedSort)
        assertFalse(state.copy(streamFilter = StreamFilterSettings()).isMergedSort)
        assertEquals(listOf("CAM", "1080p"), state.copy(selectedFilter = "a", streamFilter = StreamFilterSettings()).allStreams.map { it.name })
    }

    @Test
    fun groupsEmptiedByFiltersAreDropped() {
        val groups = listOf(
            AddonStreamGroup(addonName = "A", addonId = "a", streams = listOf(cam)),
            AddonStreamGroup(addonName = "B", addonId = "b", streams = listOf(fhd)),
        )
        val result = StreamFilterEngine.apply(groups, StreamFilterSettings(hideLowQuality = true))
        assertEquals(listOf("b"), result.groups.map { it.addonId })
    }

    @Test
    fun resetClearsFiltersAndSortButKeepsAutoplaySetting() {
        val settings = StreamFilterSettings(
            resolutions = setOf(StreamFilterResolution.SD),
            cachedOnly = true,
            sort = StreamSortMode.SIZE_DESC,
            cachedFirst = true,
            autoPlayRespectsFilters = true,
            query = "x",
        )
        assertTrue(settings.changesResults)
        val reset = settings.reset()
        assertFalse(reset.changesResults)
        assertTrue(reset.autoPlayRespectsFilters)
        assertEquals("", reset.query)
    }

    @Test
    fun forAutoPlayFiltersAndSortsFlatList() {
        val settings = StreamFilterSettings(hideLowQuality = true, sort = StreamSortMode.SIZE_ASC)
        assertEquals(listOf("1080p", "4K HDR"), StreamFilterEngine.forAutoPlay(listOf(uhd, cam, fhd), settings).map { it.name })
    }

    @Test
    fun serializationKeepsSettingsButNotQuery() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val settings = StreamFilterSettings(
            resolutions = setOf(StreamFilterResolution.UHD_4K, StreamFilterResolution.UNKNOWN),
            sort = StreamSortMode.BEST_QUALITY,
            query = "remux",
        )
        val decoded = json.decodeFromString<StreamFilterSettings>(json.encodeToString(settings))
        assertEquals(settings.copy(query = ""), decoded)
    }

    @Test
    fun formatFilterDetectsHlsDashAndContainers() {
        val hls = stream("Live", url = "https://cdn.example.com/master.m3u8?token=1")
        val dash = stream("Dash", url = "https://cdn.example.com/manifest.mpd")
        val mp4 = stream("Mp4", url = "https://cdn.example.com/file.mp4")
        val all = group(hls, dash, mp4, fhd)
        assertEquals(listOf("Live"), names(StreamFilterEngine.apply(all, StreamFilterSettings(formats = setOf(StreamFilterFormat.HLS)))))
        assertEquals(listOf("Dash"), names(StreamFilterEngine.apply(all, StreamFilterSettings(formats = setOf(StreamFilterFormat.DASH)))))
        assertEquals(listOf("Mp4"), names(StreamFilterEngine.apply(all, StreamFilterSettings(formats = setOf(StreamFilterFormat.MP4)))))
        assertEquals(listOf("1080p"), names(StreamFilterEngine.apply(all, StreamFilterSettings(formats = setOf(StreamFilterFormat.MKV)))))
    }

    @Test
    fun providerFilterUsesSourceNameThenAddonName() {
        val fromPlugin = stream("Plugin").copy(sourceName = "ProviderX")
        val result = StreamFilterEngine.apply(group(fromPlugin, fhd), StreamFilterSettings(providers = setOf("ProviderX")))
        assertEquals(listOf("Plugin"), names(result))
        val byAddon = StreamFilterEngine.apply(group(fromPlugin, fhd), StreamFilterSettings(providers = setOf("Addon")))
        assertEquals(listOf("1080p"), names(byAddon))
    }

    @Test
    fun includeAndExcludeKeywords() {
        val include = StreamFilterSettings(includeKeywords = "BluRay, hdr")
        assertEquals(listOf("4K HDR", "1080p"), names(StreamFilterEngine.apply(group(uhd, fhd, cam), include)))
        val exclude = StreamFilterSettings(excludeKeywords = "x265 , cam")
        assertEquals(listOf("1080p"), names(StreamFilterEngine.apply(group(uhd, fhd, cam), exclude)))
        assertEquals(1, exclude.activeFilterCount)
    }

    @Test
    fun parsesBitrateAndSeeders() {
        assertEquals(12_000L, StreamFilterEngine.parseBitrateKbps("HEVC 12 Mbps"))
        assertEquals(800L, StreamFilterEngine.parseBitrateKbps("800 kbps"))
        assertEquals(null, StreamFilterEngine.parseBitrateKbps("no rate"))
        assertEquals(300, StreamFilterEngine.parseSeeders("👤 300 💾 8 GB"))
        assertEquals(42, StreamFilterEngine.parseSeeders("Seeders: 42"))
        assertEquals(null, StreamFilterEngine.parseSeeders("nothing"))
    }
}
