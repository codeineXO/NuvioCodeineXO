package com.nuvio.app.features.player.seekpreview

internal class SeekrPreviewTrack(
    val cues: List<SeekrCue>,
    val vttUrl: String? = null,
    private val sheetCache: BoundedSheetCache = BoundedSheetCache(),
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

    suspend fun clear() {
        sheetCache.clear()
    }
}
