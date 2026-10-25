package com.nuvio.app.features.streams

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.nuvio.app.core.ui.LocalFrostedGlassEnabled
import com.nuvio.app.core.ui.nuvio

/** Only the desktop results panel provides this; player cards retain their own styling. */
internal val LocalStreamResultsGlass = staticCompositionLocalOf { false }

/** Inner surfaces share the panel's blur instead of sampling each other. */
@Composable
internal fun Modifier.streamResultsSurface(): Modifier {
    val tokens = MaterialTheme.nuvio
    return background(
        if (LocalFrostedGlassEnabled.current) {
            tokens.colors.textPrimary.copy(alpha = tokens.opacity.subtle)
        } else {
            tokens.colors.surfaceCard.copy(alpha = 1f)
        },
    )
}
