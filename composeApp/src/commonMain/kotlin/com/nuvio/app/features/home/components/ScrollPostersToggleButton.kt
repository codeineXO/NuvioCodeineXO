package com.nuvio.app.features.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.LocalNavigationGlassState
import com.nuvio.app.core.ui.excludeFromRemoteNavigation
import com.nuvio.app.core.ui.navigationGlass
import dev.chrisbanes.haze.HazeState
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_action_off
import nuvio.composeapp.generated.resources.compose_action_on
import nuvio.composeapp.generated.resources.settings_homescreen_scroll_posters_layout
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrollPostersToggleButton(
    isScrollLayout: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    hazeState: HazeState? = LocalNavigationGlassState.current,
) {
    val statusText = stringResource(if (isScrollLayout) Res.string.compose_action_on else Res.string.compose_action_off)
    val settingTitle = stringResource(Res.string.settings_homescreen_scroll_posters_layout)
    val label = "$settingTitle: $statusText"

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
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
                    contentDescription = label
                },
            contentAlignment = Alignment.Center,
        ) {
            val icon = if (isScrollLayout) Icons.Rounded.ViewCarousel else Icons.Rounded.ViewModule
            val tint = if (isScrollLayout) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
