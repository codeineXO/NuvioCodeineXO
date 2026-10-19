package com.nuvio.app.features.library

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeCatalogRowSection
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import com.nuvio.app.features.home.components.HomeSkeletonRow
import com.nuvio.app.features.servers.ServerCatalog
import com.nuvio.app.features.servers.ServerFailure
import com.nuvio.app.features.servers.ServerLibraryRef
import com.nuvio.app.features.servers.ServerRepository
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.isDesktop
import com.nuvio.app.features.servers.message
import com.nuvio.app.features.servers.serverFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.library_server_load_failed
import nuvio.composeapp.generated.resources.library_servers_empty_message
import nuvio.composeapp.generated.resources.library_servers_empty_title
import org.jetbrains.compose.resources.stringResource

internal data class LibraryServerShelf(
    val ref: ServerLibraryRef,
    val section: HomeCatalogSection? = null,
    val failure: ServerFailure? = null,
)

private val desktopShelfCache = LibraryServerShelvesCache()

// Retain one metadata snapshot outside disposed tab compositions, never decoded artwork.
internal class LibraryServerShelvesCache {
    private val mutex = Mutex()
    private var key: Pair<Int, Int>? = null
    private var shelves: List<LibraryServerShelf>? = null

    suspend fun get(
        profileId: Int,
        revision: Int,
        forceRefresh: Boolean = false,
        load: suspend () -> List<LibraryServerShelf>,
    ): List<LibraryServerShelf> = mutex.withLock {
        val requestKey = profileId to revision
        if (!forceRefresh && key == requestKey) shelves?.let { return@withLock it }
        key = null
        shelves = null
        load().also {
            key = requestKey
            shelves = it
        }
    }
}

internal suspend fun loadServerShelves(forceRefresh: Boolean = false): List<LibraryServerShelf> =
    if (isDesktop) desktopShelfCache.get(
        profileId = ProfileRepository.activeProfileId,
        revision = ServerRepository.uiState.value.revision,
        forceRefresh = forceRefresh,
        load = ::fetchServerShelves,
    ) else fetchServerShelves()

private suspend fun fetchServerShelves(): List<LibraryServerShelf> = coroutineScope {
    ServerCatalog.libraries().map { ref ->
        async {
            try {
                LibraryServerShelf(ref, section = ServerCatalog.librarySection(ref, SERVER_SHELF_LIMIT))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                LibraryServerShelf(ref, failure = error.serverFailure())
            }
        }
    }.awaitAll()
}

internal fun LazyListScope.serverLibraryContent(
    shelves: List<LibraryServerShelf>?,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onCatalogClick: ((HomeCatalogSection) -> Unit)?,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onPosterLongClick: ((MetaPreview) -> Unit)?,
    onRetry: () -> Unit,
) {
    when {
        shelves == null -> items(3) { HomeSkeletonRow(horizontalPadding = 16.dp) }

        shelves.isEmpty() -> item {
            HomeEmptyStateCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = stringResource(Res.string.library_servers_empty_title),
                message = stringResource(Res.string.library_servers_empty_message),
            )
        }

        else -> shelves.forEach { shelf ->
            val section = shelf.section
            if (section != null && section.items.isNotEmpty()) {
                item(key = "server-shelf:${section.key}") {
                    HomeCatalogRowSection(
                        section = section,
                        watchedKeys = watchedKeys,
                        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                        onViewAllClick = onCatalogClick?.takeIf { section.hasMore }?.let { open -> { open(section) } },
                        onPosterClick = onPosterClick,
                        onPosterLongClick = onPosterLongClick,
                    )
                }
            } else if (shelf.failure != null) {
                item(key = "server-shelf-error:${shelf.ref.connection.id}:${shelf.ref.library.id}") {
                    HomeEmptyStateCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = stringResource(Res.string.library_server_load_failed, shelf.ref.title),
                        message = stringResource(shelf.failure.message()),
                        actionLabel = stringResource(Res.string.action_retry),
                        onActionClick = onRetry,
                    )
                }
            }
        }
    }
}

private const val SERVER_SHELF_LIMIT = 18
