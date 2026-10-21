package com.nuvio.app.features.details

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

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
            opacity.animateTo(0f, tween(60))
            replace()
            entering = true
            opacity.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
        } finally {
            opacity.snapTo(1f)
        }
    }
}

internal fun Modifier.enhancedDetailsPageTransition(transition: EnhancedDetailsPageTransition): Modifier = graphicsLayer {
    alpha = transition.opacity.value
    val direction = if (transition.forward) 1 else -1
    translationY = (1f - alpha) * direction * (if (transition.entering) 14.dp.toPx() else -8.dp.toPx())
}
