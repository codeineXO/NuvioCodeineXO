package com.nuvio.app.features.streams

import androidx.compose.runtime.mutableStateMapOf

data class StreamLaunch(
    val profileId: Int,
    val type: String,
    val videoId: String,
    val parentMetaId: String? = null,
    val parentMetaType: String? = null,
    val title: String,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val pauseDescription: String? = null,
    val resumePositionMs: Long? = null,
    val resumeProgressFraction: Float? = null,
    val manualSelection: Boolean = false,
    val startFromBeginning: Boolean = false,
)

object StreamLaunchStore {
    private var nextLaunchId = 1L
    private val launches = mutableStateMapOf<Long, StreamLaunch>()

    fun put(launch: StreamLaunch): Long {
        val launchId = nextLaunchId++
        launches[launchId] = launch
        return launchId
    }

    fun get(launchId: Long): StreamLaunch? = launches[launchId]

    fun update(launchId: Long, transform: (StreamLaunch) -> StreamLaunch) {
        val current = launches[launchId] ?: return
        launches[launchId] = transform(current)
    }

    fun updateEpisode(
        streamLaunchId: Long?,
        parentMetaId: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        episodeTitle: String?,
        episodeThumbnail: String?,
        pauseDescription: String?,
        videoId: String?,
        resumePositionMs: Long? = null,
        resumeProgressFraction: Float? = null,
    ) {
        val targetId = streamLaunchId?.takeIf { launches.containsKey(it) }
            ?: parentMetaId?.let { pId ->
                launches.entries.lastOrNull { (_, launch) ->
                    launch.parentMetaId == pId || launch.videoId == pId
                }?.key
            }
            ?: return

        update(targetId) { current ->
            current.copy(
                seasonNumber = seasonNumber ?: current.seasonNumber,
                episodeNumber = episodeNumber ?: current.episodeNumber,
                episodeTitle = episodeTitle ?: current.episodeTitle,
                episodeThumbnail = episodeThumbnail ?: current.episodeThumbnail,
                pauseDescription = pauseDescription ?: current.pauseDescription,
                videoId = videoId ?: current.videoId,
                resumePositionMs = resumePositionMs ?: current.resumePositionMs,
                resumeProgressFraction = resumeProgressFraction ?: current.resumeProgressFraction,
                manualSelection = true,
                startFromBeginning = false,
            )
        }
    }

    fun remove(launchId: Long) {
        launches.remove(launchId)
    }

    fun clear() {
        nextLaunchId = 1L
        launches.clear()
    }
}
