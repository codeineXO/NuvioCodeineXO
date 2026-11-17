package com.nuvio.app.features.player

import androidx.compose.runtime.compositionLocalOf

/**
 * False while the full-screen player is still fading in over the app. Native video surfaces that
 * ignore Compose alpha (desktop heavyweight hosts) stay collapsed until it turns true, so they appear
 * once the fade has covered the page beneath instead of cutting in mid-transition.
 */
internal val LocalPlayerSurfaceRevealReady = compositionLocalOf { true }
