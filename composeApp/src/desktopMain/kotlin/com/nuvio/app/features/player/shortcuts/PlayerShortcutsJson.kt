package com.nuvio.app.features.player.shortcuts

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_shortcut_cheat_sheet_title
import org.jetbrains.compose.resources.getString

/** Localized titles for the in-player cheat sheet, resolved once per player. */
internal class PlayerShortcutLabels(
    val title: String,
    val groups: Map<PlayerShortcutGroup, String>,
    val actions: Map<PlayerShortcutAction, String>,
) {
    companion object {
        suspend fun load(): PlayerShortcutLabels = PlayerShortcutLabels(
            title = getString(Res.string.player_shortcut_cheat_sheet_title),
            groups = PlayerShortcutGroup.entries.associateWith { getString(it.titleRes) },
            actions = PlayerShortcutAction.entries.associateWith { getString(it.titleRes) },
        )
    }
}

/** The `shortcuts` field of the controls state the player script reads its keymap from. */
internal fun playerShortcutsJson(
    keymap: PlayerKeymap,
    steps: PlayerShortcutSteps,
    labels: PlayerShortcutLabels?,
): String = buildJsonObject {
    put("bindings", buildJsonObject {
        PlayerShortcutAction.entries.forEach { action ->
            keymap.bindings(action).filterNotNull().forEach { put(it.serialize(), action.id) }
        }
    })
    put("steps", buildJsonObject {
        put("seekShortMs", steps.seekShortMs)
        put("seekLongMs", steps.seekLongMs)
        put("seekFineMs", steps.seekFineMs)
        put("volumePercent", steps.volumePercent)
        put("subtitleDelayMs", steps.subtitleDelayMs)
    })
    if (labels != null) {
        put("sheet", buildJsonObject {
            put("title", labels.title)
            put("groups", buildJsonArray {
                PlayerShortcutGroup.entries.forEach { group ->
                    add(buildJsonObject {
                        put("title", labels.groups[group].orEmpty())
                        put("items", buildJsonArray {
                            PlayerShortcutAction.entries.filter { it.group == group }.forEach { action ->
                                val keys = keymap.bindings(action).filterNotNull()
                                if (keys.isEmpty()) return@forEach
                                add(buildJsonObject {
                                    put("label", labels.actions[action].orEmpty())
                                    put("keys", JsonArray(keys.map { JsonPrimitive(it.displayLabel()) }))
                                })
                            }
                        })
                    })
                }
            })
        })
    }
}.toString()
