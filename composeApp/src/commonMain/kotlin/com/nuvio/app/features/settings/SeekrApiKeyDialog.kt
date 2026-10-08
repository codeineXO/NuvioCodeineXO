package com.nuvio.app.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.player.seekpreview.SeekrLookupClient
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.settings_playback_seekr_api_key
import nuvio.composeapp.generated.resources.settings_playback_seekr_api_key_description
import nuvio.composeapp.generated.resources.settings_playback_seekr_get_api_key
import nuvio.composeapp.generated.resources.settings_playback_seekr_invalid_api_key
import org.jetbrains.compose.resources.stringResource

private const val SEEKR_WEBSITE_URL = "https://seekr.tv"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SeekrApiKeyDialog(
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(initialValue) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val invalidKeyMessage = stringResource(Res.string.settings_playback_seekr_invalid_api_key)

    DialogSurface(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = stringResource(Res.string.settings_playback_seekr_api_key),
    ) {
        Text(
            text = stringResource(Res.string.settings_playback_seekr_api_key_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { runCatching { uriHandler.openUri(SEEKR_WEBSITE_URL) } }
                .padding(vertical = 4.dp, horizontal = 2.dp),
        ) {
            Text(
                text = "${stringResource(Res.string.settings_playback_seekr_get_api_key)} (seekr.tv) ↗",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
        }
        SettingsSecretTextField(
            value = value,
            onValueChange = {
                value = it
                errorMessage = null
            },
            label = stringResource(Res.string.settings_playback_seekr_api_key),
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null,
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
                enabled = !isVerifying,
            )
            DialogButton(
                text = stringResource(Res.string.action_save),
                style = DialogButtonStyle.Primary,
                onClick = {
                    val trimmed = value.trim()
                    if (trimmed.isEmpty()) {
                        onSave(trimmed)
                    } else if (trimmed == initialValue) {
                        onDismiss()
                    } else {
                        isVerifying = true
                        errorMessage = null
                        scope.launch {
                            val isValid = SeekrLookupClient.validateKey(trimmed)
                            isVerifying = false
                            if (isValid) {
                                onSave(trimmed)
                            } else {
                                errorMessage = invalidKeyMessage
                            }
                        }
                    }
                },
                enabled = !isVerifying,
            )
        }
    }
}
