package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize

interface PlayerGestureController {
    fun currentBrightness(): Float?
    fun setBrightness(level: Float): Float?
    fun currentVolume(): PlayerAudioLevel?
    fun setVolume(level: Float): PlayerAudioLevel?
}

data class PlayerAudioLevel(
    val fraction: Float,
    val isMuted: Boolean,
)

@Composable
expect fun LockPlayerToLandscape()

@Composable
expect fun FullscreenPlayerDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
)

@Composable
expect fun HidePlayerSystemBars()

@Composable
expect fun EnterImmersivePlayerMode(keepScreenAwake: Boolean)

@Composable
expect fun ManagePlayerPictureInPicture(
    isPlaying: Boolean,
    videoSize: IntSize,
)

expect fun togglePlayerPictureInPicture()

/** True when the mini player shows in its own window docked over the app instead of inside the page. */
expect fun playerMiniPlayerUsesDockedWindow(): Boolean

/** Moves the video into the docked mini player window, then runs [onDocked]; [onDocked] is skipped if docking fails. */
expect fun dockPlayerForMiniPlayer(onDocked: () -> Unit)

/** Moves the video back from the docked mini player window, if it is there. */
expect fun undockMiniPlayer()

@Composable
expect fun rememberIsInPictureInPicture(): Boolean

/**
 * Key of the output media audio plays on (speaker, wired or Bluetooth headphones…), updated when
 * the output changes. Null while [enabled] is false or when the platform cannot tell.
 */
@Composable
internal expect fun rememberAudioOutputKey(enabled: Boolean): String?

@Composable
fun rememberPlayerGestureController(restoreBrightness: Boolean): PlayerGestureController? {
    val controller = rememberPlatformPlayerGestureController() ?: return null
    DisposableEffect(controller, restoreBrightness) {
        if (restoreBrightness) {
            PlayerSettingsStorage.loadPlaybackBrightness()
                ?.takeIf { it in 0f..1f }
                ?.let(controller::setBrightness)
        }
        onDispose {}
    }
    return remember(controller) {
        object : PlayerGestureController by controller {
            override fun setBrightness(level: Float): Float? {
                if (!level.isFinite()) return null
                return controller.setBrightness(level.coerceIn(0f, 1f))
                    ?.also(PlayerSettingsStorage::savePlaybackBrightness)
            }
        }
    }
}

@Composable
internal expect fun rememberPlatformPlayerGestureController(): PlayerGestureController?
