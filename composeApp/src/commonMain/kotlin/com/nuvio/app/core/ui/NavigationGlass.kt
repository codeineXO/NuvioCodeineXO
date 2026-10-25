package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** App-scoped glass policy, inherited by screen content, dialogs, and popups. */
internal val LocalFrostedGlassEnabled = staticCompositionLocalOf { true }

/** Only Compose page content is registered here; never a video surface or player UI. */
internal val LocalNavigationGlassState = staticCompositionLocalOf<HazeState?> { null }
internal val LocalDesktopSidebarContentInset = staticCompositionLocalOf { 0.dp }

/** Attach to artwork or scrolling content, with glass controls in a sibling layer. */
@Composable
internal fun Modifier.navigationGlassSource(zIndex: Float = 0f): Modifier {
    if (!LocalFrostedGlassEnabled.current) return this
    val state = LocalNavigationGlassState.current ?: return this
    return hazeSource(state, zIndex = zIndex)
}

/** Bounded backdrop blur. Content (including labels and focus rings) stays sharp. */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.navigationGlass(
    shape: Shape = MaterialTheme.nuvio.shapes.avatar,
    panel: Boolean = false,
    hazeState: HazeState? = LocalNavigationGlassState.current,
    backgroundOnly: Boolean = false,
): Modifier {
    val tokens = MaterialTheme.nuvio
    val frostedGlassEnabled = LocalFrostedGlassEnabled.current
    // In-page effects must not sample the z=1 scrolling source which contains them.
    // Navigation overlays retain their existing full-page backdrop behavior.
    val backdropState = hazeState?.takeIf { state ->
        frostedGlassEnabled && (!backgroundOnly || state.areas.any { it.zIndex < 1f })
    }
    // The same light, theme-neutral tint for controls and panels, without a gray surface wash.
    val tint = tokens.colors.background.copy(alpha = tokens.opacity.pressed)
    val fallback = tokens.colors.background.copy(alpha = tokens.opacity.strong)
    return this
        .shadow(if (panel) tokens.elevation.modal else tokens.elevation.raised, shape, clip = false)
        .clip(shape)
        .background(
            if (!frostedGlassEnabled) {
                (if (panel) tokens.colors.surfacePopover else tokens.colors.surfaceCard).copy(alpha = 1f)
            } else if (backdropState == null) fallback else tint,
        )
        .then(
            if (backdropState != null) {
                Modifier.hazeEffect(backdropState) {
                    if (backgroundOnly) canDrawArea = { it.zIndex < 1f }
                    backgroundColor = tokens.colors.background
                    blurRadius = if (panel) 16.dp else 10.dp
                    inputScale = HazeInputScale.Auto
                    noiseFactor = 0.02f
                    tints = listOf(HazeTint(tint))
                    fallbackTint = HazeTint(fallback)
                }
            } else Modifier,
        )

}

/** Shared interaction overlay inside a glass panel; deliberately adds no second blur. */
@Composable
internal fun Modifier.navigationGlassFeedback(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    selected: Boolean = false,
    showFocus: Boolean = true,
): Modifier {
    val tokens = MaterialTheme.nuvio
    val hovered by interactionSource.collectIsRemoteHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha = when {
        pressed -> tokens.opacity.pressed
        focused && showFocus -> tokens.opacity.selected
        hovered -> tokens.opacity.hover
        selected -> tokens.opacity.subtle
        else -> 0f
    }
    // Existing remote-navigation focus indicators are drawn by the target modifier.
    return background(tokens.colors.textPrimary.copy(alpha = alpha), shape)
}
