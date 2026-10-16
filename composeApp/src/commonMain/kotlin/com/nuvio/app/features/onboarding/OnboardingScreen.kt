package com.nuvio.app.features.onboarding

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
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.nuvio.app.features.player.SubtitleOutlineEffect
import com.nuvio.app.features.player.SvpMotionOption
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
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.background),
    ) {
        HomeInteractiveGradientBackground(modifier = Modifier.fillMaxSize())

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
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (isReviewMode && onBack != null) {
                    NuvioBackButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp),
                        shape = tokens.shapes.compactCard,
                        containerColor = tokens.colors.surface,
                        contentColor = tokens.colors.textPrimary,
                        buttonSize = NuvioTokens.Space.s36,
                        iconSize = tokens.icons.md,
                    )
                } else if (currentStep > 0) {
                    NuvioBackButton(
                        onClick = { currentStep-- },
                        modifier = Modifier.size(36.dp),
                        shape = tokens.shapes.compactCard,
                        containerColor = tokens.colors.surface,
                        contentColor = tokens.colors.textPrimary,
                        buttonSize = NuvioTokens.Space.s36,
                        iconSize = tokens.icons.md,
                    )
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
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
                            .clickable(onClick = onBack)
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
                    Spacer(modifier = Modifier.size(36.dp))
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
                                presetApplied = presetApplied,
                                onOpenReviewDialog = { showReviewDialog = true },
                                onApplyDirectly = {
                                    applyRecommendedPreset(presetState)
                                    presetApplied = true
                                    NuvioToastController.show("Recommended CodeineXO setup applied")
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

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "An ultra-refined media experience loaded with custom player UI, anime upscaling shaders, dynamic aura lighting, and smart library tools.",
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Feature Highlights
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FeatureHighlightRow(
                icon = Icons.Rounded.PlayArrow,
                title = "CodeineXO Player UI",
                subtitle = "Precision seekbar overlay, torrent stats HUD, and gesture scrubbing.",
            )
            FeatureHighlightRow(
                icon = Icons.Rounded.Tune,
                title = "Anime Upscaler & Filler Skip",
                subtitle = "Hardware-accelerated anime shader upscaling and filler episode flags.",
            )
            FeatureHighlightRow(
                icon = Icons.Rounded.Palette,
                title = "Dynamic Ambient Aura",
                subtitle = "Immersive multi-layered aurora ambient backlight tailored to your desktop.",
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
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
    presetApplied: Boolean,
    onOpenReviewDialog: () -> Unit,
    onApplyDirectly: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio

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
                .clickable(onClick = onOpenReviewDialog),
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
                        "Motion (SVP) Off",
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
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
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
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
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

        Spacer(modifier = Modifier.height(32.dp))

        // Navigation actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(0.4f)
                    .heightIn(min = 48.dp),
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
                modifier = Modifier
                    .weight(0.6f)
                    .heightIn(min = 48.dp),
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
            FeatureHighlightRow(
                icon = Icons.Rounded.Keyboard,
                title = "Shortcuts & Playback Controls",
                subtitle = "Use Space to pause/resume, Left/Right arrows to seek, and scroll wheel for volume.",
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(0.4f)
                    .heightIn(min = 50.dp),
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
                modifier = Modifier
                    .weight(0.6f)
                    .heightIn(min = 50.dp),
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

                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismissRequest),
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

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                thickness = tokens.borders.hairline,
                color = tokens.colors.borderSubtle,
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable settings list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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
                        PresetSegmentRow<SvpMotionOption>(
                            title = "Motion Interpolation (SVP)",
                            description = "Smooth frame interpolation",
                            options = listOf(
                                SvpMotionOption.OFF to "Off",
                                SvpMotionOption.AUTO to "Auto",
                            ),
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
                    text = "Confirm & Apply Preset",
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
                        .clickable { onSelect(option) },
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
