package com.nuvio.app.features.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.video_enhancements_hardware_note
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun VideoEnhancementsHardwareNote() {
    Text(
        text = stringResource(Res.string.video_enhancements_hardware_note),
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = NuvioTokens.Space.s16,
            vertical = NuvioTokens.Space.s12,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.nuvio.colors.textMuted,
    )
}
