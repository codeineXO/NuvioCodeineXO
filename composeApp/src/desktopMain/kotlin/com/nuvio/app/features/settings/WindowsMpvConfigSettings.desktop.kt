package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.remoteTextInputGuard
import com.nuvio.app.features.player.desktop.WindowsMpvConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun WindowsMpvConfigSettings(isTablet: Boolean, enabled: Boolean) {
    val title = stringResource(Res.string.settings_playback_mpv_config)
    var showEditor by remember { mutableStateOf(false) }
    val config by WindowsMpvConfig.settings.collectAsState()
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_playback_mpv_config_enabled),
        description = stringResource(Res.string.settings_playback_mpv_config_enabled_description),
        checked = config.enabled,
        enabled = enabled,
        isTablet = isTablet,
        onCheckedChange = WindowsMpvConfig::setEnabled,
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsNavigationRow(
        title = title,
        description = stringResource(Res.string.settings_playback_mpv_config_description),
        enabled = enabled,
        isTablet = isTablet,
        onClick = { showEditor = true },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_playback_mpv_logging),
        description = stringResource(Res.string.settings_playback_mpv_logging_description),
        checked = config.loggingEnabled,
        enabled = enabled,
        isTablet = isTablet,
        onCheckedChange = WindowsMpvConfig::setLoggingEnabled,
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_playback_hls_segment_retry),
        description = stringResource(Res.string.settings_playback_hls_segment_retry_description),
        checked = config.hlsSegmentRetryEnabled,
        enabled = enabled,
        isTablet = isTablet,
        onCheckedChange = WindowsMpvConfig::setHlsSegmentRetryEnabled,
    )
    if (enabled && showEditor) WindowsMpvConfigEditor(title) { showEditor = false }
}

@Composable
private fun WindowsMpvConfigEditor(title: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(WindowsMpvConfig.load()) }
    var issues by remember { mutableStateOf(emptyList<WindowsMpvConfig.Issue>()) }
    var runtimeError by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }

    DialogSurface(
        title = title,
        message = stringResource(Res.string.settings_playback_mpv_config_instructions),
        onDismissRequest = { if (!validating) onClose() },
    ) {
        Text(
            text = stringResource(
                Res.string.settings_playback_mpv_config_supported,
                WindowsMpvConfig.supportedOptions.joinToString(),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                issues = emptyList()
                runtimeError = false
            },
            modifier = Modifier.fillMaxWidth().remoteTextInputGuard(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            enabled = !validating,
            isError = issues.isNotEmpty() || runtimeError,
            minLines = 4,
            maxLines = 8,
        )
        if (issues.isNotEmpty() || runtimeError) {
            Column(Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState())) {
                if (runtimeError) {
                    Text(
                        stringResource(Res.string.settings_playback_mpv_config_validation_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                issues.forEach { issue ->
                    val message = when (issue.kind) {
                        WindowsMpvConfig.ErrorKind.Syntax ->
                            stringResource(Res.string.settings_playback_mpv_config_syntax, issue.line ?: 0)
                        WindowsMpvConfig.ErrorKind.Unsupported ->
                            stringResource(Res.string.settings_playback_mpv_config_unsupported, issue.line ?: 0, issue.detail)
                        WindowsMpvConfig.ErrorKind.InvalidValue ->
                            stringResource(Res.string.settings_playback_mpv_config_invalid_value, issue.line ?: 0, issue.detail)
                        WindowsMpvConfig.ErrorKind.ReservedLabel ->
                            stringResource(Res.string.settings_playback_mpv_config_reserved_label, issue.detail)
                    }
                    Text(message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_clear),
                onClick = {
                    WindowsMpvConfig.save("")
                    onClose()
                },
                enabled = !validating,
            )
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onClose,
                enabled = !validating,
            )
            DialogButton(
                text = stringResource(Res.string.action_save),
                style = DialogButtonStyle.Primary,
                loading = validating,
                onClick = {
                    val submitted = draft
                    validating = true
                    runtimeError = false
                    scope.launch {
                        try {
                            issues = withContext(Dispatchers.IO) { WindowsMpvConfig.validate(submitted) }
                            if (issues.isEmpty()) {
                                WindowsMpvConfig.save(submitted)
                                onClose()
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            runtimeError = true
                        } catch (_: LinkageError) {
                            runtimeError = true
                        } finally {
                            validating = false
                        }
                    }
                },
            )
        }
    }
}
