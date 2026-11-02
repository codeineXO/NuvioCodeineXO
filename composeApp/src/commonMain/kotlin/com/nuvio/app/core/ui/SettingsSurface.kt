package com.nuvio.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.settings.SurfaceStyle

internal val LocalSettingsSurfaceStyleEnabled = staticCompositionLocalOf { false }
internal val LocalSettingsPanelPresent = staticCompositionLocalOf { false }

@Composable
internal fun SettingsSurfaceStyle(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSettingsSurfaceStyleEnabled provides true, content = content)
}

/** A new overlay must paint its own surface even when opened inside a settings card. */
@Composable
internal fun SettingsOverlaySurfaceLayer(content: @Composable () -> Unit) {
    if (LocalSettingsSurfaceStyleEnabled.current) {
        CompositionLocalProvider(LocalSettingsPanelPresent provides false) {
            DarkGlassPanelContent(enabled = false, content = content)
        }
    } else {
        content()
    }
}

/** Retains ordinary Surface behavior outside settings and semantic fills inside panels. */
@Composable
internal fun SettingsAwareSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(color),
    border: BorderStroke? = null,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    panel: Boolean = false,
    content: @Composable () -> Unit,
) {
    val enabled = LocalSettingsSurfaceStyleEnabled.current
    val nested = LocalSettingsPanelPresent.current
    val glass = enabled && LocalSurfaceStyle.current != SurfaceStyle.Flat
    Surface(
        modifier = modifier.then(
            if (glass && !nested) Modifier.navigationGlass(shape, panel = panel, backgroundOnly = true)
            else Modifier,
        ),
        shape = shape,
        color = when {
            !enabled -> color
            glass && !nested -> Color.Transparent
            glass && color.alpha == 1f -> Color.Transparent
            !nested -> color.copy(alpha = 1f)
            else -> color
        },
        contentColor = contentColor,
        border = border,
        tonalElevation = if (glass) 0.dp else tonalElevation,
        shadowElevation = if (glass) 0.dp else shadowElevation,
    ) {
        if (enabled) {
            CompositionLocalProvider(LocalSettingsPanelPresent provides true) {
                DarkGlassPanelContent(content = content)
            }
        } else {
            content()
        }
    }
}
