package com.nuvio.app.features.player

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_playback_player_ui_codeinexo
import nuvio.composeapp.generated.resources.settings_playback_player_ui_codeinexo_description
import nuvio.composeapp.generated.resources.settings_playback_player_ui_official
import nuvio.composeapp.generated.resources.settings_playback_player_ui_official_description
import org.jetbrains.compose.resources.StringResource

enum class PlayerUiMode(
    val storageKey: String,
    val labelRes: StringResource,
    val descriptionRes: StringResource,
) {
    OFFICIAL(
        storageKey = "official",
        labelRes = Res.string.settings_playback_player_ui_official,
        descriptionRes = Res.string.settings_playback_player_ui_official_description,
    ),
    CODEINE_XO(
        storageKey = "codeine_xo",
        labelRes = Res.string.settings_playback_player_ui_codeinexo,
        descriptionRes = Res.string.settings_playback_player_ui_codeinexo_description,
    );

    companion object {
        fun fromStorageKey(key: String?): PlayerUiMode =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: CODEINE_XO
    }
}
