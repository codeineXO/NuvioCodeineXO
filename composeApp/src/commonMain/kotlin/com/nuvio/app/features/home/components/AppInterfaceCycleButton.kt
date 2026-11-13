package com.nuvio.app.features.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.nuvio.app.core.ui.LocalNavigationGlassState
import com.nuvio.app.core.ui.excludeFromRemoteNavigation
import com.nuvio.app.core.ui.navigationGlass
import com.nuvio.app.features.home.AppInterface
import dev.chrisbanes.haze.HazeState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_switch_layout
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppInterfaceCycleButton(
    value: AppInterface,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    hazeState: HazeState? = LocalNavigationGlassState.current,
) {
    val label = stringResource(value.label)
    val actionLabel = stringResource(Res.string.home_switch_layout)
    val tooltipPosition = TooltipDefaults.rememberPlainTooltipPositionProvider()

    TooltipBox(
        positionProvider = remember(tooltipPosition) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    val position = tooltipPosition.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)
                    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                    val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
                    return IntOffset(position.x.coerceIn(0, maxX), position.y.coerceIn(0, maxY))
                }
            }
        },
        tooltip = { PlainTooltip { Text("$actionLabel · $label") } },
        state = rememberTooltipState(),
        focusable = false,
    ) {
        Box(
            modifier = modifier
                .size(buttonSize)
                .navigationGlass(CircleShape, hazeState = hazeState)
                .excludeFromRemoteNavigation()
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onToggle,
                )
                .semantics {
                    contentDescription = "$actionLabel · $label"
                },
            contentAlignment = Alignment.Center,
        ) {
            val icon = when (value) {
                AppInterface.Official -> Icons.Rounded.ViewModule
                AppInterface.ScrollPosters -> Icons.Rounded.ViewCarousel
                AppInterface.PersistentHero -> Icons.Rounded.ViewAgenda
            }
            val tint = if (value == AppInterface.Official) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
