package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_keyboard_navigation
import nuvio.composeapp.generated.resources.settings_keyboard_navigation_description
import org.jetbrains.compose.resources.stringResource

internal object KeyboardNavigationSettings {
    private val mutableEnabled = MutableStateFlow(true)
    val enabled = mutableEnabled.asStateFlow()
    private var persist: (Boolean) -> Unit = {}

    fun initialize(enabled: Boolean, save: (Boolean) -> Unit) {
        mutableEnabled.value = enabled
        persist = save
    }

    fun setEnabled(enabled: Boolean) {
        persist(enabled)
        mutableEnabled.value = enabled
    }
}

@Composable
internal fun KeyboardNavigationSettingsRow(isTablet: Boolean) {
    val enabled by KeyboardNavigationSettings.enabled.collectAsState()
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_keyboard_navigation),
        description = stringResource(Res.string.settings_keyboard_navigation_description),
        checked = enabled,
        isTablet = isTablet,
        onCheckedChange = KeyboardNavigationSettings::setEnabled,
    )
}
