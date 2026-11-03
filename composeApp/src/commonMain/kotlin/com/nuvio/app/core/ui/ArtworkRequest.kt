package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.Extras
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.nuvio.app.isDesktop

internal enum class ArtworkRole(val aspectRatio: Double) {
    Poster(2.0 / 3.0),
    Backdrop(16.0 / 9.0),
}

internal val ArtworkRoleKey = Extras.Key<ArtworkRole?>(null)
internal val ArtworkHeadroomKey = Extras.Key<Double>(1.25)

/** Hints affect desktop requests only; provider URLs and caller request options stay intact. */
@Composable
internal fun rememberArtworkRequest(model: Any?, role: ArtworkRole, headroom: Double = 1.25): Any? {
    val context = LocalPlatformContext.current
    return remember(context, model, role, headroom) {
        if (!isDesktop || model == null) model else {
            val builder = if (model is ImageRequest) model.newBuilder() else ImageRequest.Builder(context).data(model)
            builder.apply {
                extras[ArtworkRoleKey] = role
                extras[ArtworkHeadroomKey] = headroom
            }.build()
        }
    }
}

/** Includes the stretched layout height and the additional graphics-layer zoom. */
internal fun heroArtworkHeadroom(heightDp: Float, motionStrength: Float): Double {
    val stretch = 1.0 + HeroStretchMaxDp / heightDp.coerceAtLeast(1f) * motionStrength
    val scroll = 1.0 + 0.3 * motionStrength
    return maxOf(1.25, stretch * stretch * scroll)
}
