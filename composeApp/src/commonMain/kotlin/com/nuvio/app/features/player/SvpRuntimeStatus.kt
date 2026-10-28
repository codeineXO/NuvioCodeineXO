package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.remoteNavigationTarget
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.motion_smoothing_initializing
import nuvio.composeapp.generated.resources.motion_smoothing_unavailable
import org.jetbrains.compose.resources.stringResource

/** Transient status only: successful initialization needs no persistent confirmation. */
@Composable
internal fun SvpRuntimeStatus(
    state: SvpRuntimeDownloadState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    if (state is SvpRuntimeDownloadState.Installed) return
    val hasError = state is SvpRuntimeDownloadState.Error
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!hasError) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = tokens.colors.accent,
            )
        }
        Text(
            text = stringResource(if (hasError) Res.string.motion_smoothing_unavailable else Res.string.motion_smoothing_initializing),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = if (hasError) MaterialTheme.colorScheme.error else tokens.colors.textMuted,
        )
        if (hasError && onRetry != null) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.remoteNavigationTarget(shape = tokens.shapes.button),
            ) {
                Text(stringResource(Res.string.action_retry))
            }
        }
    }
}
