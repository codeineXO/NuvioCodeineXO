package com.nuvio.app.features.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.core.ui.remoteNavigationGroup
import com.nuvio.app.features.home.components.CollectionFolderCard
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
    val posterCardStyle = rememberPosterCardStyleUiState()
    val basePosterWidthDp = desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp)
    val minGridWidth = if (posterCardStyle.catalogLandscapeModeEnabled) {
        landscapePosterWidth(basePosterWidthDp)
    } else {
        basePosterWidthDp.dp
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NuvioScreenHeader(
            title = collection?.title ?: title,
            onBack = onBack,
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
            LazyVerticalGrid(
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
