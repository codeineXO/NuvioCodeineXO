package com.nuvio.app.features.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.downloads.DownloadLocation
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.isDesktop
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DownloadLocationSettings(isTablet: Boolean) {
    if (!isDesktop) return
    val profiles by ProfileRepository.state.collectAsStateWithLifecycle()
    LaunchedEffect(profiles.activeProfile) { DownloadLocation.refresh() }
    val path by DownloadLocation.path.collectAsStateWithLifecycle()
    val busy by DownloadLocation.busy.collectAsStateWithLifecycle()
    SettingsSection(title = stringResource(Res.string.compose_settings_root_downloads_title).uppercase(), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.downloads_choose_directory),
                description = if (busy) stringResource(Res.string.downloads_location_moving) else path,
                icon = Icons.Rounded.FolderOpen,
                enabled = !busy,
                isTablet = isTablet,
                onClick = { DownloadLocation.chooseFolder() },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.downloads_location_default),
                description = stringResource(Res.string.downloads_location_move_description),
                enabled = !busy,
                isTablet = isTablet,
                onClick = { DownloadLocation.chooseFolder(restoreDefault = true) },
            )
        }
    }
}
