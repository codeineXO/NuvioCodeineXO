package com.nuvio.app.features.remote

import com.nuvio.app.core.ui.ActionMenuAction
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.remote.RemoteCardMenus.Target
import com.nuvio.app.features.streams.StreamsRepository
import com.nuvio.app.features.watchprogress.continueWatchingItemKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The menu belongs to the phone connection that requested it. */
internal class RemoteCardMenuSession {
    private data class Menu(val id: Int, val request: JsonObject, val actions: List<ActionMenuAction>)
    private var menu: Menu? = null

    suspend fun open(request: JsonObject): String? {
        val id = request.number("menu") ?: return null
        val actions = target(request)?.let { RemoteCardMenus.actions(it) }.orEmpty()
        menu = Menu(id, request, actions)
        return buildJsonObject {
            put("k", "cardMenu")
            put("menu", id)
            putJsonArray("actions") {
                actions.forEach { action ->
                    addJsonObject {
                        put("label", action.label)
                        put("destructive", action.isDestructive)
                    }
                }
            }
        }.toString()
    }

    suspend fun select(message: JsonObject) {
        val pending = menu?.takeIf { it.id == message.number("menu") } ?: return
        menu = null
        val index = message.number("action") ?: return
        val selected = pending.actions.getOrNull(index) ?: return
        val target = target(pending.request) ?: return
        val current = RemoteCardMenus.actions(target).getOrNull(index) ?: return
        if (current.label != selected.label || current.isDestructive != selected.isDestructive) return
        withContext(Dispatchers.Main) {
            if (target(pending.request) != null) current.onSelected()
        }
    }

    private fun target(request: JsonObject): Target? {
        val route = RemoteAppBridge.route.value
        val index = request.number("item") ?: return null
        return when (request.string("kind")) {
            "poster" -> if (route == "tabs") {
                RemoteHome.posterAt(request)?.takeIf { it.id == request.string("id") && it.type == request.string("type") }
                    ?.let { Target.Poster(it) }
            } else null
            "continue" -> if (route == "tabs") {
                RemoteAppBridge.continueWatching.value.getOrNull(index)
                    ?.takeIf { continueWatchingItemKey(it) == request.string("key") }
                    ?.let { Target.ContinueWatching(it) }
            } else null
            "episode" -> if (route == "details") {
                val meta = MetaDetailsRepository.uiState.value.meta ?: return null
                val key = "${meta.type}:${meta.id}"
                if (key != request.string("key") || RemoteAppBridge.details.value?.key != key) return null
                meta.videos.getOrNull(index)?.takeIf { it.id == request.string("id") }?.let { Target.Episode(key, it) }
            } else null
            "source" -> if (route == "streams") {
                val key = RemoteAppBridge.streams.value?.key ?: return null
                if (key != request.string("key")) return null
                val group = request.number("group") ?: return null
                StreamsRepository.uiState.value.groups.getOrNull(group)?.streams?.getOrNull(index)
                    ?.takeIf { it.hashCode() == request.number("id") }
                    ?.let { Target.Source(key, it) }
            } else null
            else -> null
        }
    }

    private fun JsonObject.number(name: String): Int? = (get(name) as? JsonPrimitive)?.intOrNull
    private fun JsonObject.string(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull
}
