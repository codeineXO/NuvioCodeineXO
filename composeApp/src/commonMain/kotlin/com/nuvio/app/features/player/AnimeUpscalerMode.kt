package com.nuvio.app.features.player

enum class AnimeUpscalerMode(
    val index: Int,
    val storageKey: String,
    val label: String,
    val description: String,
) {
    FAST(
        index = 0,
        storageKey = "fast",
        label = "Fast",
        description = "Low-end PCs & laptops",
    ),
    LINE_RECOVERY(
        index = 1,
        storageKey = "line_recovery",
        label = "Soft & Clear",
        description = "Smooth lines & balanced detail",
    ),
    SHARP_DETAIL(
        index = 2,
        storageKey = "sharp_detail",
        label = "Sharp & Detailed",
        description = "Maximum clarity & fine lines",
    );

    companion object {
        fun fromIndex(index: Int): AnimeUpscalerMode =
            entries.firstOrNull { it.index == index } ?: FAST

        fun fromStorageKey(key: String?): AnimeUpscalerMode =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: FAST
    }
}
