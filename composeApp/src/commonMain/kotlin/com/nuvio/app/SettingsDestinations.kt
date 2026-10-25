package com.nuvio.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.collection.CollectionEditorPage
import com.nuvio.app.features.collection.CollectionEditorScreen
import com.nuvio.app.features.collection.CollectionFolderBrowserScreen
import com.nuvio.app.features.collection.CollectionManagementScreen
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.FolderDetailRepository
import com.nuvio.app.features.collection.FolderDetailScreen
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.settings.SettingsScreen
import com.nuvio.app.navigation.AppRoute
import com.nuvio.app.navigation.CollectionEditorPageRoute
import com.nuvio.app.navigation.CollectionEditorRoute
import com.nuvio.app.navigation.CollectionsRoute
import com.nuvio.app.navigation.CollectionFoldersRoute
import com.nuvio.app.navigation.FolderDetailRoute
import com.nuvio.app.navigation.NuvioNavigator
import com.nuvio.app.navigation.OnboardingRoute
import com.nuvio.app.navigation.PartyRoute
import com.nuvio.app.navigation.SettingsPageRoute
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.party_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsDestination(
    route: AppRoute,
    navController: NuvioNavigator,
    content: @Composable (onBack: () -> Unit) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    PlatformBackHandler(enabled = true, onBack = onBack)
    content(onBack)
}

@Composable
internal fun SettingsRootDestination(
    route: SettingsPageRoute,
    navController: NuvioNavigator,
    useNativeNavigation: Boolean,
    collectionsTitle: String,
    onCheckForUpdates: (() -> Unit)?,
    onTestUpdateBanner: (() -> Unit)?,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    val watchPartyTitle = stringResource(Res.string.party_title)
    SettingsScreen(
        modifier = Modifier.fillMaxSize(),
        initialPageName = route.pageName,
        requestedPageName = route.pageName,
        onRequestedPageConsumed = {},
        rootActionsEnabled = false,
        onCollectionsClick = {
            navController.navigate(CollectionsRoute(collectionsTitle))
        },
        onWatchPartyClick = {
            navController.navigate(PartyRoute(title = watchPartyTitle))
        },
        onCheckForUpdatesClick = onCheckForUpdates,
        onTestUpdateBannerClick = onTestUpdateBanner,
        onOnboardingClick = {
            navController.navigate(OnboardingRoute(isReviewMode = true))
        },
    )
}

@Composable
internal fun CollectionsDestination(
    route: CollectionsRoute,
    navController: NuvioNavigator,
    newCollectionTitle: String,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    CollectionManagementScreen(
        onBack = onBack,
        onNavigateToEditor = { collectionId ->
            val editorTitle = collectionId
                ?.let { id ->
                    CollectionRepository.collections.value.firstOrNull { it.id == id }?.title
                }
                .orEmpty()
            navController.navigate(
                CollectionEditorRoute(
                    collectionId = collectionId,
                    title = editorTitle.ifBlank { newCollectionTitle },
                ),
            )
        },
    )
}

@Composable
internal fun CollectionFoldersDestination(
    route: CollectionFoldersRoute,
    navController: NuvioNavigator,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    CollectionFolderBrowserScreen(
        collectionId = route.collectionId,
        title = route.title,
        onBack = onBack,
        onFolderClick = { collectionId, folderId ->
            val folderTitle = CollectionRepository.collections.value
                .firstOrNull { it.id == collectionId }
                ?.folders
                ?.firstOrNull { it.id == folderId }
                ?.title
                .orEmpty()
            navController.navigate(
                FolderDetailRoute(
                    collectionId = collectionId,
                    folderId = folderId,
                    title = folderTitle.ifBlank { route.title },
                ),
            )
        },
    )
}

@Composable
internal fun CollectionEditorDestination(
    route: CollectionEditorRoute,
    navController: NuvioNavigator,
    useNativeNavigation: Boolean,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    CollectionEditorScreen(
        collectionId = route.collectionId,
        onBack = onBack,
        initialPage = if (useNativeNavigation) CollectionEditorPage.Root else null,
        onNavigateToPage = if (useNativeNavigation) {
            { page, title ->
                navController.navigate(
                    CollectionEditorPageRoute(
                        collectionId = route.collectionId,
                        pageName = page.name,
                        title = title,
                    ),
                )
            }
        } else {
            null
        },
    )
}

@Composable
internal fun CollectionEditorPageDestination(
    route: CollectionEditorPageRoute,
    navController: NuvioNavigator,
) {
    val page = remember(route.pageName) {
        runCatching { CollectionEditorPage.valueOf(route.pageName) }.getOrNull()
    }
    val onBack = rememberGuardedPopBackStack(navController, route)
    if (page == null || page == CollectionEditorPage.Root) {
        LaunchedEffect(route) { onBack() }
        return
    }
    CollectionEditorScreen(
        collectionId = route.collectionId,
        initialPage = page,
        initializeRepository = false,
        onBack = onBack,
        onNavigateToPage = { nextPage, title ->
            navController.navigate(
                CollectionEditorPageRoute(
                    collectionId = route.collectionId,
                    pageName = nextPage.name,
                    title = title,
                ),
            )
        },
    )
}

@Composable
internal fun FolderDestination(
    route: FolderDetailRoute,
    navController: NuvioNavigator,
    onCatalogClick: (HomeCatalogSection) -> Unit,
    onPosterLongClick: ((com.nuvio.app.features.home.MetaPreview) -> Unit)? = null,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    LaunchedEffect(route.collectionId, route.folderId) {
        FolderDetailRepository.initialize(route.collectionId, route.folderId)
    }
    FolderDetailScreen(
        onBack = onBack,
        onCatalogClick = onCatalogClick,
        onPosterClick = rememberOpenMeta(navController),
        onPosterLongClick = onPosterLongClick,
    )
}
