package com.nuvio.app.features.player

internal val PlayerScreenRuntime.contentLanguage: String?
    get() {
        val metadata = listOfNotNull(metaUiState.meta, playerMeta).filter {
            it.id == parentMetaId && it.type == parentMetaType
        }
        return metadata.firstNotNullOfOrNull { normalizeLanguageCode(it.language) }
            ?: normalizeLanguageCode(args.contentLanguage)
            ?: metadata.firstNotNullOfOrNull { countryToLanguageCode(it.country) }
    }

internal val PlayerScreenRuntime.preferredAudioLanguageTargets: List<String>
    get() = resolvePreferredAudioLanguageTargets(
        preferredAudioLanguage = playerSettingsUiState.preferredAudioLanguage,
        secondaryPreferredAudioLanguage = playerSettingsUiState.secondaryPreferredAudioLanguage,
        deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
        contentOriginalLanguage = contentLanguage,
    )

internal data class AppliedAudioPreferences(
    val controller: PlayerEngineController,
    val languages: List<String>,
    val tracks: List<AudioTrack>,
)

/**
 * "Original" resolves through the content's metadata, which can arrive after playback starts.
 * Until then the targets are only the device-language fallback, so applying them before the
 * player has tracks would open that language and switch away once the original is known.
 */
private val PlayerScreenRuntime.awaitingOriginalAudioLanguage: Boolean
    get() = normalizeLanguageCode(playerSettingsUiState.preferredAudioLanguage) == AudioLanguageOption.ORIGINAL &&
        contentLanguage == null

internal fun PlayerScreenRuntime.applyPreferredAudioTrack(targets: List<String>) {
    if (isUserExplicitAudioSelection) return
    if (audioTracks.isEmpty()) {
        // Before tracks exist this only primes the player's startup language. The title's saved
        // audio choice is restored once tracks load and overrides the preference, so prime that
        // instead; otherwise the player opens one track and switches right after the first frame.
        val savedLanguage = PlayerTrackPreferenceStorage.load(parentMetaId)
            ?.audioLanguage
            ?.let(::normalizeLanguageCode)
        if (savedLanguage != null) {
            playerController?.applyAudioLanguagePreferences(listOf(savedLanguage))
            return
        }
        // Leave the stream's default track in place rather than priming a fallback that may be replaced.
        if (awaitingOriginalAudioLanguage) return
    }
    val controller = playerController ?: return
    val preferences = AppliedAudioPreferences(
        controller = controller,
        languages = targets,
        tracks = audioTracks.map { it.copy(isSelected = false) },
    )
    if (appliedAudioPreferences != preferences) {
        controller.applyAudioLanguagePreferences(targets)
        appliedAudioPreferences = preferences
        preferredAudioSelectionApplied = false
    } else {
        preferredAudioSelectionApplied = true
    }
}

internal fun PlayerScreenRuntime.restorePersistedAudioPreference(preference: PersistedPlayerTrackPreference) {
    if (audioTracks.isEmpty()) return
    val restoredIndex = findPersistedAudioTrackIndex(audioTracks, preference)
    if (restoredIndex < 0) return
    playerController?.selectAudioTrack(restoredIndex)
    selectedAudioIndex = restoredIndex
    isUserExplicitAudioSelection = true
    preferredAudioSelectionApplied = true
}

internal fun PlayerScreenRuntime.refreshAudioTracksIfChanged() {
    if (playbackSnapshot.isLoading) return
    val controller = playerController ?: return
    if (!preferredAudioSelectionApplied) {
        refreshTracks()
        return
    }
    val tracks = controller.getAudioTracks()
    // The engine caches its track list, so the same instance means nothing changed.
    if (tracks !== audioTracks && tracks != audioTracks) {
        refreshTracks()
    }
}
