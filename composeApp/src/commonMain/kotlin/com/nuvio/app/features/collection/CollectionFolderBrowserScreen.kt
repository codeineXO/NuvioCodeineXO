package com.nuvio.app.features.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import com.nuvio.app.core.ui.NuvioLazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.LocalDesktopSidebarContentInset
import com.nuvio.app.core.ui.NuvioAsyncImage as AsyncImage
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.core.ui.navigationGlassSource
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.remoteNavigationGroup
import com.nuvio.app.features.home.components.CollectionFolderCard
import com.nuvio.app.features.home.components.HomeInteractiveGradientBackground
import com.nuvio.app.features.settings.ThemeSettingsRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.collections_folders_empty
import org.jetbrains.compose.resources.stringResource

@Composable
fun CollectionFolderBrowserScreen(
    collectionId: String,
    title: String,
    onBack: () -> Unit,
    onFolderClick: (collectionId: String, folderId: String) -> Unit,
) {
    val collections by CollectionRepository.collections.collectAsStateWithLifecycle()
    val collection = collections.firstOrNull { it.id == collectionId }
    val auraBackgroundEnabled by ThemeSettingsRepository.auraBackgroundEnabled.collectAsStateWithLifecycle()
    val coverImageUrl = collection?.backdropImageUrl?.takeIf { it.isNotBlank() }
        ?: collection?.folders?.firstNotNullOfOrNull { it.coverImageUrl?.takeIf(String::isNotBlank) }
    val posterCardStyle = rememberPosterCardStyleUiState()
    val basePosterWidthDp = desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp)
    val minGridWidth = if (posterCardStyle.catalogLandscapeModeEnabled) {
        landscapePosterWidth(basePosterWidthDp)
    } else {
        basePosterWidthDp.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (auraBackgroundEnabled || coverImageUrl != null) Modifier else Modifier.background(MaterialTheme.colorScheme.background)
            ),
    ) {
        if (coverImageUrl != null) {
            Box(Modifier.matchParentSize().navigationGlassSource()) {
                AsyncImage(
                    model = coverImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.06f
                            scaleY = 1.06f
                        }
                        .blur(28.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.85f,
                )
            }
        } else if (auraBackgroundEnabled) {
            HomeInteractiveGradientBackground(Modifier.fillMaxSize())
        } else {
            Box(Modifier.matchParentSize().navigationGlassSource(zIndex = -1f).background(MaterialTheme.colorScheme.background))
        }

        val sidebarInset = LocalDesktopSidebarContentInset.current
        Column(modifier = Modifier.fillMaxSize().padding(start = sidebarInset)) {
            NuvioScreenHeader(
                title = collection?.title ?: title,
                onBack = onBack,
                backgroundColor = Color.Transparent,
                backButtonBackgroundOnly = true,
                backButtonKeyboardNavigationEnabled = false,
            )
            val folders = collection?.folders.orEmpty()
            if (folders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.collections_folders_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                NuvioLazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = minGridWidth),
                    modifier = Modifier.fillMaxSize().remoteNavigationGroup(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(
                        items = folders,
                        key = { folder -> "collection_${collectionId}_folder_${folder.id}" },
                    ) { folder ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            CollectionFolderCard(
                                folder = folder,
                                fillMaxWidth = true,
                                onClick = { onFolderClick(collectionId, folder.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
