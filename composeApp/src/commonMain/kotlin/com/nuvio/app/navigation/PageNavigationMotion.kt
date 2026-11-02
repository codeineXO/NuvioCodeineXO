package com.nuvio.app.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.NavigationMotionSettings

/** Short travel and overlapping opacity keep heavy nested screens from flashing through the backdrop. */
internal fun pageNavigationMotion(forward: Boolean, distancePx: Int): ContentTransform {
    val motion = NavigationMotionSettings.profile
    val duration = motion.duration(NuvioTokens.Motion.sheetEnterMillis)
    val exitDuration = motion.duration(NuvioTokens.Motion.normalMillis)
    val enter = fadeIn(tween(duration, easing = NuvioTokens.Motion.standard)) +
        slideInVertically(tween(duration, easing = NuvioTokens.Motion.standard)) {
            distancePx.coerceAtLeast(0) * if (forward) 1 else -1
        }
    val exit = fadeOut(tween(
        durationMillis = exitDuration,
        delayMillis = duration - exitDuration,
        easing = NuvioTokens.Motion.standard,
    ))
    return enter togetherWith exit
}
