package com.nuvio.app.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.isRemoteKeyboardNavigationActive
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.remoteNavigationTarget
import com.nuvio.app.features.remote.RemoteServer
import com.nuvio.app.features.remote.RemoteServerPlatform
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.compose_settings_remote_connect
import nuvio.composeapp.generated.resources.compose_settings_remote_copy_url
import nuvio.composeapp.generated.resources.compose_settings_remote_generate_qr_code
import nuvio.composeapp.generated.resources.compose_settings_remote_no_address
import nuvio.composeapp.generated.resources.compose_settings_remote_qr_code
import nuvio.composeapp.generated.resources.compose_settings_remote_url_copied
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RemoteSettings(isTablet: Boolean) {
    val platform = RemoteServer.platform ?: return
    val enabled by RemoteServer.enabled.collectAsStateWithLifecycle()
    val port by RemoteServer.port.collectAsStateWithLifecycle()
    val connection by RemoteServer.connection.collectAsStateWithLifecycle()
    val url = connection.url
    var showPortDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember(url) { mutableStateOf(false) }

    SettingsSection(title = "REMOTE", isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = "Phone Remote",
                description = connection.error ?: if (enabled && url == null) {
                    stringResource(Res.string.compose_settings_remote_no_address)
                } else {
                    "Control Nuvio from a phone on the same Wi-Fi"
                },
                checked = enabled,
                isTablet = isTablet,
                onCheckedChange = RemoteServer::setEnabled,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = "Port",
                description = port.toString(),
                isTablet = isTablet,
                onClick = { showPortDialog = true },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.compose_settings_remote_generate_qr_code),
                description = url,
                icon = Icons.Rounded.QrCode,
                enabled = url != null,
                isTablet = isTablet,
                onClick = { showQrDialog = true },
            )
        }
    }

    if (showQrDialog && url != null) {
        RemoteQrDialog(url = url, platform = platform, onDismiss = { showQrDialog = false })
    }

    if (showPortDialog) {
        RemotePortDialog(
            initialPort = port,
            onSave = {
                RemoteServer.setPort(it)
                showPortDialog = false
            },
            onDismiss = { showPortDialog = false },
        )
    }
}

@Composable
private fun RemoteQrDialog(url: String, platform: RemoteServerPlatform, onDismiss: () -> Unit) {
    val qrCode = remember(url) { platform.qrCode(url) }
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(Res.string.compose_settings_remote_url_copied)
    val title = stringResource(Res.string.compose_settings_remote_qr_code)

    DialogSurface(
        title = title,
        message = stringResource(Res.string.compose_settings_remote_connect),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.nuvio.spacing.listGap),
        ) {
            Image(
                bitmap = qrCode,
                contentDescription = title,
                modifier = Modifier.size(320.dp),
                filterQuality = FilterQuality.None,
            )
            Text(text = url, style = MaterialTheme.typography.bodyMedium)
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_close), onClick = onDismiss)
                DialogButton(
                    text = stringResource(Res.string.compose_settings_remote_copy_url),
                    style = DialogButtonStyle.Primary,
                    onClick = {
                        clipboard.setText(AnnotatedString(url))
                        NuvioToastController.show(copiedMessage)
                    },
                )
            }
        }
    }
}

@Composable
private fun RemotePortDialog(initialPort: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var value by remember { mutableStateOf(initialPort.toString()) }
    var isEditing by remember { mutableStateOf(false) }
    val parsed = value.toIntOrNull()?.takeIf { it in RemoteServer.PORTS }

    DialogSurface(onDismissRequest = onDismiss, title = "Remote Port") {
        OutlinedTextField(
            value = value,
            onValueChange = { text -> value = text.filter(Char::isDigit).take(5) },
            readOnly = isRemoteKeyboardNavigationActive && !isEditing,
            modifier = Modifier.fillMaxWidth()
                .remoteNavigationTarget(textField = true, onEditingChange = { isEditing = it }),
            singleLine = true,
            isError = parsed == null,
            label = { Text("Port (${RemoteServer.PORTS.first}-${RemoteServer.PORTS.last})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = tokens.colors.borderFocus.copy(alpha = tokens.opacity.strong),
                unfocusedBorderColor = tokens.colors.borderDefault.copy(alpha = tokens.opacity.medium),
                focusedContainerColor = tokens.colors.surface,
                unfocusedContainerColor = tokens.colors.surface,
            ),
        )
        DialogButtons {
            DialogButton(text = "Cancel", onClick = onDismiss)
            DialogButton(
                text = "Save",
                style = DialogButtonStyle.Primary,
                enabled = parsed != null,
                onClick = { parsed?.let(onSave) },
            )
        }
    }
}
