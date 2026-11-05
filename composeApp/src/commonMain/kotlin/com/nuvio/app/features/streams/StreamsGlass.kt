package com.nuvio.app.features.streams

import com.nuvio.app.core.ui.LocalSurfaceStyle
import com.nuvio.app.features.settings.SurfaceStyle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.LocalFrostedGlassEnabled
import com.nuvio.app.core.ui.FrostedGlassTokens
import com.nuvio.app.core.ui.frostedGlassBorder
import com.nuvio.app.core.ui.nuvio

/** Only the desktop results panel provides this; player cards retain their own styling. */
internal val LocalStreamResultsGlass = staticCompositionLocalOf { false }

/** Inner surfaces share the panel's blur instead of sampling each other. */
@Composable
internal fun Modifier.streamResultsSurface(): Modifier {
    val tokens = MaterialTheme.nuvio
    val surfaceStyle = LocalSurfaceStyle.current
    return background(
        when {
            surfaceStyle == SurfaceStyle.DarkGlass -> {
                Color.White.copy(alpha = 0.07f)
            }
            surfaceStyle == SurfaceStyle.FrostedGlass || LocalFrostedGlassEnabled.current -> {
                FrostedGlassTokens.InsetColor
            }
            else -> {
                Color.White.copy(alpha = 0.07f).compositeOver(tokens.colors.surfaceCard)
            }
        },
    )
}

/** Distinct subtle border for results cards in Dark Glass and Flat styles; Frosted Glass is untouched. */
@Composable
internal fun Modifier.streamResultsBorder(shape: Shape): Modifier {
    val surfaceStyle = LocalSurfaceStyle.current
    return if (surfaceStyle == SurfaceStyle.DarkGlass || surfaceStyle == SurfaceStyle.Flat) {
        border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.10f),
            shape = shape,
        )
    } else {
        frostedGlassBorder(shape, inset = true)
    }
}

