package com.nuvio.app.features.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.NavigationFeel
import com.nuvio.app.core.ui.NavigationMotionSettings
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NavigationFeelSettingsRow(isTablet: Boolean) {
    val feel by NavigationMotionSettings.feel.collectAsState()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    // Use the full-width settings row as one focus target, matching adjacent controls.
    SettingsNavigationRow(
        title = stringResource(Res.string.settings_navigation_feel),
        description = stringResource(Res.string.settings_navigation_feel_description),
        isTablet = isTablet,
        trailingContent = {
            Text(stringResource(if (feel == NavigationFeel.Smooth) Res.string.settings_navigation_feel_smooth
                else Res.string.settings_navigation_feel_snappy))
        },
        onClick = { showPicker = true },
    )
    if (showPicker) {
        DialogSurface(
            title = stringResource(Res.string.settings_navigation_feel),
            message = stringResource(Res.string.settings_navigation_feel_description),
            onDismissRequest = { showPicker = false },
        ) {
            NavigationFeel.entries.forEach { option ->
                DialogOption(
                    text = stringResource(if (option == NavigationFeel.Smooth) Res.string.settings_navigation_feel_smooth
                        else Res.string.settings_navigation_feel_snappy),
                    description = stringResource(if (option == NavigationFeel.Smooth) Res.string.settings_navigation_feel_smooth_description
                        else Res.string.settings_navigation_feel_snappy_description),
                    selected = option == feel,
                    onClick = {
                        NavigationMotionSettings.select(option)
                        showPicker = false
                    },
                )
            }
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_done), onClick = { showPicker = false })
            }
        }
    }
}
