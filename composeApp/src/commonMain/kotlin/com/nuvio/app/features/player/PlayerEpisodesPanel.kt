package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import com.nuvio.app.core.ui.NuvioAnimatedWatchedBadge
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamsUiState
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import com.nuvio.app.features.watching.application.WatchingState
import androidx.compose.foundation.Image
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.details.EpisodeRatingsVisibility
import com.nuvio.app.features.details.ImdbEpisodeRatingsRepository
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaScreenSettingsRepository
import com.nuvio.app.features.tmdb.TmdbService
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_back
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.compose_action_reload
import nuvio.composeapp.generated.resources.compose_player_episode_code_episode_only
import nuvio.composeapp.generated.resources.compose_player_episode_code_full
import nuvio.composeapp.generated.resources.compose_player_no_episodes_available
import nuvio.composeapp.generated.resources.compose_player_panel_episodes
import nuvio.composeapp.generated.resources.compose_player_panel_streams
import nuvio.composeapp.generated.resources.compose_player_playing
import nuvio.composeapp.generated.resources.episodes_season
import nuvio.composeapp.generated.resources.episodes_specials
import nuvio.composeapp.generated.resources.rating_imdb
import nuvio.composeapp.generated.resources.source_imdb
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun PlayerEpisodesPanel(
    visible: Boolean,
    episodes: List<MetaVideo>,
    parentMetaType: String,
    parentMetaId: String,
    currentSeason: Int?,
    currentEpisode: Int?,
    progressByVideoId: Map<String, WatchProgressEntry>,
    watchedKeys: Set<String>,
    blurUnwatchedEpisodes: Boolean,
    episodeStreamsState: EpisodeStreamsPanelState,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (MetaVideo) -> Unit,
    onEpisodeStreamFilterSelected: (String?) -> Unit,
    onEpisodeStreamSelected: (StreamItem, MetaVideo) -> Unit,
    onBackToEpisodes: () -> Unit,
    onReloadEpisodeStreams: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    episodeRatings: Map<Pair<Int, Int>, Double> = emptyMap(),
) {
    PlayerSidePanel(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            PlayerPanelHeader(
                title = if (episodeStreamsState.showStreams) {
                    stringResource(Res.string.compose_player_panel_streams)
                } else {
                    stringResource(Res.string.compose_player_panel_episodes)
                },
            ) {
                PlayerDialogButton(
                    label = stringResource(Res.string.action_close),
                    onClick = onDismiss,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (episodeStreamsState.showStreams) {
                EpisodeStreamsPanelContent(
                    state = episodeStreamsState,
                    onFilterSelected = onEpisodeStreamFilterSelected,
                    onStreamSelected = onEpisodeStreamSelected,
                    onBack = onBackToEpisodes,
                    onReload = onReloadEpisodeStreams,
                    modifier = Modifier.weight(1f),
                )
            } else {
                EpisodesListPanelContent(
                    episodes = episodes,
                    parentMetaType = parentMetaType,
                    parentMetaId = parentMetaId,
                    currentSeason = currentSeason,
                    currentEpisode = currentEpisode,
                    progressByVideoId = progressByVideoId,
                    watchedKeys = watchedKeys,
                    blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                    onSeasonSelected = onSeasonSelected,
                    onEpisodeSelected = onEpisodeSelected,
                    initialEpisodeRatings = episodeRatings,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

data class EpisodeStreamsPanelState(
    val showStreams: Boolean = false,
    val selectedEpisode: MetaVideo? = null,
    val streamsUiState: StreamsUiState = StreamsUiState(),
)

@Composable
private fun EpisodesListPanelContent(
    episodes: List<MetaVideo>,
    parentMetaType: String,
    parentMetaId: String,
    currentSeason: Int?,
    currentEpisode: Int?,
    progressByVideoId: Map<String, WatchProgressEntry>,
    watchedKeys: Set<String>,
    blurUnwatchedEpisodes: Boolean,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeSelected: (MetaVideo) -> Unit,
    modifier: Modifier = Modifier,
    initialEpisodeRatings: Map<Pair<Int, Int>, Double> = emptyMap(),
) {
    val tokens = MaterialTheme.nuvio
    val metaScreenSettings by MetaScreenSettingsRepository.uiState.collectAsState()
    var episodeImdbRatings by remember(parentMetaId, initialEpisodeRatings) {
        mutableStateOf(initialEpisodeRatings)
    }

    LaunchedEffect(initialEpisodeRatings) {
        if (initialEpisodeRatings.isNotEmpty()) {
            episodeImdbRatings = initialEpisodeRatings
        }
    }

    LaunchedEffect(parentMetaId, episodes, metaScreenSettings.episodeRatingsVisibility) {
        if (!metaScreenSettings.episodeRatingsVisibility.showRatings || parentMetaId.isBlank()) {
            episodeImdbRatings = emptyMap()
            return@LaunchedEffect
        }
        if (initialEpisodeRatings.isNotEmpty()) {
            episodeImdbRatings = initialEpisodeRatings
            return@LaunchedEffect
        }
        val peekedMeta = MetaDetailsRepository.peek(parentMetaType, parentMetaId)
        val imdbId = extractImdbId(parentMetaId)
            ?: extractImdbId(peekedMeta?.imdbId)
            ?: extractImdbId(peekedMeta?.id)
            ?: episodes.firstNotNullOfOrNull { extractImdbId(it.id) }
        val tmdbId = extractTmdbId(parentMetaId)
            ?: extractTmdbId(peekedMeta?.id)
            ?: TmdbService.ensureTmdbId(peekedMeta?.id ?: parentMetaId, parentMetaType, fallbackImdbId = imdbId)?.toIntOrNull()
            ?: TmdbService.ensureTmdbId(imdbId ?: parentMetaId, parentMetaType, fallbackImdbId = peekedMeta?.imdbId)?.toIntOrNull()
        if (imdbId == null && tmdbId == null) {
            episodeImdbRatings = emptyMap()
            return@LaunchedEffect
        }
        episodeImdbRatings = ImdbEpisodeRatingsRepository.getEpisodeRatings(
            imdbId = imdbId,
            tmdbId = tmdbId,
            seasonNumbers = episodes.mapNotNull { it.season }.distinct(),
        )
    }

    val groupedEpisodes = remember(episodes) {
        episodes
            .filter { it.season != null || it.episode != null }
            .groupBy { it.season?.coerceAtLeast(0) ?: 0 }
    }
    val availableSeasons = remember(groupedEpisodes) {
        groupedEpisodes.keys.filter { it > 0 }.sorted() + groupedEpisodes.keys.filter { it == 0 }
    }
    var selectedSeason by remember(currentSeason, availableSeasons) {
        mutableIntStateOf(
            when {
                currentSeason != null && currentSeason in availableSeasons -> currentSeason
                availableSeasons.isNotEmpty() -> availableSeasons.first()
                else -> 1
            },
        )
    }
    val seasonEpisodes = remember(groupedEpisodes, selectedSeason) {
        (groupedEpisodes[selectedSeason] ?: emptyList()).sortedBy { it.episode ?: 0 }
    }
    val seasonListState = rememberLazyListState()
    val episodeListState = rememberLazyListState()
    var positionedSeasonRow by remember(availableSeasons) { mutableStateOf(false) }
    var positionedEpisodeList by remember(selectedSeason) { mutableStateOf(false) }

    LaunchedEffect(selectedSeason, availableSeasons) {
        val index = availableSeasons.indexOf(selectedSeason)
        if (index >= 0) {
            if (positionedSeasonRow) seasonListState.animateScrollToItem(index)
            else {
                seasonListState.scrollToItem(index)
                positionedSeasonRow = true
            }
        }
    }

    LaunchedEffect(selectedSeason, seasonEpisodes, currentSeason, currentEpisode) {
        if (seasonEpisodes.isEmpty()) return@LaunchedEffect
        val currentIndex = if (selectedSeason == currentSeason && currentEpisode != null) {
            seasonEpisodes.indexOfFirst { it.season == currentSeason && it.episode == currentEpisode }
        } else {
            -1
        }
        val targetIndex = currentIndex.takeIf { it >= 0 } ?: 0
        if (positionedEpisodeList) episodeListState.animateScrollToItem(targetIndex)
        else {
            episodeListState.scrollToItem(targetIndex)
            positionedEpisodeList = true
        }
    }

    Column(modifier = modifier) {
        if (availableSeasons.isNotEmpty()) {
            LazyRow(
                state = seasonListState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            ) {
                items(availableSeasons, key = { it }) { season ->
                    EpisodeSeasonChip(
                        label = if (season == 0) {
                            stringResource(Res.string.episodes_specials)
                        } else {
                            stringResource(Res.string.episodes_season, season)
                        },
                        isSelected = selectedSeason == season,
                        onClick = {
                            selectedSeason = season
                            onSeasonSelected(season)
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (seasonEpisodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.compose_player_no_episodes_available),
                    color = tokens.colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                state = episodeListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
            ) {
                itemsIndexed(
                    items = seasonEpisodes,
                    key = { index, episode -> "${episode.season}:${episode.episode}:${episode.id}#$index" },
                ) { _, episode ->
                    val isCurrent = episode.season == currentSeason && episode.episode == currentEpisode
                    val episodeVideoId = buildPlaybackVideoId(
                        parentMetaId = parentMetaId,
                        seasonNumber = episode.season,
                        episodeNumber = episode.episode,
                        fallbackVideoId = episode.id,
                    )
                    val isWatched = progressByVideoId[episodeVideoId]?.isEffectivelyCompleted == true ||
                        WatchingState.isEpisodeWatched(
                            watchedKeys = watchedKeys,
                            metaType = parentMetaType,
                            metaId = parentMetaId,
                            episode = episode,
                        )
                    EpisodeRow(
                        episode = episode,
                        isCurrent = isCurrent,
                        isWatched = isWatched,
                        blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                        episodeRatings = episodeImdbRatings,
                        episodeRatingsVisibility = metaScreenSettings.episodeRatingsVisibility,
                        onClick = { onEpisodeSelected(episode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeSeasonChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val hazeState = LocalPlayerHazeState.current
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (isSelected) {
                    Modifier.background(Color(0xFFF5F5F5))
                } else if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurRadius = 120.dp
                        noiseFactor = 0f
                        backgroundColor = Color.Black.copy(alpha = 0.65f)
                        tints = listOf(HazeTint(Color.Black.copy(alpha = 0.65f)))
                        fallbackTint = HazeTint(Color.Black.copy(alpha = 0.94f))
                    }
                } else {
                    Modifier.background(Color.Black.copy(alpha = 0.88f))
                },
            )
            .border(
                1.dp,
                if (isSelected) Color.Transparent else if (hazeState != null) Color.White.copy(alpha = 0.10f) else tokens.colors.borderDefault,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else tokens.colors.textSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: MetaVideo,
    isCurrent: Boolean,
    isWatched: Boolean,
    blurUnwatchedEpisodes: Boolean,
    episodeRatings: Map<Pair<Int, Int>, Double> = emptyMap(),
    episodeRatingsVisibility: EpisodeRatingsVisibility = EpisodeRatingsVisibility.SHOW_ALL,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val hazeState = LocalPlayerHazeState.current
    val cardShape = RoundedCornerShape(16.dp)
    val shouldBlurArtwork = blurUnwatchedEpisodes && !isWatched
    val playingDescription = stringResource(Res.string.compose_player_playing)
    val episodeCode = when {
        episode.season != null && episode.episode != null -> stringResource(
            Res.string.compose_player_episode_code_full,
            episode.season,
            episode.episode,
        )
        episode.episode != null -> stringResource(
            Res.string.compose_player_episode_code_episode_only,
            episode.episode,
        )
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurRadius = 160.dp
                        noiseFactor = 0f
                        backgroundColor = Color.Black.copy(alpha = 0.68f)
                        tints = listOf(HazeTint(Color.Black.copy(alpha = 0.68f)))
                        fallbackTint = HazeTint(Color.Black.copy(alpha = 0.95f))
                    }
                } else {
                    Modifier.background(Color.Black.copy(alpha = 0.88f))
                },
            )
            .then(
                if (isCurrent) {
                    Modifier.border(width = 2.dp, color = tokens.colors.focusRing, shape = cardShape)
                } else if (hazeState != null) {
                    Modifier.border(width = 1.dp, color = Color.White.copy(alpha = 0.10f), shape = cardShape)
                } else {
                    Modifier
                },
            )
            .semantics {
                if (isCurrent) stateDescription = playingDescription
            }
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.colors.surfacePopover),
        ) {
            episode.thumbnail?.let { thumbnail ->
                AsyncImage(
                    model = thumbnail,
                    contentDescription = episode.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (shouldBlurArtwork) Modifier.blur(NuvioTokens.Space.s18) else Modifier),
                    contentScale = ContentScale.Crop,
                )
            }
            if (episodeCode != null) {
                Text(
                    text = episodeCode,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            NuvioAnimatedWatchedBadge(
                isVisible = isWatched,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = episode.title,
                color = tokens.colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val formattedReleaseDate = episode.released?.takeIf { it.isNotBlank() }?.let { formatReleaseDateForDisplay(it) }
            val rawRating = episode.seasonEpisodeKey()?.let { episodeRatings[it] } ?: episode.rating
            val ratingLabel = remember(rawRating, episodeRatingsVisibility, isWatched) {
                rawRating?.takeIf { it > 0.0 && episodeRatingsVisibility.showRating(isWatched) }
                    ?.let(::formatEpisodeRating)
            }
            if (formattedReleaseDate != null || ratingLabel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (formattedReleaseDate != null) {
                        Text(
                            text = formattedReleaseDate,
                            color = tokens.colors.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (formattedReleaseDate != null && ratingLabel != null) {
                        Text(
                            text = "•",
                            color = tokens.colors.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (ratingLabel != null) {
                        PlayerEpisodeRatingBadge(rating = ratingLabel)
                    }
                }
            }
            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Text(
                    text = overview,
                    color = tokens.colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val imdbRegex = Regex("tt\\d+")

internal fun extractImdbId(value: String?): String? {
    if (value.isNullOrBlank()) return null
    return imdbRegex.find(value)?.value
}

internal fun extractTmdbId(value: String?): Int? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return trimmed
        .takeIf { it.startsWith("tmdb:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.substringBefore(':')
        ?.substringBefore('/')
        ?.toIntOrNull()
}

internal fun MetaVideo.seasonEpisodeKey(): Pair<Int, Int>? {
    val s = season ?: return null
    val e = episode ?: return null
    return s to e
}

internal fun formatEpisodeRating(rating: Double): String {
    val roundedTenths = (rating * 10.0).roundToInt()
    val whole = roundedTenths / 10
    val tenth = (roundedTenths % 10).absoluteValue
    return "$whole.$tenth"
}

@Composable
private fun PlayerEpisodeRatingBadge(
    rating: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (AppFeaturePolicy.imdbRatingLogoEnabled) {
            Image(
                painter = painterResource(Res.drawable.rating_imdb),
                contentDescription = stringResource(Res.string.source_imdb),
                modifier = Modifier
                    .width(24.dp)
                    .height(12.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = stringResource(Res.string.source_imdb),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                ),
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
            )
        }
        Text(
            text = rating,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color(0xFFF5C518),
            maxLines = 1,
        )
    }
}

@Composable
private fun EpisodeStreamsPanelContent(
    state: EpisodeStreamsPanelState,
    onFilterSelected: (String?) -> Unit,
    onStreamSelected: (StreamItem, MetaVideo) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.nuvio
    val episode = state.selectedEpisode ?: return
    val streamsUiState = state.streamsUiState

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerDialogButton(
                label = stringResource(Res.string.action_back),
                onClick = onBack,
            )
            PlayerDialogButton(
                label = stringResource(Res.string.compose_action_reload),
                onClick = onReload,
            )
            Text(
                text = buildString {
                    if (episode.season != null && episode.episode != null) {
                        append(
                            stringResource(
                                Res.string.compose_player_episode_code_full,
                                episode.season,
                                episode.episode,
                            ),
                        )
                    }
                    if (episode.title.isNotBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(episode.title)
                    }
                },
                color = tokens.colors.textSecondary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        PlayerProviderFilterRow(
            streamsUiState = streamsUiState,
            onFilterSelected = onFilterSelected,
        )

        Spacer(Modifier.height(16.dp))

        PlayerStreamList(
            streamsUiState = streamsUiState,
            onStreamSelected = { stream -> onStreamSelected(stream, episode) },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
        )
    }
}
