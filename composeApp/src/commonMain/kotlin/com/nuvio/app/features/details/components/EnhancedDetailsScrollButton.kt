package com.nuvio.app.features.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.nuvio.app.core.ui.RemoteNavigationRole
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.remoteNavigationTarget
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.details_back_to_top
import nuvio.composeapp.generated.resources.details_see_more
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EnhancedDetailsScrollButton(
    secondPage: Boolean,
    onClick: (backToTop: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSelect: (backToTop: Boolean) -> Unit = onClick,
) {
    val backToTop = secondPage
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(NuvioTokens.Radius.lg)
    Surface(
        onClick = { onClick(backToTop) },
        modifier = modifier
            .heightIn(min = NuvioTokens.Space.s40)
            .remoteNavigationTarget(shape = shape, role = RemoteNavigationRole.PageSwitch, onSelect = { onSelect(backToTop) }),
        shape = shape,
        color = tokens.colors.surfacePopover,
        contentColor = tokens.colors.textPrimary,
        shadowElevation = NuvioTokens.Elevation.raised,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = NuvioTokens.Space.s16, vertical = NuvioTokens.Space.s10),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
        ) {
            Text(
                text = stringResource(if (backToTop) Res.string.details_back_to_top else Res.string.details_see_more),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = if (backToTop) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(NuvioTokens.Space.s18),
            )
        }
    }
}
