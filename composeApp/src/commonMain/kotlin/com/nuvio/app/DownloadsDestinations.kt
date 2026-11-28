package com.nuvio.app

import androidx.compose.runtime.Composable
import com.nuvio.app.features.downloads.DownloadItem
import com.nuvio.app.features.downloads.DownloadsScreen
import com.nuvio.app.navigation.DetailRoute
import com.nuvio.app.navigation.DownloadShowRoute
import com.nuvio.app.navigation.NuvioNavigator

@Composable
internal fun DownloadShowDestination(
    route: DownloadShowRoute,
    navController: NuvioNavigator,
    onOpenDownload: (DownloadItem) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    DownloadsScreen(
        onBack = onBack,
        onOpenDownload = onOpenDownload,
        onOpenDetails = navController::openDownloadDetails,
        initialShowId = route.showId,
        onBackFromShow = onBack,
    )
}

internal fun NuvioNavigator.openDownloadDetails(item: DownloadItem) {
    navigate(
        DetailRoute(
            type = item.parentMetaType,
            id = item.parentMetaId,
            title = item.title,
        ),
    )
}
