package com.nuvio.app.features.streams

import com.nuvio.app.features.player.playerSourceIdentityKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class StreamAttemptMark { LastPlayed, Failed }

/** The streams opened for one episode: the last one played and the ones that failed. */
internal data class StreamAttempts(
    val lastPlayedKey: String? = null,
    val failedKeys: Set<String> = emptySet(),
) {
    fun markFor(stream: StreamItem): StreamAttemptMark? {
        val key = stream.playerSourceIdentityKey() ?: return null
        return when {
            key in failedKeys -> StreamAttemptMark.Failed
            key == lastPlayedKey -> StreamAttemptMark.LastPlayed
            else -> null
        }
    }
}

/**
 * Remembers, while the app runs, which stream was last opened for each episode and which ones
 * failed, so the stream list can mark them and scroll back to the last one.
 */
internal object StreamAttemptStore {
    private data class Playing(val contentKey: String, val streamKey: String)

    private val _state = MutableStateFlow<Map<String, StreamAttempts>>(emptyMap())
    val state: StateFlow<Map<String, StreamAttempts>> = _state.asStateFlow()

    private var playing: Playing? = null

    fun contentKey(videoId: String, season: Int?, episode: Int?): String =
        "$videoId|${season ?: -1}|${episode ?: -1}"

    fun markPlayed(contentKey: String, stream: StreamItem) {
        val key = stream.playerSourceIdentityKey() ?: return
        playing = Playing(contentKey, key)
        _state.update { all ->
            val attempts = all[contentKey] ?: StreamAttempts()
            all + (contentKey to attempts.copy(lastPlayedKey = key, failedKeys = attempts.failedKeys - key))
        }
    }

    /** Marks the stream last opened for [contentKey] as failed, if it is the one playing. */
    fun markPlayingFailed(contentKey: String) {
        val current = playing?.takeIf { it.contentKey == contentKey } ?: return
        _state.update { all ->
            val attempts = all[contentKey] ?: StreamAttempts()
            if (current.streamKey in attempts.failedKeys) return@update all
            all + (contentKey to attempts.copy(failedKeys = attempts.failedKeys + current.streamKey))
        }
    }
}
