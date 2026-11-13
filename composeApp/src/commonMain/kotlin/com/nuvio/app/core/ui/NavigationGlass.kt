package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.settings.SurfaceStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** App-scoped glass policy, inherited by screen content, dialogs, and popups. */
internal val LocalFrostedGlassEnabled = staticCompositionLocalOf { true }
internal val LocalSurfaceStyle = staticCompositionLocalOf { SurfaceStyle.Default }
internal val DarkGlassSurfaceColor = Color.Black.copy(alpha = 0.80f)
private val LocalDarkGlassSurfacePresent = staticCompositionLocalOf { false }

/** Design and performance tokens for frosted glass backdrop blur and reflections. */
@OptIn(ExperimentalHazeApi::class)
internal object FrostedGlassTokens {
    val BlurRadius: Dp = 24.dp
    const val NoiseFactor: Float = 0.01f
    val InputScale: HazeInputScale = HazeInputScale.Auto
    const val TintAlpha: Float = 0.05f
    val TintColor: Color = Color.White.copy(alpha = TintAlpha)
    val SecondaryContentColor: Color = Color.White.copy(alpha = 0.80f)
    val PanelBlurRadius = 36.dp
    val PanelTintColor = Color.White.copy(alpha = 0.08f)
    val PanelInputScale: HazeInputScale = HazeInputScale.Fixed(0.66f)
    val FallbackColor = Color(0xFF424242).copy(alpha = 0.94f)
    val InsetColor = Color.Black.copy(alpha = 0.20f)
    val ShadowElevation: Dp = 12.dp
    val ShadowAmbientColor: Color = Color.Black.copy(alpha = 0.12f)
    val ShadowSpotColor: Color = Color.Black.copy(alpha = 0.18f)
}

/** Brighter secondary foregrounds for frosted glass, preserving other surface styles. */
@Composable
internal fun frostedGlassSecondaryContentColor(fallback: Color): Color =
    if (LocalFrostedGlassEnabled.current && LocalSurfaceStyle.current == SurfaceStyle.FrostedGlass) {
        FrostedGlassTokens.SecondaryContentColor
    } else fallback

/** Soothing ambient drop shadow around borderless glass elements. */
internal fun Modifier.frostedGlassShadow(
    shape: Shape,
    elevation: Dp = FrostedGlassTokens.ShadowElevation,
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = FrostedGlassTokens.ShadowAmbientColor,
    spotColor = FrostedGlassTokens.ShadowSpotColor,
)

internal fun Modifier.frostedGlassBorder(shape: Shape, inset: Boolean = false): Modifier =
    if (inset) border(0.5.dp, Color.White.copy(alpha = 0.05f), shape)
    else border(0.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))), shape)

/** Inner controls share their panel's dark fill, while retaining interaction feedback. */
@Composable
internal fun DarkGlassPanelContent(content: @Composable () -> Unit) {
    DarkGlassPanelContent(enabled = LocalSurfaceStyle.current == SurfaceStyle.DarkGlass, content = content)
}

@Composable
internal fun DarkGlassPanelContent(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalDarkGlassSurfacePresent provides enabled,
        content = content,
    )
}

/** Only Compose page content is registered here; never a video surface or player UI. */
internal val LocalNavigationGlassState = staticCompositionLocalOf<HazeState?> { null }
internal val LocalDesktopSidebarContentInset = staticCompositionLocalOf { 0.dp }
internal val LocalDesktopSidebarActive = staticCompositionLocalOf { false }

/** Attach to artwork or scrolling content, with glass controls in a sibling layer. */
@Composable
internal fun Modifier.navigationGlassSource(
    zIndex: Float = 0f,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || LocalSurfaceStyle.current != SurfaceStyle.FrostedGlass || !LocalFrostedGlassEnabled.current || !LocalScreenActive.current) return this
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
    stableBackdropFill: Boolean = false,
    blurRadius: Dp = if (panel) FrostedGlassTokens.PanelBlurRadius else FrostedGlassTokens.BlurRadius,
    forceRedraw: Boolean = false,
): Modifier {
    val tokens = MaterialTheme.nuvio
    val effectBlurRadius = blurRadius
    if (LocalSettingsSurfaceStyleEnabled.current && LocalSettingsPanelPresent.current && !panel) return if (LocalSurfaceStyle.current == SurfaceStyle.FrostedGlass) clip(shape).background(FrostedGlassTokens.InsetColor).frostedGlassBorder(shape, inset = true) else clip(shape)
    val surfaceStyle = LocalSurfaceStyle.current
    val frostedGlassEnabled = LocalFrostedGlassEnabled.current && surfaceStyle == SurfaceStyle.FrostedGlass
    // In-page effects must not sample the z=1 scrolling source which contains them.
    // Navigation overlays retain their existing full-page backdrop behavior.
    val backdropState = hazeState?.takeIf { state ->
        frostedGlassEnabled && (stableBackdropFill ||
            (state.areas.isNotEmpty() && (!backgroundOnly || state.areas.any { it.zIndex < 1f })))
    }
    val tint = if (panel) FrostedGlassTokens.PanelTintColor else FrostedGlassTokens.TintColor
    val fallback = FrostedGlassTokens.FallbackColor
    val elevation = if (surfaceStyle == SurfaceStyle.DarkGlass) {
        tokens.elevation.flat
    } else if (frostedGlassEnabled) {
        if (panel) FrostedGlassTokens.ShadowElevation else 6.dp
    } else if (panel) {
        tokens.elevation.modal
    } else {
        tokens.elevation.raised
    }
    return this
        .then(
            if (frostedGlassEnabled) {
                Modifier.frostedGlassShadow(shape, elevation)
            } else {
                Modifier.shadow(elevation, shape, clip = false)
            },
        )
        .clip(shape)
        .background(
            if (surfaceStyle == SurfaceStyle.DarkGlass) {
                if (LocalDarkGlassSurfacePresent.current && !(panel && LocalSettingsSurfaceStyleEnabled.current)) {
                    Color.Transparent
                } else DarkGlassSurfaceColor
            } else if (!frostedGlassEnabled) {
                (if (panel) tokens.colors.surfacePopover else tokens.colors.surfaceCard).copy(alpha = 1f)
            } else if (backdropState == null || backdropState.areas.isEmpty()) fallback else Color.Transparent,
        )
        .then(
            if (backdropState != null) {
                Modifier.hazeEffect(backdropState) {
                    if (backgroundOnly) canDrawArea = { it.zIndex < 1f }
                    // Source content/layer changes already invalidate Haze. Forcing
                    // every pre-draw repeats work while the aura is animating.
                    forceInvalidateOnPreDraw = forceRedraw
                    backgroundColor = tokens.colors.background
                    this.blurRadius = effectBlurRadius
                    inputScale = if (panel) FrostedGlassTokens.PanelInputScale else FrostedGlassTokens.InputScale
                    noiseFactor = FrostedGlassTokens.NoiseFactor
                    tints = listOf(HazeTint(tint))
                    fallbackTint = HazeTint(fallback)
                }
            } else Modifier,
        )
        .then(if (frostedGlassEnabled) Modifier.frostedGlassBorder(shape) else Modifier)

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
