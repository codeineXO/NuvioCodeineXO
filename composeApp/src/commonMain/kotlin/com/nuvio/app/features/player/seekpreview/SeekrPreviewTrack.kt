package com.nuvio.app.features.player.seekpreview

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal class SeekrPreviewTrack(
    val cues: List<SeekrCue>,
    val vttUrl: String? = null,
    /** Identity of the looked-up title; a refresh for the same content reuses [sheetCache]. */
    val content: SeekrContent? = null,
    internal val sheetCache: BoundedSheetCache = BoundedSheetCache(),
) {
    val isEmpty: Boolean get() = cues.isEmpty()

    fun cueIndexAt(positionMs: Long): Int {
        if (cues.isEmpty()) return -1
        if (positionMs <= cues.first().startMs) return 0
        if (positionMs >= cues.last().endMs) return cues.lastIndex

        var low = 0
        var high = cues.lastIndex

        while (low <= high) {
            val mid = (low + high) ushr 1
            val cue = cues[mid]
            when {
                positionMs < cue.startMs -> high = mid - 1
                positionMs >= cue.endMs -> low = mid + 1
                else -> return mid
            }
        }
        return low.coerceIn(0, cues.lastIndex)
    }

    fun cueAt(positionMs: Long): SeekrCue? {
        val index = cueIndexAt(positionMs)
        return cues.getOrNull(index)
    }

    suspend fun thumbnailAtIndex(index: Int): SeekrThumbnail? {
        val cue = cues.getOrNull(index) ?: return null
        val sheetBitmap = sheetCache.get(cue.tile.sheetUrl) ?: return null
        return SeekrThumbnail(
            sheetBitmap = sheetBitmap,
            tile = cue.tile,
            cueStartMs = cue.startMs,
            cueEndMs = cue.endMs,
        )
    }

    suspend fun thumbnailAt(positionMs: Long): SeekrThumbnail? {
        val index = cueIndexAt(positionMs)
        return thumbnailAtIndex(index)
    }

    /** The sheet holding cue [index], then the nearest different sheets before and after it. */
    fun nearbySheetUrls(index: Int): List<String> {
        val center = cues.getOrNull(index)?.tile?.sheetUrl ?: return emptyList()
        val previous = (index - 1 downTo 0).firstNotNullOfOrNull { i ->
            cues[i].tile.sheetUrl.takeIf { it != center }
        }
        val next = (index + 1..cues.lastIndex).firstNotNullOfOrNull { i ->
            cues[i].tile.sheetUrl.takeIf { it != center }
        }
        return listOfNotNull(center, next, previous)
    }

    /** Warms the sheet cache around [index] so scrubbing across a sheet boundary doesn't wait on the network. */
    suspend fun preloadAround(index: Int) {
        coroutineScope {
            nearbySheetUrls(index).forEach { url -> launch { sheetCache.get(url) } }
        }
    }

    suspend fun clear() {
        sheetCache.clear()
    }
}
