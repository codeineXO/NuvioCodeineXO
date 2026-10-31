package com.nuvio.app.features.downloads

import com.nuvio.app.core.ui.LocalDesktopSidebarContentInset
import com.nuvio.app.core.ui.remoteNavigationTarget
import com.nuvio.app.core.ui.navigationGlass
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.LocalNavigationGlassState
import com.nuvio.app.core.ui.navigationGlassSource
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.home.components.HomeInteractiveGradientBackground
import com.nuvio.app.isDesktop
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.i18n.localizedByteUnit
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioToastController
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenDownload: (DownloadItem) -> Unit,
    initialShowId: String? = null,
    onNavigateToShow: ((showId: String, title: String) -> Unit)? = null,
    onBackFromShow: (() -> Unit)? = null,
) {
    val uiState by remember {
        DownloadsRepository.ensureLoaded()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.completedItems.size) {
        DownloadsRepository.markCompletedSeen()
    }

    var selectedShowId by rememberSaveable(initialShowId) { mutableStateOf(initialShowId) }
    var downloadPendingDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
    val openDownloadsDirectoryFailedText = stringResource(Res.string.downloads_open_directory_failed)

    val completedEpisodes = remember(uiState.items) {
        uiState.completedItems
            .filter { it.isEpisode }
            .sortedForSeriesDownloads()
    }

    val selectedShowTitle = remember(selectedShowId, completedEpisodes) {
        selectedShowId?.let { showId ->
            completedEpisodes.firstOrNull { it.parentMetaId == showId }?.title
        }
    }

    val auraEnabled by ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()
    val locationBusy by DownloadLocation.busy.collectAsStateWithLifecycle()
    val sidebarInset = LocalDesktopSidebarContentInset.current
    Box(Modifier.fillMaxSize()) {
        if (auraEnabled) {
            HomeInteractiveGradientBackground(Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().navigationGlassSource(zIndex = -1f).background(MaterialTheme.nuvio.colors.background))
        }
        Box(Modifier.fillMaxSize().padding(start = sidebarInset)) {
            NuvioScreen(backgroundColor = Color.Transparent) {
                stickyHeader {
                    NuvioScreenHeader(
                        title = if (selectedShowId == null) {
                            stringResource(Res.string.compose_settings_root_downloads_title)
                        } else {
                            selectedShowTitle ?: stringResource(Res.string.downloads_show_downloads)
                        },
                        onBack = {
                            if (selectedShowId != null) {
                                onBackFromShow?.invoke() ?: run { selectedShowId = null }
                            } else {
                                onBack()
                            }
                        },
                        showFullscreenAction = false,
                        backgroundColor = Color.Transparent,
                        backButtonBackgroundOnly = true,
                        actions = {
                            if (locationBusy) {
                                Text(stringResource(Res.string.downloads_location_moving), color = MaterialTheme.nuvio.colors.textSecondary)
                            }
                            if (isDesktop) {
                                DownloadFolderButton(
                                    label = stringResource(Res.string.downloads_choose_directory),
                                    icon = Icons.Rounded.FolderOpen,
                                    enabled = !locationBusy,
                                    showLabel = true,
                                ) { DownloadLocation.chooseFolder() }
                            }
                            DownloadFolderButton(
                                label = stringResource(Res.string.downloads_open_folder_tooltip),
                                contentDescription = stringResource(Res.string.downloads_open_directory),
                                icon = Icons.Rounded.Folder,
                                enabled = !locationBusy,
                            ) {
                                if (!DownloadsPlatformDownloader.openDownloadsDirectory()) {
                                    NuvioToastController.show(openDownloadsDirectoryFailedText)
                                }
                            }
                        },
                    )
                }

                if (selectedShowId == null) {
                    downloadsRootContent(
                        uiState = uiState,
                        onOpenDownload = onOpenDownload,
                        onOpenShow = { showId, title ->
                            onNavigateToShow?.invoke(showId, title) ?: run { selectedShowId = showId }
                        },
                        onDeleteDownload = { downloadPendingDeletionId = it },
                    )
                } else {
                    downloadsShowContent(
                        showId = selectedShowId.orEmpty(),
                        episodes = completedEpisodes,
                        onOpenDownload = onOpenDownload,
                        onDeleteDownload = { downloadPendingDeletionId = it },
                    )
                }
            }
        }
    }

    val pendingDeletionId = downloadPendingDeletionId
    if (pendingDeletionId != null) {
        NuvioStatusModal(
            title = stringResource(Res.string.action_delete_confirm_title),
            message = stringResource(Res.string.action_delete_confirm_message),
            isVisible = true,
            destructive = true,
            confirmText = stringResource(Res.string.action_yes),
            dismissText = stringResource(Res.string.action_no),
            onConfirm = {
                DownloadsRepository.cancelDownload(pendingDeletionId)
                downloadPendingDeletionId = null
            },
            onDismiss = { downloadPendingDeletionId = null },
        )
    }
}

private fun LazyListScope.downloadsRootContent(
    uiState: DownloadsUiState,
    onOpenDownload: (DownloadItem) -> Unit,
    onOpenShow: (showId: String, title: String) -> Unit,
    onDeleteDownload: (String) -> Unit,
) {
    val activeItems = uiState.activeItems
    val completedMovies = uiState.completedItems.filterNot(DownloadItem::isEpisode)
    val completedShows = uiState.completedItems
        .filter(DownloadItem::isEpisode)
        .groupBy { it.parentMetaId }
        .mapNotNull { (_, episodes) ->
            episodes.firstOrNull()?.let { first ->
                first to episodes
            }
        }
        .sortedBy { (item, _) -> item.title.lowercase() }

    if (activeItems.isNotEmpty()) {
        item {
            SectionTitle(stringResource(Res.string.downloads_section_active))
        }
        items(
            items = activeItems,
            key = { it.id },
        ) { item ->
            DownloadRow(
                item = item,
                onOpen = { onOpenDownload(item) },
                onPause = { DownloadsRepository.pauseDownload(item.id) },
                onResume = { DownloadsRepository.resumeDownload(item.id) },
                onRetry = { DownloadsRepository.retryDownload(item.id) },
                onDelete = { onDeleteDownload(item.id) },
            )
        }
    }

    if (completedMovies.isNotEmpty()) {
        item {
            SectionTitle(stringResource(Res.string.downloads_section_movies))
        }
        items(
            items = completedMovies,
            key = { it.id },
        ) { item ->
            DownloadRow(
                item = item,
                onOpen = { onOpenDownload(item) },
                onPause = { DownloadsRepository.pauseDownload(item.id) },
                onResume = { DownloadsRepository.resumeDownload(item.id) },
                onRetry = { DownloadsRepository.retryDownload(item.id) },
                onDelete = { onDeleteDownload(item.id) },
            )
        }
    }

    if (completedShows.isNotEmpty()) {
        item {
            SectionTitle(stringResource(Res.string.downloads_section_shows))
        }
        items(
            items = completedShows,
            key = { (item, _) -> item.parentMetaId },
        ) { (item, episodes) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .navigationGlass(shape = MaterialTheme.shapes.medium, panel = true, backgroundOnly = true)
                    .remoteNavigationTarget(shape = MaterialTheme.shapes.medium).clickable { onOpenShow(item.parentMetaId, item.title) },
                shape = MaterialTheme.shapes.medium,
                color = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(Res.string.downloads_episode_count, episodes.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (uiState.items.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.downloads_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun LazyListScope.downloadsShowContent(
    showId: String,
    episodes: List<DownloadItem>,
    onOpenDownload: (DownloadItem) -> Unit,
    onDeleteDownload: (String) -> Unit,
) {
    val showEpisodes = episodes
        .filter { it.parentMetaId == showId }
        .sortedForSeriesDownloads()

    val seasons = showEpisodes
        .groupBy { it.seasonNumber ?: 0 }
        .toList()
        .sortedWith(
            compareBy<Pair<Int, List<DownloadItem>>> { (season, _) ->
                if (season == 0) 0 else 1
            }.thenBy { (season, _) -> if (season == 0) 0 else season },
        )

    if (seasons.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.downloads_empty_episodes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    seasons.forEach { (seasonNumber, entries) ->
        item {
            SectionTitle(
                if (seasonNumber == 0) {
                    stringResource(Res.string.episodes_specials)
                } else {
                    stringResource(Res.string.episodes_season, seasonNumber)
                },
            )
        }

        val sortedEpisodes = entries.sortedForSeriesDownloads()

        items(
            items = sortedEpisodes,
            key = { it.id },
        ) { item ->
            DownloadRow(
                item = item,
                onOpen = { onOpenDownload(item) },
                onPause = { DownloadsRepository.pauseDownload(item.id) },
                onResume = { DownloadsRepository.resumeDownload(item.id) },
                onRetry = { DownloadsRepository.retryDownload(item.id) },
                onDelete = { onDeleteDownload(item.id) },
            )
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val locationBusy by DownloadLocation.busy.collectAsStateWithLifecycle()
    val displayTitle = item.displayTitle()
    val actionColors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.nuvio.colors.textPrimary)
    val displaySubtitle = downloadDisplaySubtitle(
        item = item,
        displayTitle = displayTitle,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .navigationGlass(shape = MaterialTheme.shapes.medium, panel = true, backgroundOnly = true)
            .remoteNavigationTarget(shape = MaterialTheme.shapes.medium).clickable(enabled = item.isPlayable && !locationBusy, onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = displaySubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusText(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (item.status) {
                        DownloadStatus.Downloading -> {
                            IconButton(onClick = onPause, enabled = !locationBusy, colors = actionColors, modifier = Modifier.navigationGlass(shape = CircleShape, backgroundOnly = true).remoteNavigationTarget(shape = CircleShape)) {
                                Icon(
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = stringResource(Res.string.compose_action_pause),
                                )
                            }
                        }
                        DownloadStatus.Paused -> {
                            IconButton(onClick = onResume, enabled = !locationBusy, colors = actionColors, modifier = Modifier.navigationGlass(shape = CircleShape, backgroundOnly = true).remoteNavigationTarget(shape = CircleShape)) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(Res.string.action_resume),
                                )
                            }
                        }
                        DownloadStatus.Failed -> {
                            IconButton(onClick = onRetry, enabled = !locationBusy, colors = actionColors, modifier = Modifier.navigationGlass(shape = CircleShape, backgroundOnly = true).remoteNavigationTarget(shape = CircleShape)) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(Res.string.action_retry),
                                )
                            }
                        }
                        DownloadStatus.Completed -> {
                            IconButton(onClick = onOpen, enabled = !locationBusy, colors = actionColors, modifier = Modifier.navigationGlass(shape = CircleShape, backgroundOnly = true).remoteNavigationTarget(shape = CircleShape)) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(Res.string.action_play),
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDelete, enabled = !locationBusy, colors = actionColors, modifier = Modifier.navigationGlass(shape = CircleShape, backgroundOnly = true).remoteNavigationTarget(shape = CircleShape)) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(Res.string.action_delete),
                        )
                    }
                }
            }

            if (item.status == DownloadStatus.Downloading) {
                if (item.totalBytes != null && item.totalBytes > 0L) {
                    LinearProgressIndicator(
                        progress = item.progressFraction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun DownloadItem.displayTitle(): String =
    if (isEpisode) {
        episodeTitle?.trim()?.takeIf { it.isNotBlank() } ?: title
    } else {
        title
    }

@Composable
private fun downloadDisplaySubtitle(
    item: DownloadItem,
    displayTitle: String,
): String {
    val seasonNumber = item.seasonNumber
    val episodeNumber = item.episodeNumber
    if (seasonNumber == null || episodeNumber == null) {
        return item.displaySubtitle
    }

    val episodeCode = stringResource(
        Res.string.compose_player_episode_code_full,
        seasonNumber,
        episodeNumber,
    )
    return listOf(
        episodeCode,
        item.episodeTitle?.trim().orEmpty().takeIf { it.isNotBlank() && it != displayTitle },
        item.title.trim().takeIf { it.isNotBlank() && it != displayTitle },
    ).filterNotNull().joinToString(" • ")
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadFolderButton(
    label: String,
    contentDescription: String = label,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    showLabel: Boolean = false,
    onClick: () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()
    val defaultPosition = TooltipDefaults.rememberPlainTooltipPositionProvider()
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val positionProvider = remember(defaultPosition, margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val preferred = defaultPosition.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)
                val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
                val insetX = margin.coerceAtMost(maxX / 2)
                val insetY = margin.coerceAtMost(maxY / 2)
                return IntOffset(
                    preferred.x.coerceIn(insetX, maxX - insetX),
                    preferred.y.coerceIn(insetY, maxY - insetY),
                )
            }
        }
    }
    val shape = if (showLabel) MaterialTheme.shapes.medium else CircleShape
    val buttonModifier = Modifier.navigationGlass(shape = shape, backgroundOnly = true)
        .onFocusChanged { focus ->
            if (focus.isFocused) scope.launch { tooltipState.show() }
            else tooltipState.dismiss()
        }
        .remoteNavigationTarget(shape = shape)
    TooltipBox(
        positionProvider = positionProvider,
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltipState,
        focusable = false,
    ) {
        if (showLabel) {
            TextButton(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                shape = shape,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.nuvio.colors.textPrimary),
            ) {
                Text(label)
            }
        } else {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.nuvio.colors.textPrimary),
            ) {
                Icon(icon, contentDescription = contentDescription)
            }
        }
    }
}

@Composable
private fun statusText(item: DownloadItem): String {
    val size = if (item.totalBytes != null && item.totalBytes > 0L) {
        "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
    } else {
        formatBytes(item.downloadedBytes)
    }

    return when (item.status) {
        DownloadStatus.Downloading -> stringResource(Res.string.downloads_status_downloading, size)
        DownloadStatus.Paused -> stringResource(Res.string.downloads_status_paused, size)
        DownloadStatus.Completed -> stringResource(
            Res.string.downloads_status_completed,
            formatBytes(item.totalBytes ?: item.downloadedBytes),
        )
        DownloadStatus.Failed -> item.errorMessage ?: stringResource(Res.string.downloads_status_failed)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 ${localizedByteUnit("B")}"
    val kib = 1024.0
    val mib = kib * 1024.0
    val gib = mib * 1024.0
    val value = bytes.toDouble()
    return when {
        value >= gib -> "${((value / gib) * 10.0).toInt() / 10.0} ${localizedByteUnit("GB")}"
        value >= mib -> "${((value / mib) * 10.0).toInt() / 10.0} ${localizedByteUnit("MB")}"
        value >= kib -> "${((value / kib) * 10.0).toInt() / 10.0} ${localizedByteUnit("KB")}"
        else -> "$bytes ${localizedByteUnit("B")}"
    }
}
