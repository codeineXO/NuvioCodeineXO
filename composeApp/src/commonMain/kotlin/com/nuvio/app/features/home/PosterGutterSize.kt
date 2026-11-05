package com.nuvio.app.features.home

enum class PosterGutterSize(val key: String, val multiplier: Float) {
    Default("default", 1f),
    Narrow("narrow", 0.75f),
    Wide("wide", 1.25f);

    companion object {
        fun fromKey(key: String): PosterGutterSize = entries.firstOrNull { it.key == key } ?: Default
    }
}
