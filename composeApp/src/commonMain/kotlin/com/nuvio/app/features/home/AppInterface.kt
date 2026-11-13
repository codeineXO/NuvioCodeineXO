package com.nuvio.app.features.home

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_interface_official
import nuvio.composeapp.generated.resources.app_interface_persistent_hero
import nuvio.composeapp.generated.resources.app_interface_scroll_posters
import org.jetbrains.compose.resources.StringResource

enum class AppInterface(val key: String, val label: StringResource) {
    Official("official", Res.string.app_interface_official),
    ScrollPosters("scroll_posters", Res.string.app_interface_scroll_posters),
    PersistentHero("persistent_hero", Res.string.app_interface_persistent_hero);

    fun next(): AppInterface = entries[(ordinal + 1) % entries.size]

    companion object {
        val Default: AppInterface = PersistentHero

        internal fun fromStored(key: String?, legacyScrollPosters: Boolean?): AppInterface =
            entries.firstOrNull { it.key == key } ?: when (legacyScrollPosters) {
                true -> ScrollPosters
                false -> Official
                null -> Default
            }
    }
}
