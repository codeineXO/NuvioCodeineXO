package com.nuvio.app.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.navigationMotionDuration

/** Fades never block input, so the Snappy feel may shorten them only down to a soft minimum. */
private const val MinPageFadeMillis = 220

internal fun pageFadeMillis(smoothMillis: Int): Int =
    navigationMotionDuration(smoothMillis).coerceAtLeast(MinPageFadeMillis)

/**
 * Soft crossfade with no positional travel. Pages draw over a shared backdrop, so the outgoing page fades
 * too, over the same span, rather than lingering behind or vanishing early. The incoming page uses a
 * decelerating curve so it is mostly visible within the first few frames and navigation still feels quick.
 */
internal fun pageNavigationMotion(): ContentTransform {
    val duration = pageFadeMillis(NuvioTokens.Motion.sheetEnterMillis)
    return fadeIn(tween(duration, easing = NuvioTokens.Motion.decelerate)) togetherWith
        fadeOut(tween(duration, easing = NuvioTokens.Motion.standard))
}
