package com.nuvio.app.features.player

enum class AnimeUpscalerOption(
    val storageKey: String,
    val label: String,
) {
    AUTO("auto", "Auto (Anime & Cartoons)"),
    ALWAYS_ON("always_on", "Always On"),
    OFF("off", "Off");

    val isEnabled: Boolean get() = this != OFF

    companion object {
        fun fromStorageKey(key: String?): AnimeUpscalerOption = when (key?.lowercase()) {
            "auto" -> AUTO
            "always_on", "true", "enabled", "on" -> ALWAYS_ON
            "off", "false", "disabled" -> OFF
            else -> OFF
        }
    }
}

enum class SvpMotionOption(
    val storageKey: String,
    val label: String,
) {
    AUTO("auto", "Auto (Anime Only)"),
    GLOBAL("global", "Global (All Media)"),
    OFF("off", "Off");

    val isEnabled: Boolean get() = this != OFF

    companion object {
        fun fromStorageKey(key: String?): SvpMotionOption = when (key?.lowercase()) {
            "auto" -> AUTO
            "global", "all" -> GLOBAL
            "off", "disabled" -> OFF
            else -> OFF
        }
    }
}
