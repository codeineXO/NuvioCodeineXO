package com.nuvio.app.features.anime

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.player.skip.SimklIdResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object AnimeFillerRepository {
    private val log = Logger.withTag("AnimeFillerRepo")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // In-memory cache: cacheKey -> Set of filler episode numbers
    private val memoryCache = mutableMapOf<String, Set<Int>>()
    // AnimeFillerList shows directory cache: normalized title -> slug
    private var aflShowsCache: Map<String, String>? = null

    private const val AFL_BASE_URL = "https://www.animefillerlist.com"
    private const val JIKAN_BASE_URL = "https://api.jikan.moe/v4"
    private const val HTTP_TIMEOUT_MS = 5_000L

    private val USER_AGENT_HEADERS = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    )

    private val CONDENSED_FILLER_REGEX = Regex("""(?s)<div class="filler"[^>]*>.*?<span class="Episodes">(.*?)</span>""")
    private val EPISODE_RANGE_REGEX = Regex("""(\d+)(?:\s*-\s*(\d+))?""")
    private val ROW_FILLER_REGEX = Regex("""<tr class="[^"]*filler[^"]*"[^>]*>\s*<td class="Number">(\d+)</td>""")
    private val SHOW_LINK_REGEX = Regex("""<a href="/shows/([^"]+)">([^<]+)</a>""")

    /**
     * Enriches the provided [meta] with filler episode flags.
     * Operates across all catalog and metadata addons (IMDb, TMDB, TVDB, Kitsu, etc.).
     */
    suspend fun enrichFillerStatus(
        meta: MetaDetails,
        fallbackItemId: String? = null,
        fallbackItemType: String? = null,
    ): MetaDetails = withContext(Dispatchers.Default) {
        AnimeFillerSettingsRepository.ensureLoaded()
        if (!AnimeFillerSettingsRepository.snapshot()) {
            return@withContext meta
        }

        if (meta.videos.isEmpty()) {
            return@withContext meta
        }

        // Fast path: if already marked, return
        if (meta.videos.any { it.isFiller }) {
            return@withContext meta
        }

        val isKnownAnime = isAnimeContent(meta, fallbackItemType)

        // Parse (source, id) for Simkl resolution
        val (source, rawId) = parseIdSource(meta.id)
            ?: meta.imdbId?.takeIf { it.isNotBlank() }?.let { "imdb" to it }
            ?: parseIdSource(fallbackItemId)
            ?: ("" to "")

        // Resolve IDs and mapping via Simkl
        val contentTypeHint = if (isKnownAnime) "anime" else meta.type.takeIf { it.isNotBlank() } ?: fallbackItemType
        val resolved = if (source.isNotBlank() && rawId.isNotBlank()) {
            withTimeoutOrNull(HTTP_TIMEOUT_MS) {
                SimklIdResolver.resolveIds(source, rawId, contentTypeHint)
            }
        } else null

        val isAnime = isKnownAnime || resolved?.type == "anime"
        if (!isAnime) {
            return@withContext meta
        }

        val cacheKey = buildCacheKey(meta, resolved)
        val cachedFillers = getCachedFillerEpisodes(cacheKey)

        val fillerEpisodes: Set<Int> = if (cachedFillers != null) {
            cachedFillers
        } else {
            val resolvedFillers = fetchFillerEpisodes(meta, resolved)
            saveCachedFillerEpisodes(cacheKey, resolvedFillers)
            resolvedFillers
        }

        if (fillerEpisodes.isEmpty()) {
            return@withContext meta
        }

        // Retrieve TVDB-to-Anime episode mapping from Simkl
        val mapping = if (resolved != null && resolved.simklId > 0) {
            withTimeoutOrNull(HTTP_TIMEOUT_MS) {
                SimklIdResolver.getEpisodeMapping(resolved.simklId, resolved.type)
            } ?: emptyList()
        } else {
            emptyList()
        }

        // Tag filler episodes
        val updatedVideos = meta.videos.map { video ->
            val animeEpisodeNumber = if (mapping.isNotEmpty() && video.season != null && video.episode != null) {
                mapping.firstOrNull { it.tvdbSeason == video.season && it.tvdbEpisode == video.episode }?.animeEpisode
                    ?: video.episode
            } else {
                video.episode
            }

            val isFiller = animeEpisodeNumber != null && animeEpisodeNumber in fillerEpisodes
            if (video.isFiller != isFiller) video.copy(isFiller = isFiller) else video
        }

        log.d { "Tagged ${updatedVideos.count { it.isFiller }} filler episodes for ${meta.name} (id=${meta.id})" }
        meta.copy(videos = updatedVideos)
    }

    private suspend fun fetchFillerEpisodes(
        meta: MetaDetails,
        resolved: SimklIdResolver.ResolvedIds?,
    ): Set<Int> {
        // Source 1: AnimeFillerList
        val aflResult = fetchFromAnimeFillerList(meta, resolved)
        if (aflResult != null) {
            return aflResult
        }

        // Source 2: Jikan MyAnimeList API fallback
        val malId = resolved?.mal
        if (!malId.isNullOrBlank()) {
            val jikanResult = fetchFromJikan(malId)
            if (jikanResult != null) {
                return jikanResult
            }
        }

        return emptySet()
    }

    private suspend fun fetchFromAnimeFillerList(
        meta: MetaDetails,
        resolved: SimklIdResolver.ResolvedIds?,
    ): Set<Int>? {
        val candidates = buildCandidateSlugs(meta, resolved)
        for (slug in candidates) {
            val fillers = tryFetchAflSlug(slug)
            if (fillers != null) {
                return fillers
            }
        }

        // Try show index lookup if slug candidates failed
        val matchedSlug = findSlugInShowDirectory(meta.name)
        if (matchedSlug != null && matchedSlug !in candidates) {
            val fillers = tryFetchAflSlug(matchedSlug)
            if (fillers != null) {
                return fillers
            }
        }

        return null
    }

    private suspend fun tryFetchAflSlug(slug: String): Set<Int>? = withTimeoutOrNull(HTTP_TIMEOUT_MS) {
        runCatching {
            val url = "$AFL_BASE_URL/shows/$slug"
            val html = httpGetTextWithHeaders(url, USER_AGENT_HEADERS)
            parseAflHtml(html)
        }.onFailure {
            log.d { "AnimeFillerList fetch failed for slug '$slug': ${it.message}" }
        }.getOrNull()
    }

    internal fun parseAflHtml(html: String): Set<Int> {
        val fillers = mutableSetOf<Int>()

        // 1. Parse condensed list: <div class="filler">...<span class="Episodes">26, 101-106...</span></div>
        val condensedMatch = CONDENSED_FILLER_REGEX.find(html)
        if (condensedMatch != null) {
            val episodesHtml = condensedMatch.groupValues[1]
            EPISODE_RANGE_REGEX.findAll(episodesHtml).forEach { match ->
                val start = match.groupValues[1].toIntOrNull() ?: return@forEach
                val end = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: start
                for (ep in start..end) {
                    fillers.add(ep)
                }
            }
        }

        // 2. Parse episode rows: <tr class="filler...">...<td class="Number">X</td>
        ROW_FILLER_REGEX.findAll(html).forEach { match ->
            match.groupValues[1].toIntOrNull()?.let { fillers.add(it) }
        }

        return fillers
    }

    private suspend fun fetchFromJikan(malId: String): Set<Int>? = withTimeoutOrNull(HTTP_TIMEOUT_MS) {
        runCatching {
            val url = "$JIKAN_BASE_URL/anime/$malId/episodes"
            val jsonText = httpGetTextWithHeaders(url, USER_AGENT_HEADERS)
            val root = json.parseToJsonElement(jsonText).jsonObject
            val data = root["data"]?.jsonArray ?: return@runCatching emptySet()

            val fillers = mutableSetOf<Int>()
            for (elem in data) {
                val obj = elem.jsonObject
                val epNum = obj["mal_id"]?.jsonPrimitive?.int ?: continue
                val isFiller = obj["filler"]?.jsonPrimitive?.booleanOrNull == true
                if (isFiller) {
                    fillers.add(epNum)
                }
            }
            fillers
        }.onFailure {
            log.d { "Jikan episode fetch failed for MAL ID $malId: ${it.message}" }
        }.getOrNull()
    }

    private suspend fun findSlugInShowDirectory(title: String): String? {
        val directory = aflShowsCache ?: withTimeoutOrNull(HTTP_TIMEOUT_MS) {
            runCatching {
                val html = httpGetTextWithHeaders("$AFL_BASE_URL/shows", USER_AGENT_HEADERS)
                SHOW_LINK_REGEX.findAll(html).map { match ->
                    val slug = match.groupValues[1]
                    val rawTitle = match.groupValues[2]
                    val cleanTitle = cleanShowTitle(rawTitle)
                    cleanTitle to slug
                }.toMap().also { aflShowsCache = it }
            }.getOrNull()
        } ?: return null

        val cleanTarget = cleanShowTitle(title)
        return directory[cleanTarget] ?: directory.entries.firstOrNull { (k, _) ->
            k.contains(cleanTarget) || cleanTarget.contains(k)
        }?.value
    }

    private fun cleanShowTitle(title: String): String =
        title.replace(Regex("""\s*\([^)]*\)"""), "")
            .replace(":", "")
            .replace("-", " ")
            .trim()
            .lowercase()

    internal fun buildCandidateSlugs(
        meta: MetaDetails,
        resolved: SimklIdResolver.ResolvedIds?,
    ): List<String> = buildList {
        resolved?.tvdbslug?.takeIf { it.isNotBlank() }?.let { add(it.lowercase()) }
        resolved?.slug?.takeIf { it.isNotBlank() }?.let { add(it.lowercase()) }

        val nameSlug = slugify(meta.name)
        if (nameSlug.isNotBlank()) add(nameSlug)

        if (meta.name.contains(":")) {
            val beforeColon = slugify(meta.name.substringBefore(':'))
            if (beforeColon.isNotBlank()) add(beforeColon)
            val withoutColon = slugify(meta.name.replace(":", ""))
            if (withoutColon.isNotBlank()) add(withoutColon)
        }
    }.distinct()

    internal fun slugify(name: String): String =
        name.lowercase()
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private fun buildCacheKey(meta: MetaDetails, resolved: SimklIdResolver.ResolvedIds?): String = when {
        resolved != null && resolved.simklId > 0 -> "simkl:${resolved.simklId}"
        !resolved?.mal.isNullOrBlank() -> "mal:${resolved?.mal}"
        meta.id.isNotBlank() -> "id:${meta.id}"
        else -> "title:${slugify(meta.name)}"
    }

    private fun getCachedFillerEpisodes(key: String): Set<Int>? {
        memoryCache[key]?.let { return it }

        val diskPayload = AnimeFillerStorage.getCachedFillers(key) ?: return null
        if (diskPayload == "none" || diskPayload.isBlank()) {
            val empty = emptySet<Int>()
            memoryCache[key] = empty
            return empty
        }

        val parsed = diskPayload.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()

        memoryCache[key] = parsed
        return parsed
    }

    private fun saveCachedFillerEpisodes(key: String, fillers: Set<Int>) {
        memoryCache[key] = fillers
        val payload = if (fillers.isEmpty()) "none" else fillers.sorted().joinToString(",")
        AnimeFillerStorage.saveCachedFillers(key, payload)
    }

    private fun isAnimeContent(meta: MetaDetails, fallbackItemType: String?): Boolean {
        if (meta.type.equals("anime", ignoreCase = true)) return true
        if (fallbackItemType?.equals("anime", ignoreCase = true) == true) return true
        if (meta.id.startsWith("kitsu:", ignoreCase = true) ||
            meta.id.startsWith("mal:", ignoreCase = true) ||
            meta.id.startsWith("anidb:", ignoreCase = true)
        ) return true

        if (meta.genres.any { it.equals("Anime", ignoreCase = true) || it.equals("Animation", ignoreCase = true) }) {
            val isJapanese = meta.country?.equals("JP", ignoreCase = true) == true ||
                meta.country?.contains("Japan", ignoreCase = true) == true ||
                meta.language?.equals("ja", ignoreCase = true) == true ||
                meta.language?.contains("Japanese", ignoreCase = true) == true
            if (isJapanese || meta.genres.any { it.equals("Anime", ignoreCase = true) }) {
                return true
            }
        }

        return false
    }

    private fun parseIdSource(id: String?): Pair<String, String>? {
        val raw = id?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (raw.startsWith("tt", ignoreCase = true)) return "imdb" to raw.substringBefore(':')
        val colon = raw.indexOf(':')
        if (colon <= 0) return null
        val prefix = raw.substring(0, colon).lowercase()
        val value = raw.substring(colon + 1).substringBefore(':').takeIf { it.isNotBlank() } ?: return null
        return when (prefix) {
            "imdb", "tmdb", "tvdb", "mal", "kitsu", "anidb", "anilist", "simkl" -> prefix to value
            else -> null
        }
    }
}
