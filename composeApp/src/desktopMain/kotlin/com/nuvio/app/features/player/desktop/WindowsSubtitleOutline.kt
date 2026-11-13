package com.nuvio.app.features.player.desktop

import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.player.SubtitleOutlineEffect
import com.nuvio.app.features.player.SubtitleStyleState
import com.nuvio.app.features.player.backgroundBoxColor
import java.util.Locale

// mpv properties and ASS tags both take decimal values with a dot separator.
internal data class WindowsSubtitleOutline(
    val shadowColor: Color,
    val boxColor: Color = Color.Transparent,
    val strokeWidth: String = "0",
    val shadowOffset: String = "0",
    val blur: String = "0",
)

internal fun SubtitleStyleState.resolveWindowsSubtitleOutline(): WindowsSubtitleOutline {
    val width = outlineWidth.coerceIn(0.5f, 8f)
    val effect = if (outlineEffect.needsOutline && !outlineEnabled) {
        SubtitleOutlineEffect.NONE
    } else outlineEffect
    val outline = WindowsSubtitleOutline(shadowColor = outlineColor)
    return when (effect) {
        SubtitleOutlineEffect.NONE -> outline
        SubtitleOutlineEffect.OUTLINE -> outline.copy(strokeWidth = width.toString())
        SubtitleOutlineEffect.SOFT_GLOW -> outline.copy(
            strokeWidth = (width * 2f).coerceIn(2f, 12f).toPresetDecimal(),
            blur = (width * 1.2f + 2f).coerceIn(2.5f, 9f).toPresetDecimal(),
        )
        SubtitleOutlineEffect.OUTLINE_AND_SHADOW -> outline.copy(
            strokeWidth = width.toString(),
            shadowOffset = (width + 1f).coerceIn(2f, 5f).toString(),
            blur = (width * 0.5f + 0.8f).coerceIn(1f, 4f).toPresetDecimal(),
        )
        SubtitleOutlineEffect.BACKGROUND_BOX -> outline.copy(boxColor = backgroundBoxColor)
    }
}

private fun Float.toPresetDecimal(): String = String.format(Locale.US, "%.1f", this)
