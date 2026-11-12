package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.RemoteNavigationContentScope
import com.nuvio.app.core.ui.SettingsAwareSurface
import com.nuvio.app.features.remote.RemoteServer
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_settings_page_root
import nuvio.composeapp.generated.resources.compose_settings_page_codeine_xo
import org.jetbrains.compose.resources.stringResource

internal fun settingsContentKey(page: SettingsPage, category: SettingsCategory): String =
    if (page == SettingsPage.Root) "Root:${category.name}" else page.name

/** The category rail survives page replacement and never becomes a content bookmark. */
@Composable
internal fun SettingsNavigationLayout(
    page: SettingsPage,
    category: SettingsCategory,
    topOffset: Dp,
    onCategorySelected: (SettingsCategory) -> Unit,
    onCodeineClick: () -> Unit,
    onRemoteClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier.fillMaxSize()) {
        SettingsAwareSurface(Modifier.width(280.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface, panel = true) {
            Column(Modifier.fillMaxSize().padding(top = topOffset).verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.compose_settings_page_root),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp),
                    style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                SettingsCategory.entries.forEach { item ->
                    key(item) {
                        SettingsSidebarItem(stringResource(item.labelRes), item.icon,
                            selected = item == category && page != SettingsPage.CodeineXO && page != SettingsPage.Remote,
                            onClick = { onCategorySelected(item) })
                    }
                }
                key(SettingsPage.CodeineXO) {
                    SettingsSidebarItem(stringResource(Res.string.compose_settings_page_codeine_xo), Icons.Rounded.AutoAwesome,
                        selected = page == SettingsPage.CodeineXO, onClick = onCodeineClick)
                }
                if (RemoteServer.platform != null) {
                    key(SettingsPage.Remote) {
                        SettingsSidebarItem(stringResource(SettingsPage.Remote.titleRes), SettingsPage.Remote.icon,
                            selected = page == SettingsPage.Remote, onClick = onRemoteClick)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val contentKey = settingsContentKey(page, category)
            RemoteNavigationContentScope(contentKey) {
                // Replace the page before lazy measurement; outgoing native popups
                // must be disposed before the scene starts measuring its layers.
                key(contentKey) { content() }
            }
        }
    }
}
