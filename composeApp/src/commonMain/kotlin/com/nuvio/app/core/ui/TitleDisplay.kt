package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** True while the user wants titles shown as their logo artwork rather than as text. */
@Composable
fun rememberTitleLogosEnabled(): Boolean {
    val style by remember {
        PosterCardStyleRepository.ensureLoaded()
        PosterCardStyleRepository.uiState
    }.collectAsStateWithLifecycle()
    return style.titleDisplayMode == TitleDisplayMode.Logo
}

/** [logo] when title logos are enabled, otherwise null so callers fall back to the title text. */
@Composable
fun <T> titleLogoOrNull(logo: T?): T? = if (rememberTitleLogosEnabled()) logo else null
