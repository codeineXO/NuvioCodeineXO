package com.nuvio.app.features.plugins

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class PluginIdResolution(
    val inputId: String,
    val passedId: String? = null,
    val resolved: Boolean = false,
    val scraperName: String = "",
)

/** Observes real plugin calls only; opening diagnostics never performs a network lookup. */
internal object PluginIdDiagnostics {
    private val state = MutableStateFlow<Map<String, PluginIdResolution>>(emptyMap())
    val resolutions = state.asStateFlow()

    fun key(id: String, type: String, season: Int?, episode: Int?) =
        "${normalizePluginType(type)}|$id|$season|$episode"

    fun record(key: String, scraperId: String, resolution: PluginIdResolution) {
        val entryKey = "$key|$scraperId"
        state.update { previous ->
            (previous - entryKey).entries.toList().takeLast(63).associate { it.toPair() } + (entryKey to resolution)
        }
    }

    fun clearRequest(key: String) {
        state.update { previous -> previous.filterKeys { !it.startsWith("$key|") } }
    }
}
