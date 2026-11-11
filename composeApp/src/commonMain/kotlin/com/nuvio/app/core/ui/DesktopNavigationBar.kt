package com.nuvio.app.core.ui

import com.nuvio.app.features.settings.SurfaceStyle

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.nuvio.app.core.ui.collectIsRemoteHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.jelly.JellyNavigationBar
import com.nuvio.app.features.settings.NavBarStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
internal fun DesktopNavigationBar(
    items: List<FloatingNavigationItem>,
    modifier: Modifier = Modifier,
    scrollState: NuvioNavBarScrollState? = null,
    hazeState: HazeState? = null,
    contentPadding: PaddingValues = PaddingValues(top = 10.dp, bottom = 8.dp),
    navBarStyle: NavBarStyle = NavBarStyle.ADAPTIVE,
    isHeroEnabled: Boolean = false,
    profileSwitcherOpen: Boolean = false,
    windowWidth: Dp? = null,
    glowEnabled: Boolean = true,
    navigationRoute: Any = LocalRemoteRoute.current,
) {
    if (items.isEmpty()) return
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsRemoteHoveredAsState()
    val isHomeSelected = items.first().selected
    val isSettingsSelected = items.last().selected
    val isScrolledAwayFromTop by remember(scrollState, isSettingsSelected) {
        derivedStateOf {
            (scrollState?.totalScrollOffset ?: 0f) > if (isSettingsSelected) 70f else 35f
        }
    }
    val isFrosted = isHovered || profileSwitcherOpen ||
        ((isHomeSelected || isSettingsSelected) && isScrolledAwayFromTop)
    val labelFraction by animateFloatAsState(
        targetValue = desktopNavigationLabelFraction(navBarStyle, isHomeSelected, isHeroEnabled, isHovered, profileSwitcherOpen, isScrolledAwayFromTop),
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "desktop_nav_labels",
    )
    val glassElevation by animateFloatAsState(
        targetValue = if (LocalFrostedGlassEnabled.current && isFrosted) 6f else 0f,
        animationSpec = tween(320),
        label = "glass_elevation",
    )
    val surfaceColor by animateColorAsState(
        targetValue = if (isFrosted) FrostedGlassTokens.TintColor else Color.White.copy(alpha = 0.04f),
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "desktop_nav_surface",
    )
    val glowStrength by animateFloatAsState(
        targetValue = if (glowEnabled) 1f else 0f,
        animationSpec = tween(420, easing = NuvioTokens.Motion.standard),
        label = "desktop_nav_glow",
    )
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(contentPadding).padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val viewportWidth = windowWidth ?: maxWidth
        val iconSize = when {
            viewportWidth < 800.dp -> 26.dp
            viewportWidth > 1600.dp -> 30.dp
            else -> 28.dp
        }
        val trackHeight = desktopTopBarHeight(viewportWidth)
        val trackWidth = ((trackHeight + 64.dp * labelFraction) * items.size + 8.dp).coerceAtMost(maxWidth)
        CompositionLocalProvider(
            LocalRemoteNavigationRole provides RemoteNavigationRole.TopBar,
            LocalRemoteRoute provides navigationRoute,
        ) {
        JellyNavigationBar(
            items = items,
            labelFraction = labelFraction,
            glowStrength = glowStrength,
            surfaceShadowElevation = glassElevation.dp,
            horizontalLabels = true,
            iconSize = iconSize,
            modifier = Modifier.width(trackWidth).height(trackHeight).hoverable(interactionSource),
        ) {
            val pillShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier.matchParentSize()
                    .clip(pillShape)
                    .then(if (LocalFrostedGlassEnabled.current) Modifier.frostedGlassBorder(pillShape) else Modifier)
                    .then(
                        if (LocalFrostedGlassEnabled.current && isFrosted && hazeState != null) {
                            Modifier.hazeEffect(state = hazeState) {
                                blurRadius = FrostedGlassTokens.BlurRadius
                                noiseFactor = FrostedGlassTokens.NoiseFactor
                                tints = emptyList()
                                fallbackTint = HazeTint(FrostedGlassTokens.FallbackColor)
                                inputScale = FrostedGlassTokens.InputScale
                            }
                        } else {
                            Modifier
                        },
                    )
                    .background(if (LocalSurfaceStyle.current == SurfaceStyle.DarkGlass) DarkGlassSurfaceColor else if (LocalFrostedGlassEnabled.current) (if (isFrosted && hazeState?.areas?.isNotEmpty() != true) FrostedGlassTokens.FallbackColor else surfaceColor) else MaterialTheme.nuvio.colors.surfacePopover.copy(alpha = 1f)),
            )
        }
        }
    }
}

internal fun desktopNavigationLabelFraction(
    style: NavBarStyle,
    isHomeSelected: Boolean,
    isHeroEnabled: Boolean,
    isHovered: Boolean,
    profileSwitcherOpen: Boolean,
    isScrolledAwayFromTop: Boolean,
): Float = when (style) {
    NavBarStyle.EXPANDED -> 1f
    NavBarStyle.COMPACT -> 0f
    else -> when {
        isHovered || profileSwitcherOpen -> 1f
        isHomeSelected && isHeroEnabled -> 0f
        isHomeSelected && isScrolledAwayFromTop -> 0f
        else -> 1f
    }
}
