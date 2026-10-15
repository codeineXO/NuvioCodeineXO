package com.nuvio.app.features.player

enum class MediaContentType(val value: String) {
    ANIME("anime"),
    CARTOON("cartoon"),
    LIVE_ACTION("live_action"),
    UNKNOWN("unknown");

    val isAnime: Boolean get() = this == ANIME
    val isCartoon: Boolean get() = this == CARTOON
    val isAnimation: Boolean get() = this == ANIME || this == CARTOON
    val isLiveAction: Boolean get() = this == LIVE_ACTION

    companion object {
        fun fromValue(value: String?): MediaContentType =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}
