package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
internal fun SubtitleAppearanceControls(style: SubtitleStyleState, spacing: Dp, onStyleChanged: (SubtitleStyleState) -> Unit) {
    SubtitleStyleControls(style, onStyleChanged)
}
