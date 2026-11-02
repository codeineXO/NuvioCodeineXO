package com.nuvio.app.features.details

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.navigationMotionDuration

/** Fade through a replacement so the two pages never share the foreground. */
internal class EnhancedDetailsPageTransition {
    val opacity = Animatable(1f)
    var entering by mutableStateOf(false)
        private set
    var forward by mutableStateOf(true)
        private set

    suspend fun change(forward: Boolean, replace: suspend () -> Unit) {
        this.forward = forward
        entering = false
        try {
            opacity.animateTo(0f, tween(navigationMotionDuration(NuvioTokens.Motion.fastMillis), easing = NuvioTokens.Motion.standard))
            replace()
            entering = true
            opacity.animateTo(1f, tween(navigationMotionDuration(NuvioTokens.Motion.normalMillis), easing = NuvioTokens.Motion.standard))
        } finally {
            opacity.snapTo(1f)
        }
    }
}

internal fun Modifier.enhancedDetailsPageTransition(transition: EnhancedDetailsPageTransition): Modifier = graphicsLayer {
    alpha = transition.opacity.value
    val direction = if (transition.forward) 1 else -1
    translationY = (1f - alpha) * direction * (if (transition.entering) 10.dp.toPx() else -6.dp.toPx())
}
