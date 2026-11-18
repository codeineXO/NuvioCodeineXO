package com.nuvio.app.features.player

import androidx.compose.foundation.Image
import com.nuvio.app.core.ui.collectMappedAsState
import com.nuvio.app.core.ui.LocalNuvioPlatformDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.snap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import com.nuvio.app.core.ui.frostedGlassSecondaryContentColor

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.desktopBackButtonTopPadding
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import com.nuvio.app.core.ui.navigationGlassSource
import com.nuvio.app.isDesktop
import com.nuvio.app.core.ui.nuvioTypeScale
import com.nuvio.app.core.ui.LocalVerticalBarPadding
import com.nuvio.app.core.ui.centeredVerticalBarPadding
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_close
import nuvio.composeapp.generated.resources.action_back
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun OpeningOverlay(
    artwork: String?,
    logo: String?,
    title: String?,
    onBack: () -> Unit,
    horizontalSafePadding: Dp,
    modifier: Modifier = Modifier,
    message: String? = null,
    progress: Float? = null,
    usePageBackButton: Boolean = false,
) {
    val content = @Composable {
        OpeningOverlayContent(artwork, logo, title, onBack, horizontalSafePadding, modifier, message, progress, usePageBackButton)
    }
    if (!isDesktop) {
        content()
        return
    }
    // The desktop player draws this same screen in its controls page, which works in system pixels zoomed by the
    // player UI scale. Measure in the same units so the two hand over without the logo or text resizing.
    val playerUiScale by PlayerSettingsRepository.uiState.collectMappedAsState { it.playerUiScale }
    val platformDensity = LocalNuvioPlatformDensity.current
    val overlayDensity = remember(platformDensity, playerUiScale) {
        Density(platformDensity.density * playerUiScale.coerceIn(50, 150) / 100f, fontScale = 1f)
    }
    CompositionLocalProvider(LocalDensity provides overlayDensity, content = content)
}

@Composable
private fun OpeningOverlayContent(
    artwork: String?,
    logo: String?,
    title: String?,
    onBack: () -> Unit,
    horizontalSafePadding: Dp,
    modifier: Modifier = Modifier,
    message: String? = null,
    progress: Float? = null,
    usePageBackButton: Boolean = false,
) {
    // Desktop hands this screen between app pages and the player's controls page, which shows the logo at
    // full strength; a fresh delayed fade on every handover would read as the logo flickering.
    val contentAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (isDesktop) snap() else tween(durationMillis = 700, delayMillis = 400, easing = LinearEasing),
        label = "openingOverlayContentAlpha",
    )
    val pulse = rememberInfiniteTransition(label = "openingOverlayContentPulse")
    val contentScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "openingOverlayContentScale",
    )
    var logoLoadError by remember(logo) { mutableStateOf(false) }
    val logoUrl = logo?.takeIf { it.isNotBlank() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Desktop mirrors the player controls page (.opening-*) so the two screens line up exactly.
        val logoWidth = if (isDesktop) minOf(300.dp, maxWidth * 0.78f) else minOf(320.dp, maxWidth - 48.dp)
        val logoHeight = if (isDesktop) 180.dp else minOf(180.dp, maxHeight * 0.4f)
        val titleFontSize = if (isDesktop || maxWidth >= 600.dp) 42.sp else 30.sp
        val verticalBarPadding = if (isDesktop) PaddingValues() else LocalVerticalBarPadding.current
        val titleMaxWidth = minOf(maxWidth * 0.84f, 680.dp)
        Box(
            modifier = Modifier.matchParentSize().then(
                if (usePageBackButton) Modifier.navigationGlassSource(zIndex = -1f) else Modifier,
            ).background(Color.Black),
        ) {
            if (artwork != null) {
                val artworkModifier = Modifier.fillMaxSize().then(
                    if (isDesktop) {
                        // The first frame of the controls page's artwork drift: scale(1.015) translate(-.35%, -.25%).
                        Modifier.graphicsLayer {
                            scaleX = 1.015f
                            scaleY = 1.015f
                            translationX = -0.0035f * 1.015f * size.width
                            translationY = -0.0025f * 1.015f * size.height
                        }
                    } else Modifier,
                )
                val artworkAlignment = if (isDesktop) Alignment.Center else Alignment.TopCenter
                // Paints synchronously while the image below is still resolving, so a new screen never opens on black.
                OpeningArtworkCache.painterFor(artwork)?.let { cached ->
                    Image(
                        painter = cached,
                        contentDescription = null,
                        modifier = artworkModifier,
                        contentScale = ContentScale.Crop,
                        alignment = artworkAlignment,
                    )
                }
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    modifier = artworkModifier,
                    contentScale = ContentScale.Crop,
                    alignment = artworkAlignment,
                    onSuccess = if (isDesktop) {
                        { state -> OpeningArtworkCache.put(artwork, state.painter) }
                    } else null,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Black.copy(alpha = 0.3f),
                                    0.35f to Color.Black.copy(alpha = 0.6f),
                                    0.7f to Color.Black.copy(alpha = 0.8f),
                                    1f to Color.Black.copy(alpha = 0.9f),
                                ),
                            ),
                        ),
                )
            }
        }

        NuvioBackButton(
            usePageStyle = usePageBackButton,
            onClick = onBack,
            modifier = if (usePageBackButton) {
                Modifier.align(Alignment.TopStart)
                    .padding(verticalBarPadding)
                    .statusBarsPadding()
                    .padding(
                        start = if (isDesktop) desktopPageHorizontalPaddingForWidth(maxWidth.value) else 12.dp,
                        top = if (isDesktop) desktopBackButtonTopPadding else 8.dp,
                    )
                    .zIndex(2f)
            } else {
                Modifier.align(Alignment.TopEnd)
                    .padding(LocalVerticalBarPadding.current)
                    .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Top))
                    .padding(top = 20.dp, start = horizontalSafePadding, end = horizontalSafePadding + 20.dp)
            },
            containerColor = Color.Black.copy(alpha = if (usePageBackButton) 0.34f else 0.3f),
            showContainerOnDesktop = usePageBackButton,
            contentColor = if (usePageBackButton) MaterialTheme.colorScheme.onBackground else Color.White,
            buttonSize = 44.dp,
            iconSize = 24.dp,
            contentDescription = stringResource(if (usePageBackButton) Res.string.action_back else Res.string.compose_player_close),
        )

        val targetProgress = progress?.coerceIn(0f, 1f)
        val animatedProgress by animateFloatAsState(
            targetValue = targetProgress ?: 0f,
            animationSpec = tween(
                durationMillis = if ((targetProgress ?: 0f) >= 0.999f) 160 else 400,
                easing = LinearEasing,
            ),
            label = "openingOverlayP2pProgress",
        )
        val progressActive = targetProgress != null
        val showHorizontalProgress = progressActive && (logoUrl == null || logoLoadError)
        val statusShown = !message.isNullOrBlank() || showHorizontalProgress
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isDesktop) PaddingValues(24.dp) else centeredVerticalBarPadding(24.dp)),
            content = {
                Box(contentAlignment = Alignment.Center) {
                    if (logoUrl != null && !logoLoadError) {
                        Box(
                            modifier = Modifier
                                .width(logoWidth)
                                .height(logoHeight),
                        ) {
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = if (progressActive) 0.25f else contentAlpha
                                        if (!progressActive) {
                                            scaleX = contentScale
                                            scaleY = contentScale
                                        }
                                    },
                                contentScale = ContentScale.Fit,
                                onError = { logoLoadError = true },
                            )
                            if (progressActive) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .drawWithContent {
                                            clipRect(right = size.width * animatedProgress) {
                                                this@drawWithContent.drawContent()
                                            }
                                        },
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    } else if (!title.isNullOrBlank()) {
                        Text(
                            text = title,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            style = MaterialTheme.nuvioTypeScale.displayMd.copy(
                                fontSize = titleFontSize,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = if (isDesktop) 47.sp else TextUnit.Unspecified,
                            ),
                            modifier = Modifier
                                .then(if (isDesktop) Modifier.widthIn(max = titleMaxWidth) else Modifier)
                                .padding(horizontal = 24.dp)
                                .graphicsLayer {
                                    alpha = contentAlpha
                                    scaleX = contentScale
                                    scaleY = contentScale
                                },
                        )
                    } else {
                        NuvioLoadingIndicator(
                            modifier = Modifier.size(54.dp),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Crossfade(
                        targetState = message?.takeIf { it.isNotBlank() },
                        animationSpec = tween(260),
                        label = "openingLoadingMessage",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                    ) { loadingMessage ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (loadingMessage != null) {
                                Text(
                                    text = loadingMessage,
                                    color = if (isDesktop) Color.White.copy(alpha = 0.72f) else frostedGlassSecondaryContentColor(Color.White.copy(alpha = 0.72f)),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    style = if (isDesktop) {
                                        MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 13.sp,
                                            lineHeight = 17.55.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    } else MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                )
                            }
                        }
                    }
                    if (showHorizontalProgress) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(240.dp)
                                .height(4.dp)
                                .background(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(2.dp),
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .height(4.dp)
                                    .background(
                                        color = Color.White.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val artworkContent = measurables[0].measure(looseConstraints)
            val artworkTop = if (isDesktop) {
                // Like the controls page, centre the logo together with its status line when one is shown.
                val statusHeight = when {
                    !statusShown -> 0
                    showHorizontalProgress -> (16 + 40 + 10 + 4).dp.roundToPx()
                    else -> (16 + 40).dp.roundToPx()
                }
                ((constraints.maxHeight - artworkContent.height - statusHeight) / 2).coerceAtLeast(0)
            } else {
                (constraints.maxHeight - artworkContent.height) / 2
            }
            val statusTop = artworkTop + artworkContent.height
            val statusContent = measurables[1].measure(
                looseConstraints.copy(maxHeight = constraints.maxHeight - statusTop),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                artworkContent.placeRelative((constraints.maxWidth - artworkContent.width) / 2, artworkTop)
                statusContent.placeRelative((constraints.maxWidth - statusContent.width) / 2, statusTop)
            }
        }
    }
}
