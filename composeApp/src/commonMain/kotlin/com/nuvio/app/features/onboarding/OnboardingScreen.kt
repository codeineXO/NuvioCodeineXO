package com.nuvio.app.features.onboarding

import com.nuvio.app.core.ui.remoteNavigationTarget
import com.nuvio.app.core.ui.remoteNavigationGroup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.nuvio.app.isDesktop
import com.nuvio.app.core.ui.desktopPageHorizontalPaddingForWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioBackButton
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.anime.AnimeFillerSettingsRepository
import com.nuvio.app.features.details.MetaEpisodeCardStyle
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.components.HomeInteractiveGradientBackground
import com.nuvio.app.features.p2p.P2pSettingsRepository
import com.nuvio.app.features.player.AnimeUpscalerMode
import com.nuvio.app.features.player.AnimeUpscalerOption
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerUiMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.player.SubtitleOutlineEffect
import com.nuvio.app.features.player.SvpMotionOption
import com.nuvio.app.features.player.SvpRuntimeDownloadState
import com.nuvio.app.features.player.SvpRuntimeInstaller
import com.nuvio.app.features.settings.SettingsGroup
import com.nuvio.app.features.settings.SettingsGroupDivider
import com.nuvio.app.features.settings.SettingsSection
import com.nuvio.app.features.settings.SettingsSwitchRow
import com.nuvio.app.features.settings.ThemeSettingsRepository

data class RecommendedPresetState(
    val auraBackgroundEnabled: Boolean = true,
    val scrollPostersLayoutEnabled: Boolean = true,
    val playerUiMode: PlayerUiMode = PlayerUiMode.CODEINE_XO,
    val showSeekbarWhileSeeking: Boolean = true,
    val animeUpscalerOption: AnimeUpscalerOption = AnimeUpscalerOption.AUTO,
    val animeUpscalerMode: AnimeUpscalerMode = AnimeUpscalerMode.FAST,
    val svpMotionOption: SvpMotionOption = SvpMotionOption.OFF,
    val animeFillerDetectionEnabled: Boolean = true,
    val subtitleOutlineEffect: SubtitleOutlineEffect = SubtitleOutlineEffect.OUTLINE_AND_SHADOW,
    val subtitleOutlineWidth: Int = 1,
    val subtitleColor: Color = Color(0xFFFFF6B3),
    val episodeCardStyle: MetaEpisodeCardStyle = MetaEpisodeCardStyle.Enhanced,
    val showTorrentStatsOverlay: Boolean = true,
)

internal fun applyRecommendedPreset(state: RecommendedPresetState) {
    ThemeSettingsRepository.ensureLoaded()
    ThemeSettingsRepository.setAuraBackgroundEnabled(state.auraBackgroundEnabled)

    HomeCatalogSettingsRepository.snapshot()
    HomeCatalogSettingsRepository.setScrollPostersLayoutEnabled(state.scrollPostersLayoutEnabled)

    PlayerSettingsRepository.ensureLoaded()
    PlayerSettingsRepository.setPlayerUiMode(state.playerUiMode)
    PlayerSettingsRepository.setShowSeekbarWhileSeeking(state.showSeekbarWhileSeeking)
    PlayerSettingsRepository.setAnimeUpscalerOption(state.animeUpscalerOption)
    PlayerSettingsRepository.setAnimeUpscalerMode(state.animeUpscalerMode)
    PlayerSettingsRepository.setSvpMotionOption(state.svpMotionOption)

    val currentSubStyle = PlayerSettingsRepository.uiState.value.subtitleStyle
    PlayerSettingsRepository.setSubtitleStyle(
        currentSubStyle.copy(
            outlineEffect = state.subtitleOutlineEffect,
            outlineWidth = state.subtitleOutlineWidth,
            textColor = state.subtitleColor,
            outlineColor = Color.Black,
            outlineEnabled = state.subtitleOutlineEffect.needsOutline,
        ),
    )

    AnimeFillerSettingsRepository.ensureLoaded()
    AnimeFillerSettingsRepository.setEnabled(state.animeFillerDetectionEnabled)

    MetaScreenSettingsRepository.ensureLoaded()
    MetaScreenSettingsRepository.setEpisodeCardStyle(state.episodeCardStyle)

    P2pSettingsRepository.ensureLoaded()
    P2pSettingsRepository.setShowTorrentStatsOverlay(state.showTorrentStatsOverlay)
}

@Composable
fun OnboardingScreen(
    isReviewMode: Boolean = false,
    onComplete: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    var currentStep by rememberSaveable { mutableIntStateOf(if (isReviewMode) 1 else 0) }
    var presetState by remember { mutableStateOf(RecommendedPresetState()) }
    var showReviewDialog by rememberSaveable { mutableStateOf(false) }
    var presetApplied by rememberSaveable { mutableStateOf(false) }

    val totalSteps = 3

    PlatformBackHandler(enabled = true) {
        if (currentStep > 0) {
            currentStep--
        } else if (isReviewMode && onBack != null) {
            onBack()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.background),
    ) {
        HomeInteractiveGradientBackground(modifier = Modifier.fillMaxSize())
        val backButtonStartPadding = if (isDesktop) desktopPageHorizontalPaddingForWidth(maxWidth.value) else 24.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // ─── Header Navigation Bar ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = backButtonStartPadding, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (isReviewMode && onBack != null) {
                    NuvioBackButton(
                        usePageStyle = true,
                        onClick = onBack,
                        modifier = Modifier.size(if (isDesktop) 48.dp else 36.dp),
                        shape = tokens.shapes.compactCard,
                        containerColor = tokens.colors.surface,
                        contentColor = tokens.colors.textPrimary,
                        buttonSize = if (isDesktop) 48.dp else NuvioTokens.Space.s36,
                        iconSize = tokens.icons.md,
                    )
                } else if (currentStep > 0) {
                    NuvioBackButton(
                        usePageStyle = true,
                        onClick = { currentStep-- },
                        modifier = Modifier.size(if (isDesktop) 48.dp else 36.dp),
                        shape = tokens.shapes.compactCard,
                        containerColor = tokens.colors.surface,
                        contentColor = tokens.colors.textPrimary,
                        buttonSize = if (isDesktop) 48.dp else NuvioTokens.Space.s36,
                        iconSize = tokens.icons.md,
                    )
                } else {
                    Spacer(modifier = Modifier.size(if (isDesktop) 48.dp else 36.dp))
                }

                // Step dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 0 until totalSteps) {
                        val active = i == currentStep
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (active) 24.dp else 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (active) tokens.colors.accent else tokens.colors.textMuted.copy(alpha = 0.4f),
                                ),
                        )
                    }
                }

                if (isReviewMode && onBack != null) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .remoteNavigationTarget(shape = RoundedCornerShape(8.dp)).clickable(onClick = onBack)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.Transparent,
                    ) {
                        Text(
                            text = "Exit",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.colors.textMuted,
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .remoteNavigationTarget(shape = RoundedCornerShape(8.dp)).clickable(onClick = onComplete)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.Transparent,
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.colors.textMuted,
                        )
                    }
                }
            }

            // ─── Step Content ───
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width / 2 } + fadeIn())
                                .togetherWith(slideOutHorizontally { width -> -width / 2 } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width / 2 } + fadeIn())
                                .togetherWith(slideOutHorizontally { width -> width / 2 } + fadeOut())
                        }
                    },
                    label = "onboarding_step",
                ) { step ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (step) {
                            0 -> WelcomeStepContent(
                                isTablet = true,
                                onNext = { currentStep = 1 },
                            )
                            1 -> RecommendedSetupStepContent(
                                isTablet = true,
                                presetState = presetState,
                                presetApplied = presetApplied,
                                onOpenReviewDialog = { showReviewDialog = true },
                                onApplyDirectly = {
                                    val defaults = RecommendedPresetState()
                                    presetState = defaults
                                    applyRecommendedPreset(defaults)
                                    presetApplied = true
                                    NuvioToastController.show("Recommended CodeineXO defaults applied")
                                },
                                onNext = { currentStep = 2 },
                                onBack = { currentStep = 0 },
                            )
                            2 -> ReadyStepContent(
                                isTablet = true,
                                isReviewMode = isReviewMode,
                                onFinish = onComplete,
                                onBack = { currentStep = 1 },
                            )
                        }
                    }
                }
            }
        }

        // ─── Staged Review Dialog ───
        if (showReviewDialog) {
            RecommendedPresetReviewDialog(
                state = presetState,
                onStateChange = { presetState = it },
                onDismissRequest = { showReviewDialog = false },
                onConfirm = {
                    applyRecommendedPreset(presetState)
                    presetApplied = true
                    showReviewDialog = false
                    NuvioToastController.show("Recommended CodeineXO setup applied")
                },
            )
        }
    }
}

@Composable
private fun WelcomeStepContent(
    isTablet: Boolean,
    onNext: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // App Icon / Aura Badge
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(22.dp),
            color = tokens.colors.accent.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, tokens.colors.accent.copy(alpha = 0.35f)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(42.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to NuvioCodeineXO",
            style = MaterialTheme.typography.headlineLarge,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNext,
            modifier = (Modifier
                .widthIn(min = 220.dp, max = 320.dp)
                .heightIn(min = 50.dp)).remoteNavigationTarget(shape = tokens.shapes.button),
            shape = tokens.shapes.button,
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.colors.accent,
                contentColor = tokens.colors.onAccent,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    val tokens = MaterialTheme.nuvio
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = tokens.shapes.compactCard,
        color = tokens.colors.surface.copy(alpha = 0.7f),
        border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = tokens.colors.accent.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tokens.colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun RecommendedSetupStepContent(
    isTablet: Boolean,
    presetState: RecommendedPresetState,
    presetApplied: Boolean,
    onOpenReviewDialog: () -> Unit,
    onApplyDirectly: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val svpDownloadState by SvpRuntimeInstaller.state.collectAsStateWithLifecycle()
    val isSvpInstalled = SvpRuntimeInstaller.isInstalled || svpDownloadState is SvpRuntimeDownloadState.Installed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Recommended Setup",
            style = MaterialTheme.typography.headlineLarge,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "One-click activation for the definitive CodeineXO streaming configuration.",
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Prominent Preset Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(NuvioTokens.Radius.xl))
                .remoteNavigationTarget(shape = RoundedCornerShape(NuvioTokens.Radius.xl)).clickable(onClick = onOpenReviewDialog),
            color = tokens.colors.surface,
            border = BorderStroke(
                width = if (presetApplied) 1.5.dp else 1.dp,
                color = if (presetApplied) tokens.colors.accent else tokens.colors.borderSubtle,
            ),
            shape = RoundedCornerShape(NuvioTokens.Radius.xl),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.colors.accent.copy(alpha = 0.16f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = tokens.colors.accent,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Apply Recommended CodeineXO Setup",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Click to review and customize all 10 presets",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.colors.textMuted,
                            )
                        }
                    }

                    if (presetApplied) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = tokens.colors.accent.copy(alpha = 0.2f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = tokens.colors.accent,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "Applied",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.colors.accent,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(
                    thickness = tokens.borders.hairline,
                    color = tokens.colors.borderSubtle,
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Preset Items Grid / Highlights
                PresetPillRow(
                    pills = listOf(
                        "CodeineXO Aura",
                        "Scroll Poster Layout",
                        "CodeineXO Player UI",
                        "Seekbar Scrubbing",
                        "Anime Upscaler (Fast)",
                        if (presetState.svpMotionOption == SvpMotionOption.AUTO) "Motion (SVP) 60 FPS" else "Motion (SVP) Off",
                        "Anime Filler Skip",
                        "Halo Subtitles (#FFF6B3)",
                        "Enhanced Episode Cards",
                        "Torrent Stats HUD",
                    ),
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenReviewDialog,
                        modifier = (Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)).remoteNavigationTarget(shape = tokens.shapes.button),
                        shape = tokens.shapes.button,
                        border = BorderStroke(1.dp, tokens.colors.borderDefault),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = tokens.colors.textPrimary,
                        ),
                    ) {
                        Text(
                            text = "Review & Customize",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Button(
                        onClick = onApplyDirectly,
                        modifier = (Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)).remoteNavigationTarget(shape = tokens.shapes.button),
                        shape = tokens.shapes.button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.colors.accent,
                            contentColor = tokens.colors.onAccent,
                        ),
                    ) {
                        Text(
                            text = if (presetApplied) "Re-apply Defaults" else "Apply Defaults Now",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // SVP Runtime Setup Card on Preset Page
        if (!isSvpInstalled && SvpRuntimeInstaller.isSupported) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = tokens.shapes.compactCard,
                color = tokens.colors.surfaceElevated.copy(alpha = 0.7f),
                border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = tokens.colors.accent.copy(alpha = 0.14f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = tokens.colors.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Motion Interpolation (SVP) Setup",
                                style = MaterialTheme.typography.titleSmall,
                                color = tokens.colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Download runtime pack (~26 MB) to unlock 60 FPS motion smoothing",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.colors.textMuted,
                            )
                        }
                    }

                    when (val s = svpDownloadState) {
                        is SvpRuntimeDownloadState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = tokens.colors.accent,
                                )
                                Text(
                                    text = "Downloading runtime pack... ${(s.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.colors.textSecondary,
                                )
                            }
                        }
                        is SvpRuntimeDownloadState.Extracting -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = tokens.colors.accent,
                                )
                                Text(
                                    text = "Extracting and registering runtime...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.colors.textSecondary,
                                )
                            }
                        }
                        is SvpRuntimeDownloadState.Error -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Download error: ${s.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(
                                    onClick = { SvpRuntimeInstaller.startDownload() },
                                    shape = tokens.shapes.button,
                                 modifier = Modifier.remoteNavigationTarget(shape = tokens.shapes.button)) {
                                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = { SvpRuntimeInstaller.startDownload() },
                                modifier = (Modifier.fillMaxWidth()).remoteNavigationTarget(shape = tokens.shapes.button),
                                shape = tokens.shapes.button,
                                border = BorderStroke(1.dp, tokens.colors.accent.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = tokens.colors.accent,
                                ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Download & Setup SVP Runtime (~26 MB)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (isSvpInstalled) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = tokens.shapes.compactCard,
                color = tokens.colors.surfaceElevated.copy(alpha = 0.6f),
                border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = tokens.colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "SVP Runtime Pack Installed — 60 FPS motion smoothing ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.accent,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Navigation actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = (Modifier
                    .weight(0.4f)
                    .heightIn(min = 48.dp)).remoteNavigationTarget(shape = tokens.shapes.button),
                shape = tokens.shapes.button,
                border = BorderStroke(1.dp, tokens.colors.borderDefault),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = tokens.colors.textPrimary,
                ),
            ) {
                Text("Back")
            }

            Button(
                onClick = onNext,
                modifier = (Modifier
                    .weight(0.6f)
                    .heightIn(min = 48.dp)).remoteNavigationTarget(shape = tokens.shapes.button),
                shape = tokens.shapes.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.colors.accent,
                    contentColor = tokens.colors.onAccent,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Continue",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetPillRow(pills: List<String>) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val chunked = pills.chunked(2)
        chunked.forEach { rowPills ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowPills.forEach { pill ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        color = tokens.colors.surfaceElevated.copy(alpha = 0.8f),
                        border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(tokens.colors.accent),
                            )
                            Text(
                                text = pill,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (rowPills.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReadyStepContent(
    isTablet: Boolean,
    isReviewMode: Boolean,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(22.dp),
            color = tokens.colors.accent.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, tokens.colors.accent.copy(alpha = 0.35f)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(42.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isReviewMode) "Preset Configuration Ready" else "You're Ready to Stream!",
            style = MaterialTheme.typography.headlineLarge,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your setup has been staged. You can re-access this setup wizard or adjust individual options anytime from Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FeatureHighlightRow(
                icon = Icons.Rounded.Settings,
                title = "Settings Re-access",
                subtitle = "Open Settings > CodeineXO or General anytime to launch this setup wizard again.",
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = (Modifier
                    .weight(0.4f)
                    .heightIn(min = 50.dp)).remoteNavigationTarget(shape = tokens.shapes.button),
                shape = tokens.shapes.button,
                border = BorderStroke(1.dp, tokens.colors.borderDefault),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = tokens.colors.textPrimary,
                ),
            ) {
                Text("Back")
            }

            Button(
                onClick = onFinish,
                modifier = (Modifier
                    .weight(0.6f)
                    .heightIn(min = 50.dp)).remoteNavigationTarget(shape = tokens.shapes.button),
                shape = tokens.shapes.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.colors.accent,
                    contentColor = tokens.colors.onAccent,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (isReviewMode) "Finish Review" else "Launch CodeineXO",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.Done,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendedPresetReviewDialog(
    state: RecommendedPresetState,
    onStateChange: (RecommendedPresetState) -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

    DialogSurface(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(max = 640.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "CodeineXO Recommended Preset",
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Customize any item before applying to your profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .remoteNavigationTarget(shape = RoundedCornerShape(8.dp)).clickable {
                                onStateChange(RecommendedPresetState())
                                NuvioToastController.show("Reset to recommended defaults")
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = tokens.colors.surfaceElevated,
                        border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
                    ) {
                        Text(
                            text = "Reset Defaults",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.colors.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .remoteNavigationTarget(shape = CircleShape).clickable(onClick = onDismissRequest),
                        color = tokens.colors.surfaceElevated,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = tokens.colors.textMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                thickness = tokens.borders.hairline,
                color = tokens.colors.borderSubtle,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable settings list
            LazyColumn(
                modifier = Modifier.remoteNavigationGroup().then(Modifier
                    .weight(1f)
                    .fillMaxWidth()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SettingsGroup(isTablet = false) {
                        SettingsSwitchRow(
                            title = "CodeineXO Aura",
                            description = "Dynamic chromatic aurora ambient lighting",
                            checked = state.auraBackgroundEnabled,
                            isTablet = false,
                            onCheckedChange = { onStateChange(state.copy(auraBackgroundEnabled = it)) },
                        )
                        SettingsGroupDivider(isTablet = false)
                        SettingsSwitchRow(
                            title = "Scroll Poster Layout",
                            description = "Horizontal edge-to-edge poster rows on homescreen",
                            checked = state.scrollPostersLayoutEnabled,
                            isTablet = false,
                            onCheckedChange = { onStateChange(state.copy(scrollPostersLayoutEnabled = it)) },
                        )
                        SettingsGroupDivider(isTablet = false)
                        PresetSegmentRow<PlayerUiMode>(
                            title = "Player UI Mode",
                            description = "Player interface theme and layout",
                            options = listOf(
                                PlayerUiMode.CODEINE_XO to "CodeineXO UI",
                                PlayerUiMode.OFFICIAL to "Classic",
                            ),
                            selected = state.playerUiMode,
                            onSelect = { onStateChange(state.copy(playerUiMode = it)) },
                        )
                        SettingsGroupDivider(isTablet = false)
                        SettingsSwitchRow(
                            title = "Seekbar While Seeking",
                            description = "Show fluid seekbar bar overlay during fast-forward/rewind",
                            checked = state.showSeekbarWhileSeeking,
                            isTablet = false,
                            onCheckedChange = { onStateChange(state.copy(showSeekbarWhileSeeking = it)) },
                        )
                    }
                }

                item {
                    SettingsGroup(isTablet = false) {
                        PresetSegmentRow<AnimeUpscalerOption>(
                            title = "Anime Upscaler",
                            description = "High quality scaling for anime streams",
                            options = listOf(
                                AnimeUpscalerOption.AUTO to "Anime Only",
                                AnimeUpscalerOption.ALWAYS_ON to "All Videos",
                                AnimeUpscalerOption.OFF to "Off",
                            ),
                            selected = state.animeUpscalerOption,
                            onSelect = { onStateChange(state.copy(animeUpscalerOption = it)) },
                        )
                        if (state.animeUpscalerOption != AnimeUpscalerOption.OFF) {
                            SettingsGroupDivider(isTablet = false)
                            PresetSegmentRow<AnimeUpscalerMode>(
                                title = "Upscaler Mode",
                                description = "Processing shader intensity",
                                options = listOf(
                                    AnimeUpscalerMode.FAST to "Fast Mode",
                                    AnimeUpscalerMode.SHARP_DETAIL to "Sharp & Detailed",
                                ),
                                selected = state.animeUpscalerMode,
                                onSelect = { onStateChange(state.copy(animeUpscalerMode = it)) },
                            )
                        }
                        SettingsGroupDivider(isTablet = false)
                        SvpMotionPresetRow(
                            selected = state.svpMotionOption,
                            onSelect = { onStateChange(state.copy(svpMotionOption = it)) },
                        )
                        SettingsGroupDivider(isTablet = false)
                        SettingsSwitchRow(
                            title = "Anime Filler Detection",
                            description = "Highlight and mark filler episodes in episode lists",
                            checked = state.animeFillerDetectionEnabled,
                            isTablet = false,
                            onCheckedChange = { onStateChange(state.copy(animeFillerDetectionEnabled = it)) },
                        )
                    }
                }

                item {
                    SettingsGroup(isTablet = false) {
                        PresetSegmentRow<SubtitleOutlineEffect>(
                            title = "Subtitle Outline Style",
                            description = "Halo effect with 1px width & #FFF6B3 vanilla tint",
                            options = listOf(
                                SubtitleOutlineEffect.OUTLINE_AND_SHADOW to "Halo",
                                SubtitleOutlineEffect.OUTLINE to "Outline",
                                SubtitleOutlineEffect.SOFT_GLOW to "Glow",
                            ),
                            selected = state.subtitleOutlineEffect,
                            onSelect = { onStateChange(state.copy(subtitleOutlineEffect = it)) },
                        )
                        SettingsGroupDivider(isTablet = false)
                        PresetSegmentRow<MetaEpisodeCardStyle>(
                            title = "Episode Cards Style",
                            description = "Detailed thumbnail metadata layout",
                            options = listOf(
                                MetaEpisodeCardStyle.Enhanced to "Enhanced",
                                MetaEpisodeCardStyle.Horizontal to "Horizontal",
                                MetaEpisodeCardStyle.List to "List",
                            ),
                            selected = state.episodeCardStyle,
                            onSelect = { onStateChange(state.copy(episodeCardStyle = it)) },
                        )
                        SettingsGroupDivider(isTablet = false)
                        SettingsSwitchRow(
                            title = "Torrent Stats in Player",
                            description = "Real-time P2P peer count, seeds, and download speed overlay",
                            checked = state.showTorrentStatsOverlay,
                            isTablet = false,
                            onCheckedChange = { onStateChange(state.copy(showTorrentStatsOverlay = it)) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                thickness = tokens.borders.hairline,
                color = tokens.colors.borderSubtle,
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            DialogButtons {
                DialogButton(
                    text = "Cancel",
                    onClick = onDismissRequest,
                    style = DialogButtonStyle.Secondary,
                )
                DialogButton(
                    text = "Apply",
                    onClick = onConfirm,
                    style = DialogButtonStyle.Primary,
                )
            }
        }
    }
}

@Composable
private fun <T> PresetSegmentRow(
    title: String,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (option, label) ->
                val isSelected = option == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .remoteNavigationTarget(shape = RoundedCornerShape(8.dp)).clickable { onSelect(option) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) tokens.colors.accent.copy(alpha = 0.2f) else tokens.colors.surfaceElevated,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else tokens.borders.hairline,
                        color = if (isSelected) tokens.colors.accent else tokens.colors.borderSubtle,
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) tokens.colors.accent else tokens.colors.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SvpMotionPresetRow(
    selected: SvpMotionOption,
    onSelect: (SvpMotionOption) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val svpDownloadState by SvpRuntimeInstaller.state.collectAsStateWithLifecycle()
    val isInstalled = SvpRuntimeInstaller.isInstalled || svpDownloadState is SvpRuntimeDownloadState.Installed

    LaunchedEffect(isInstalled) {
        if (!isInstalled && selected != SvpMotionOption.OFF) {
            onSelect(SvpMotionOption.OFF)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text(
                text = "Motion Interpolation (SVP)",
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (isInstalled) "Smooth 60 FPS frame interpolation" else "Requires SVP runtime setup (~26 MB)",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val isOffSelected = selected == SvpMotionOption.OFF
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .remoteNavigationTarget(shape = RoundedCornerShape(8.dp)).clickable { onSelect(SvpMotionOption.OFF) },
                shape = RoundedCornerShape(8.dp),
                color = if (isOffSelected) tokens.colors.accent.copy(alpha = 0.2f) else tokens.colors.surfaceElevated,
                border = BorderStroke(
                    width = if (isOffSelected) 1.5.dp else tokens.borders.hairline,
                    color = if (isOffSelected) tokens.colors.accent else tokens.colors.borderSubtle,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Off",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOffSelected) tokens.colors.accent else tokens.colors.textSecondary,
                        fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }

            val isAutoSelected = selected == SvpMotionOption.AUTO
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .remoteNavigationTarget(shape = RoundedCornerShape(8.dp)).clickable {
                        if (isInstalled) {
                            onSelect(SvpMotionOption.AUTO)
                        } else {
                            NuvioToastController.show("Download and setup SVP runtime pack first")
                            if (svpDownloadState is SvpRuntimeDownloadState.Idle || svpDownloadState is SvpRuntimeDownloadState.Error) {
                                SvpRuntimeInstaller.startDownload()
                            }
                        }
                    },
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isAutoSelected -> tokens.colors.accent.copy(alpha = 0.2f)
                    !isInstalled -> tokens.colors.surfaceElevated.copy(alpha = 0.5f)
                    else -> tokens.colors.surfaceElevated
                },
                border = BorderStroke(
                    width = if (isAutoSelected) 1.5.dp else tokens.borders.hairline,
                    color = if (isAutoSelected) tokens.colors.accent else tokens.colors.borderSubtle,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isInstalled) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked",
                            tint = tokens.colors.textMuted,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (!isInstalled) "Auto (Setup Required)" else "Auto",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isAutoSelected -> tokens.colors.accent
                            !isInstalled -> tokens.colors.textMuted
                            else -> tokens.colors.textSecondary
                        },
                        fontWeight = if (isAutoSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        if (!isInstalled) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tokens.colors.surfaceElevated.copy(alpha = 0.7f),
                border = BorderStroke(tokens.borders.hairline, tokens.colors.borderSubtle),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "VapourSynth & SVP runtime (~26 MB) is required to enable real-time 60 FPS motion interpolation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )

                    when (val s = svpDownloadState) {
                        is SvpRuntimeDownloadState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { s.progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = tokens.colors.accent,
                                )
                                Text(
                                    text = "Downloading runtime pack... ${(s.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.colors.textSecondary,
                                )
                            }
                        }
                        is SvpRuntimeDownloadState.Extracting -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = tokens.colors.accent,
                                )
                                Text(
                                    text = "Extracting and registering runtime...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.colors.textSecondary,
                                )
                            }
                        }
                        is SvpRuntimeDownloadState.Error -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Error: ${s.message}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                OutlinedButton(
                                    onClick = { SvpRuntimeInstaller.startDownload() },
                                    modifier = (Modifier.fillMaxWidth()).remoteNavigationTarget(shape = tokens.shapes.button),
                                    shape = tokens.shapes.button,
                                ) {
                                    Text("Retry Download", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        else -> {
                            Button(
                                onClick = { SvpRuntimeInstaller.startDownload() },
                                modifier = (Modifier.fillMaxWidth()).remoteNavigationTarget(shape = tokens.shapes.button),
                                shape = tokens.shapes.button,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = tokens.colors.accent,
                                    contentColor = tokens.colors.onAccent,
                                ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Download & Setup SVP Runtime (~26 MB)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Runtime pack installed — 60 FPS motion interpolation ready",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.accent,
                )
            }
        }
    }
}
