package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.alpha
import com.nuvio.app.features.player.*

@Composable
internal fun SubtitleAppearanceSettings(style: SubtitleStyleState, isTablet: Boolean = false, enabled: Boolean = true, onStyleChanged: (SubtitleStyleState) -> Unit) {
    Column(Modifier.alpha(if (enabled) 1f else 0.5f)) {
        SubtitleStyleControls(style, { if (enabled) onStyleChanged(it) }, isCompact = !isTablet)
    }
}
