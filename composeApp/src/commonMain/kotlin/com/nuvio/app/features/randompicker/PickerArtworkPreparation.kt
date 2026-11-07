package com.nuvio.app.features.randompicker

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.nuvio.app.features.tmdb.TmdbMetadataService
import kotlinx.coroutines.*

internal suspend fun preparePickerArtwork(
    result: PickerResult, requests: PickerRequests, context: PlatformContext, loader: ImageLoader,
): PickerResult = coroutineScope {
    try {
    var meta = result.meta
    // Never let an optional logo consume the entire artwork budget.
    val logoMeta = if (result.artwork != null) meta else withTimeoutOrNull(750) {
        requests.request("art-meta:${meta.type}:${meta.id}") { TmdbMetadataService.completeShuffleArtwork(meta, result.preview.id) }
    }
    meta = logoMeta ?: meta
    fun request(url: String?, width: Int, height: Int): ImageRequest? = url?.let {
        ImageRequest.Builder(context).data(it).size(width, height).build()
    }
    val poster = result.artwork?.poster ?: request(meta.poster, 500, 750)
    val background = result.artwork?.background ?: request(meta.background, 1280, 720)
    val logo = result.artwork?.logo ?: request(meta.logo, 500, 200)
    suspend fun load(role: String, request: ImageRequest?): ImageRequest? = request?.takeIf {
        requests.request("image:$role:${it.data}") { loader.execute(it) is SuccessResult } == true
    }
    val posterJob = async { load("poster", poster) }
    val backgroundJob = async { load("background", background) }
    val logoJob = async { load("logo", logo) }
    try {
        val prepared = PickerArtwork(posterJob.await(), backgroundJob.await(), logoJob.await())
        // A failed prefetch is not evidence that the URL is missing. Retain it for visible-image retries.
        result.copy(meta = meta, artwork = prepared)
    } finally {
        withContext(NonCancellable) {
            listOf(posterJob, backgroundJob, logoJob).forEach { it.cancelAndJoin() }
        }
    }
    } finally { withContext(NonCancellable) { requests.cancelUnusedArtwork() } }
}
