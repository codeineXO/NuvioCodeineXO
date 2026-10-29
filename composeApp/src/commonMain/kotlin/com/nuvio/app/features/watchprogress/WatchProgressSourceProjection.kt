package com.nuvio.app.features.watchprogress

import com.nuvio.app.features.tracking.WatchProgressSource

internal fun projectWatchProgressSourceEntries(
    source: WatchProgressSource,
    nuvioEntries: Collection<WatchProgressEntry>,
    providerEntries: Collection<WatchProgressEntry>,
): List<WatchProgressEntry> = if (source.providerId == null) {
    nuvioEntries.toList()
} else {
    val savedMetadata = nuvioEntries.groupBy { it.parentMetaId to it.parentMetaType }
        .mapValues { (identity, entries) -> entries.savedMetadataForContent(identity.first, identity.second) }
    providerEntries.map { entry ->
        val snapshot = entry.metadataSnapshot ?: savedMetadata[entry.parentMetaId to entry.parentMetaType]
        if (snapshot == entry.metadataSnapshot) entry else entry.copy(metadataSnapshot = snapshot)
    }
}
