package com.nuvio.app.features.addons

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.network_empty_response_body
import nuvio.composeapp.generated.resources.network_request_failed_http
import org.jetbrains.compose.resources.getString

internal const val ADDON_STREAM_CACHE_TTL_MS = 15 * 60 * 1_000L

/** Stream searches need response headers; the text-only transport loses cache directives. */
internal suspend fun fetchAddonStreamResponse(url: String, forceRefresh: Boolean): RawHttpResponse {
    val response = httpRequestRaw(
        method = "GET",
        url = url,
        headers = buildMap {
            put("Accept", "application/json")
            if (forceRefresh) put("Cache-Control", "no-cache")
        },
        body = "",
        // Match the previous text transport: never truncate a stream list to the plugin limit.
        maxResponseBodyBytes = Int.MAX_VALUE,
    )
    if (response.status !in 200..299) error(getString(Res.string.network_request_failed_http, response.status))
    if (response.body.isBlank()) error(getString(Res.string.network_empty_response_body))
    return response
}

internal fun RawHttpResponse.streamCacheLifetimeMs(): Long {
    val directives = headers.entries
        .filter { it.key.equals("Cache-Control", ignoreCase = true) }
        .flatMap { it.value.lowercase().split(',') }
        .map(String::trim)
    if (directives.any { it.substringBefore('=').trim() in setOf("no-store", "no-cache") }) return 0
    val maxAge = directives.filter { it.substringBefore('=').trim() == "max-age" }
        .mapNotNull { it.substringAfter('=', "").trim().trim('"').toLongOrNull() }
        .minOrNull() ?: return ADDON_STREAM_CACHE_TTL_MS
    val age = headers.entries.firstOrNull { it.key.equals("Age", ignoreCase = true) }
        ?.value?.toLongOrNull()?.coerceAtLeast(0) ?: 0
    return (maxAge.coerceAtLeast(0) - age).coerceIn(0, ADDON_STREAM_CACHE_TTL_MS / 1_000) * 1_000
}

internal suspend fun fetchAddonResponseText(
    url: String,
    forceRefresh: Boolean = false,
): String =
    if (forceRefresh) {
        httpGetTextWithHeaders(
            url = url,
            headers = mapOf("Cache-Control" to "no-cache"),
        )
    } else {
        httpGetText(url)
    }
