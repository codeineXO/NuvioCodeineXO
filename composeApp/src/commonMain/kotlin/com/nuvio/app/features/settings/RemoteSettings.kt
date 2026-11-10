package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.isRemoteKeyboardNavigationActive
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.remoteNavigationTarget
import com.nuvio.app.features.remote.RemoteServer

@Composable
internal fun RemoteSettings(isTablet: Boolean) {
    if (RemoteServer.platform == null) return
    val enabled by RemoteServer.enabled.collectAsStateWithLifecycle()
    val port by RemoteServer.port.collectAsStateWithLifecycle()
    val status by RemoteServer.status.collectAsStateWithLifecycle()
    var showPortDialog by remember { mutableStateOf(false) }

    SettingsSection(title = "REMOTE", isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = "Phone Remote",
                description = status ?: "Control Nuvio from a phone on the same Wi-Fi",
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
        }
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
private fun RemotePortDialog(initialPort: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    var value by remember { mutableStateOf(initialPort.toString()) }
    var isEditing by remember { mutableStateOf(false) }
    val parsed = value.toIntOrNull()?.takeIf { it in 1024..65535 }

    DialogSurface(onDismissRequest = onDismiss, title = "Remote Port") {
        OutlinedTextField(
            value = value,
            onValueChange = { text -> value = text.filter(Char::isDigit).take(5) },
            readOnly = isRemoteKeyboardNavigationActive && !isEditing,
            modifier = Modifier.fillMaxWidth()
                .remoteNavigationTarget(textField = true, onEditingChange = { isEditing = it }),
            singleLine = true,
            isError = parsed == null,
            label = { Text("Port (1024-65535)") },
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
