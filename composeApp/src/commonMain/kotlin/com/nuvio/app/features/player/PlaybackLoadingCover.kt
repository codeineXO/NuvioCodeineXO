package com.nuvio.app.features.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nuvio.app.navigation.playerEnterFadeMillis

/**
 * Who is asking for the desktop playback loading screen. Later owners sit on top, so the player's own state wins
 * over the stream page it was opened from.
 */
internal enum class PlaybackLoadingCoverOwner(val fadesIn: Boolean) {
    StreamPage(fadesIn = true),
    PlayerScreen(fadesIn = false),
    PlayerSurface(fadesIn = false),
}

internal enum class PlaybackLoadingCoverMode {
    /** Show the loading screen. */
    Show,

    /** The player's controls page now draws the loading screen itself over the revealed video: drop it at once. */
    Revealed,
}

internal data class PlaybackLoadingCoverContent(
    val artwork: String?,
    val logo: String?,
    val title: String?,
    val message: String?,
    val progress: Float?,
    val onBack: () -> Unit,
)

internal class PlaybackLoadingCoverRequest(
    val owner: PlaybackLoadingCoverOwner,
    val mode: PlaybackLoadingCoverMode,
    val content: PlaybackLoadingCoverContent,
)

/**
 * Desktop draws one loading screen, above navigation and the player layer, from the moment a stream page starts
 * resolving a source until the player's controls page has painted its own copy over the native video window. The
 * stream page, the player screen and the player surface each only say whether they want it, so moving between them
 * never tears the screen down and rebuilds it.
 */
internal object PlaybackLoadingCover {
    private val requests = mutableStateMapOf<Any, PlaybackLoadingCoverRequest>()

    fun update(token: Any, request: PlaybackLoadingCoverRequest?) {
        if (request == null) requests.remove(token) else requests[token] = request
    }

    val revealed: Boolean
        get() = requests.values.any { it.mode == PlaybackLoadingCoverMode.Revealed }

    val active: PlaybackLoadingCoverRequest?
        get() = if (revealed) null else requests.values
            .filter { it.mode == PlaybackLoadingCoverMode.Show }
            .maxByOrNull { it.owner.ordinal }
}

/** Asks for the desktop loading screen while [mode] is non-null; withdraws on null or when it leaves composition. */
@Composable
internal fun PublishPlaybackLoadingCover(
    owner: PlaybackLoadingCoverOwner,
    mode: PlaybackLoadingCoverMode?,
    artwork: String?,
    logo: String?,
    title: String?,
    message: String?,
    progress: Float?,
    onBack: () -> Unit,
) {
    val token = remember { Any() }
    val latestOnBack by rememberUpdatedState(onBack)
    val stableOnBack = remember { { latestOnBack() } }
    val request = mode?.let {
        PlaybackLoadingCoverRequest(
            owner = owner,
            mode = it,
            content = PlaybackLoadingCoverContent(artwork, logo, title, message, progress, stableOnBack),
        )
    }
    SideEffect { PlaybackLoadingCover.update(token, request) }
    DisposableEffect(token) {
        onDispose { PlaybackLoadingCover.update(token, null) }
    }
}

@Composable
internal fun PlaybackLoadingCoverHost(modifier: Modifier = Modifier) {
    val active by remember { derivedStateOf { PlaybackLoadingCover.active } }
    val revealed by remember { derivedStateOf { PlaybackLoadingCover.revealed } }
    val alpha = remember { Animatable(0f) }
    val lastContent = remember { arrayOfNulls<PlaybackLoadingCoverContent>(1) }
    val current = active
    if (current != null) lastContent[0] = current.content
    val fadesIn = current?.owner?.fadesIn == true
    val shown = current != null
    val fadeMillis = playerEnterFadeMillis()
    LaunchedEffect(shown, fadesIn, revealed) {
        when {
            shown && fadesIn -> alpha.animateTo(1f, tween(fadeMillis, easing = FastOutSlowInEasing))
            shown -> alpha.snapTo(1f)
            revealed -> alpha.snapTo(0f)
            else -> alpha.animateTo(0f, tween(fadeMillis, easing = FastOutSlowInEasing))
        }
    }
    // Snap on and off in the same frame as the request, so a hand-over never shows a frame of the page beneath.
    val drawAlpha = when {
        shown && !fadesIn -> 1f
        revealed -> 0f
        else -> alpha.value
    }
    val content = current?.content ?: lastContent[0]
    if (content == null || (!shown && drawAlpha <= 0f)) {
        if (!shown) lastContent[0] = null
        return
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = drawAlpha }
            // Nothing beneath may be clicked through the loading screen.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        OpeningOverlay(
            artwork = content.artwork,
            logo = content.logo,
            title = content.title,
            onBack = content.onBack,
            horizontalSafePadding = 0.dp,
            message = content.message,
            progress = content.progress,
            usePageBackButton = true,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
