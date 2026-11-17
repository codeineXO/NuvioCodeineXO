package com.nuvio.app.features.details.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioDesktopAnimation
import com.nuvio.app.core.ui.NuvioTokens

@Composable
internal fun DetailBackdropImage(
    model: Any?,
    contentDescription: String?,
    carriedArtwork: Painter?,
    alignment: Alignment,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    onSuccess: (AsyncImagePainter.State.Success) -> Unit,
) {
    var loaded by remember(model) { mutableStateOf(false) }
    val reveal = if (carriedArtwork == null) 1f else key(model) {
        animateFloatAsState(
            targetValue = if (loaded) 1f else 0f,
            animationSpec = tween(NuvioTokens.Motion.normalMillis),
            label = "detail_backdrop_reveal",
        ).value
    }
    Box(modifier) {
        if (carriedArtwork != null && reveal < 1f) {
            Image(
                painter = carriedArtwork,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                alignment = alignment,
                contentScale = contentScale,
            )
        }
        if (model != null) {
            NuvioAsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                alignment = alignment,
                contentScale = contentScale,
                alpha = reveal,
                desktopAnimation = NuvioDesktopAnimation.Always,
                onSuccess = { state ->
                    loaded = true
                    onSuccess(state)
                },
            )
        }
    }
}
