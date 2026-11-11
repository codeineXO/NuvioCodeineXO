package com.nuvio.app.features.home

enum class PosterGutterSize(val key: String, val multiplier: Float) {
    Narrow("narrow", 0.75f),
    Medium("medium", 1f),
    Wide("wide", 1.25f);

    companion object {
        fun fromKey(key: String): PosterGutterSize = when (key) {
            Medium.key -> Medium
            Wide.key -> Wide
            else -> Narrow
        }
    }
}
