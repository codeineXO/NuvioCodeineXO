package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioCardDepthSurface
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.NuvioShelfSection
import com.nuvio.app.core.ui.PosterLandscapeAspectRatio
import com.nuvio.app.core.ui.desktopCatalogShelfPosterBaseWidthDp
import com.nuvio.app.core.ui.landscapePosterWidth
import com.nuvio.app.core.ui.nuvioCardDepth
import com.nuvio.app.core.ui.posterCardClickable
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.PosterShape

@Composable
fun HomeCollectionRowSection(
    collection: Collection,
    modifier: Modifier = Modifier,
    sectionPadding: Dp? = null,
    useGutterLayout: Boolean = false,
    animateGifs: Boolean = true,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)? = null,
    onViewAllClick: (() -> Unit)? = null,
) {
    if (collection.folders.isEmpty()) return

    if (sectionPadding != null) {
        HomeCollectionRowSectionContent(
            collection = collection,
            modifier = modifier.fillMaxWidth(),
            sectionPadding = sectionPadding,
            useGutterLayout = useGutterLayout,
            animateGifs = animateGifs,
            onFolderClick = onFolderClick,
            onViewAllClick = onViewAllClick,
        )
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            HomeCollectionRowSectionContent(
                collection = collection,
                modifier = Modifier.fillMaxWidth(),
                sectionPadding = homeSectionHorizontalPaddingForWidth(maxWidth.value),
                useGutterLayout = useGutterLayout,
                animateGifs = animateGifs,
                onFolderClick = onFolderClick,
                onViewAllClick = onViewAllClick,
            )
        }
    }
}

@Composable
private fun HomeCollectionRowSectionContent(
    collection: Collection,
    modifier: Modifier,
    sectionPadding: Dp,
    useGutterLayout: Boolean,
    animateGifs: Boolean,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)?,
    onViewAllClick: (() -> Unit)?,
) {
    NuvioShelfSection(
        title = collection.title,
        entries = collection.folders,
        modifier = modifier,
        headerHorizontalPadding = sectionPadding,
        rowContentPadding = PaddingValues(horizontal = sectionPadding),
        useGutterLayout = useGutterLayout,
        onViewAllClick = onViewAllClick,
        showRowNavigationControls = true,
        key = { folder -> "collection_${collection.id}_folder_${folder.id}" },
    ) { folder ->
        CollectionFolderCard(
            folder = folder,
            animateGifs = animateGifs,
            onClick = onFolderClick?.let { { it(collection.id, folder.id) } },
        )
    }
}

@Composable
internal fun CollectionFolderCard(
    folder: CollectionFolder,
    modifier: Modifier = Modifier,
    animateGifs: Boolean = true,
    onClick: (() -> Unit)? = null,
    fillMaxWidth: Boolean = false,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val basePosterWidthDp = desktopCatalogShelfPosterBaseWidthDp(posterCardStyle.widthDp)
    val isLandscapeMode = posterCardStyle.catalogLandscapeModeEnabled
    val shape = if (isLandscapeMode) PosterShape.Landscape else folder.posterShape
    val cardWidth: Dp
    val aspectRatio: Float

    when (shape) {
        PosterShape.Poster -> {
            cardWidth = basePosterWidthDp.dp
            aspectRatio = 0.675f
        }
        PosterShape.Landscape -> {
            cardWidth = landscapePosterWidth(basePosterWidthDp)
            aspectRatio = PosterLandscapeAspectRatio
        }
        PosterShape.Square -> {
            cardWidth = basePosterWidthDp.dp
            aspectRatio = 1f
        }
    }

    Column(
        modifier = Modifier
            .posterCardClickable(onClick = onClick, onLongClick = null)
            .then(modifier)
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier.width(cardWidth)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val shapeCorner = RoundedCornerShape(posterCardStyle.cornerRadiusDp.dp)
        val imageUrl = collectionFolderCardImageUrl(folder)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .nuvioCardDepth(
                    shape = shapeCorner,
                    surface = NuvioCardDepthSurface.Posters,
                ),
            shape = shapeCorner,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp,
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    !imageUrl.isNullOrBlank() -> {
                        CollectionCardRemoteImage(
                            imageUrl = imageUrl,
                            staticImageUrl = firstNonBlank(folder.coverImageUrl),
                            contentDescription = folder.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            animateIfPossible = animateGifs &&
                                isAnimatedCollectionFolderImage(folder, imageUrl) &&
                                LocalScreenActive.current,
                        )
                    }
                    !folder.coverEmoji.isNullOrBlank() -> {
                        Text(
                            text = folder.coverEmoji,
                            fontSize = 36.sp,
                        )
                    }
                    else -> {
                        Text(
                            text = folder.title.take(2).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

            }
        }

        if (!folder.hideTitle) {
            Text(
                text = folder.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun collectionFolderCardImageUrl(folder: CollectionFolder): String? {
    return if (folder.mobileFocusGifEnabled) {
        firstNonBlank(folder.focusGifUrl, folder.coverImageUrl)
    } else {
        firstNonBlank(folder.coverImageUrl)
    }
}

private fun firstNonBlank(vararg candidates: String?): String? {
    return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
}

private fun isAnimatedCollectionFolderImage(
    folder: CollectionFolder,
    imageUrl: String,
): Boolean {
    val gifUrl = firstNonBlank(folder.focusGifUrl) ?: return false
    return folder.mobileFocusGifEnabled && imageUrl == gifUrl
}
