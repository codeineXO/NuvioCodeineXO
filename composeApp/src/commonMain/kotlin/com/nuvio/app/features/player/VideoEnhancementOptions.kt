package com.nuvio.app.features.player

enum class AnimeUpscalerOption(
    val storageKey: String,
    val label: String,
) {
    OFF("off", "Off"),
    AUTO("auto", "Anime Only"),
    ALWAYS_ON("always_on", "All Media");

    val isEnabled: Boolean get() = this != OFF

    companion object {
        fun fromStorageKey(key: String?): AnimeUpscalerOption = when (key?.lowercase()) {
            "off", "false", "disabled" -> OFF
            "auto", "anime" -> AUTO
            "always_on", "true", "enabled", "on", "all" -> ALWAYS_ON
            else -> OFF
        }
    }
}

enum class SvpMotionOption(
    val storageKey: String,
    val label: String,
    val description: String,
) {
    OFF("off", "Off", "Disable motion interpolation"),
    AUTO("auto", "Anime Only", "60 FPS for Anime and Cartoons only"),
    GLOBAL("global", "All Media", "60 FPS for all contents");

    val isEnabled: Boolean get() = this != OFF

    companion object {
        fun fromStorageKey(key: String?): SvpMotionOption = when (key?.lowercase()) {
            "off", "disabled" -> OFF
            "auto", "anime" -> AUTO
            "global", "all", "cinema" -> GLOBAL
            else -> OFF
        }
    }
}
