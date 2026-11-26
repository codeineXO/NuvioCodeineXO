package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.settings.codeinexo.CodeineXOSection
import com.nuvio.app.features.settings.codeinexo.codeineXOSectionItems
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * CodeineXO settings.
 * With [section] set, adds only the CodeineXO sections that are also shown on that stock page.
 */
internal fun LazyListScope.codeineXOSettingsContent(
    section: SettingsPage? = null,
    isTablet: Boolean,
    onOnboardingClick: (() -> Unit)? = null,
) {
    if (section != null) {
        codeineXOSectionItems(CodeineXOSection.entries.filter { it.alsoShownOn == section }, isTablet)
        return
    }
    if (onOnboardingClick != null) {
        item { QuickSetupSection(isTablet, onOnboardingClick) }
    }
    codeineXOSectionItems(CodeineXOSection.entries, isTablet)
    item { SupportSection(isTablet) }
}

@Composable
private fun QuickSetupSection(isTablet: Boolean, onOnboardingClick: () -> Unit) {
    SettingsSection(title = stringResource(Res.string.xo_section_quick_setup), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.xo_onboarding_title),
                description = stringResource(Res.string.xo_onboarding_description),
                icon = Icons.Rounded.AutoAwesome,
                isTablet = isTablet,
                onClick = onOnboardingClick,
            )
        }
    }
}

@Composable
private fun SupportSection(isTablet: Boolean) {
    val tokens = MaterialTheme.nuvio
    val clipboardManager = LocalClipboardManager.current
    val copiedMessage = stringResource(Res.string.xo_support_copied)
    SettingsSection(title = stringResource(Res.string.xo_section_support), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.xo_support_discord_dm),
                description = null,
                icon = Icons.Rounded.Chat,
                isTablet = isTablet,
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(Res.string.xo_support_copy_username),
                        tint = tokens.colors.textMuted,
                        modifier = Modifier.size(if (isTablet) 20.dp else 18.dp),
                    )
                },
                onClick = {
                    clipboardManager.setText(AnnotatedString("codeine_xo"))
                    NuvioToastController.show(copiedMessage)
                },
            )
        }
    }
}
