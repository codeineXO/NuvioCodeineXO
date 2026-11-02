package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsAttribution(
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (isTablet) 20.dp else 16.dp),
    ) {
        MemberBrandWordmark(
            height = if (isTablet) 30.dp else 26.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
