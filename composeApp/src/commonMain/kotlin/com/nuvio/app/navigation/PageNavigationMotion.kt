package com.nuvio.app.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
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
 * Soft crossfade with no positional travel. Every page paints its own opaque background, so only the page
 * on top fades while the one beneath stays fully opaque: fading both let the shared backdrop show through
 * mid-transition, which reads as a dark dip on pages with bright full-bleed art such as Home.
 *
 * NavDisplay draws the incoming page on top when moving forward and beneath the exiting page on pop.
 * Both directions follow the same decelerating reveal, so the new page is mostly visible within a few frames.
 */
internal fun pageNavigationMotion(forward: Boolean): ContentTransform {
    val fade = tween<Float>(pageFadeMillis(NuvioTokens.Motion.sheetEnterMillis), easing = NuvioTokens.Motion.decelerate)
    return if (forward) {
        // A full-opacity "fade" holds the page beneath in place, drawn, until the page on top has covered it.
        fadeIn(fade) togetherWith fadeOut(fade, targetAlpha = 1f)
    } else {
        EnterTransition.None togetherWith fadeOut(fade)
    }
}

/** Symmetric crossfade for transparent pages (the player), which cannot cover the page beneath. */
internal fun transparentPageNavigationMotion(): ContentTransform {
    val duration = pageFadeMillis(NuvioTokens.Motion.sheetEnterMillis)
    return fadeIn(tween(duration, easing = NuvioTokens.Motion.decelerate)) togetherWith
        fadeOut(tween(duration, easing = NuvioTokens.Motion.standard))
}
