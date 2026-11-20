package com.nuvio.app.core.ui


import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

private val MenuShape = RoundedCornerShape(NuvioTokens.Space.s20)

private val MenuMinWidth = 180.dp

@Composable
fun Menu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(NuvioTokens.Space.none, NuvioTokens.Space.s6),
    useGlass: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val scrollState = rememberScrollState()
    val shape = if (useGlass) tokens.shapes.compactCard else MenuShape
    PlatformBackHandler(enabled = expanded, onBack = onDismissRequest)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = MenuMinWidth)
            .then(if (useGlass) Modifier.navigationGlass(shape = shape, panel = true, backgroundOnly = true) else Modifier)
            .nuvioDesktopDragScroll(scrollState, Orientation.Vertical),
        scrollState = scrollState,
        offset = offset,
        shape = shape,
        containerColor = if (useGlass) Color.Transparent else tokens.colors.surfacePopover,
        tonalElevation = tokens.elevation.flat,
        shadowElevation = if (useGlass) tokens.elevation.flat else tokens.elevation.overlay,
        border = if (useGlass) BorderStroke(tokens.borders.thin, tokens.colors.borderDefault) else BorderStroke(tokens.borders.thin, SurfaceEdge),
        content = {
            AppOverlayInput {
                if (useGlass) {
                    // Inner controls render as settings-style insets on the glass panel.
                    CompositionLocalProvider(
                        LocalSettingsSurfaceStyleEnabled provides true,
                        LocalSettingsPanelPresent provides true,
                    ) { DarkGlassPanelContent { content() } }
                } else {
                    content()
                }
            }
        },
    )
}

@Composable
fun MenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    useGlass: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    val shape = tokens.shapes.compactCard
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTokens.Space.s8)
            .clip(shape)
            .background(
                when {
                    !selected -> Color.Transparent
                    useGlass -> tokens.colors.accent.copy(alpha = tokens.opacity.selected)
                    else -> tokens.colors.accent.copy(alpha = tokens.opacity.hover)
                },
            )
            // A click leaves focus on the row; the keyboard focus ring comes from the navigation target.
            .then(if (useGlass) Modifier.navigationGlassFeedback(interactionSource, shape, showFocus = false) else Modifier)
            .remoteNavigationTarget(shape = shape)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(horizontal = NuvioTokens.Space.s12, vertical = if (useGlass) NuvioTokens.Space.s10 else NuvioTokens.Space.s12),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = if (enabled) tokens.colors.textPrimary else tokens.colors.textDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = tokens.colors.accent,
                modifier = Modifier.size(tokens.icons.md),
            )
        }
    }
}
