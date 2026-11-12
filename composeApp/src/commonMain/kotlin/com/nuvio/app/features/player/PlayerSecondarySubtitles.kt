package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.autosync.AutoSyncPreferencesRepository
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.servers_subtitle_switch_failed
import org.jetbrains.compose.resources.stringResource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class SecondarySubtitlePreference(
    val kind: String,
    val item: String,
    val id: String? = null,
    val name: String? = null,
    val language: String? = null,
    val forced: Boolean = false,
    val provider: String? = null,
    val url: String? = null,
    val delayMs: Int = 0,
    val manualDelay: Boolean = false,
)

internal data class SecondarySubtitleTarget(
    val track: SubtitleTrack? = null,
    val addon: AddonSubtitle? = null,
)

internal val DefaultSecondarySubtitleStyle = SubtitleStyleState.DEFAULT.copy(textColor = Color.White, bottomOffset = 0)

internal class SecondarySubtitleState {
    var menu by mutableStateOf(false)
    var importingFont = false
    var item by mutableStateOf<String?>(null)
    var preference by mutableStateOf<SecondarySubtitlePreference?>(null)
    var active by mutableStateOf<SecondarySubtitleTarget?>(null)
    var style by mutableStateOf(DefaultSecondarySubtitleStyle)
    var syncState by mutableStateOf(SubtitleAutoSyncUiState())
    var syncedUrl by mutableStateOf<String?>(null)
    var retimedPath by mutableStateOf<String?>(null)
    var cancelAutomaticSync by mutableStateOf(false)
    var selectionRevision by mutableStateOf(0)

    fun resetSync() {
        syncState = SubtitleAutoSyncUiState()
        syncedUrl = null
        retimedPath = null
        cancelAutomaticSync = false
    }
}

internal val PlayerScreenRuntime.secondarySubtitleMenuAddons: List<AddonSubtitle>
    get() {
        val visible = visibleAddonSubtitles
        val active = secondarySubtitles.active?.addon
        return if (active != null && visible.none { it.url == active.url }) listOf(active) + visible else visible
    }

private val PlayerScreenRuntime.secondaryItemKey: String
    get() = "item|${playbackSession.videoId}|${activeSeasonNumber ?: -1}|${activeEpisodeNumber ?: -1}"

private val PlayerScreenRuntime.secondarySeriesKey: String?
    get() = parentMetaId.takeIf { isSeries && it.isNotBlank() }?.let { "series|$it" }

@Composable
internal fun PlayerScreenRuntime.BindSecondarySubtitles() {
    val controller = playerController?.takeIf { it.supportsSecondarySubtitles }
    val item = secondaryItemKey
    val series = secondarySeriesKey
    val state = secondarySubtitles
    val selectionError = stringResource(Res.string.servers_subtitle_switch_failed)
    val autoSyncEnabled by remember {
        AutoSyncPreferencesRepository.ensureLoaded()
        AutoSyncPreferencesRepository.preferredSubtitleAutoSyncOnStart
    }.collectAsStateWithLifecycle()
    val useLibass = playerSettingsUiState.useLibass
    LaunchedEffect(controller, item, profileId) {
        state.item = null
        state.active = null
        state.resetSync()
        state.preference = null
        if (controller == null) return@LaunchedEffect
        state.style = controller.loadSecondarySubtitlePreference("appearance")
            ?.let { decodeSecondaryPreference<SecondarySubtitleAppearance>(it) }
            ?.toStyle() ?: DefaultSecondarySubtitleStyle
        state.preference = (controller.loadSecondarySubtitlePreference(item)
            ?: series?.let(controller::loadSecondarySubtitlePreference))
            ?.let { decodeSecondaryPreference<SecondarySubtitlePreference>(it) }
        state.item = item
    }
    val preference = state.preference.takeIf { state.item == item }
    val target = preference?.resolve(
        item,
        subtitleTracks,
        importedSubtitles + addonSubtitles,
    )?.takeUnless { target ->
        target.track?.index == selectedSubtitleIndex ||
            (target.addon != null && target.addon.url == selectedAddonSubtitle?.url)
    }
    LaunchedEffect(controller, item, activeSourceUrl, preference?.kind, preference?.provider) {
        if (controller != null && preference?.kind == "addon" && target == null) fetchAddonSubtitlesForActiveItem()
    }
    LaunchedEffect(controller, item, activeSourceUrl, initialLoadCompleted, target, state.selectionRevision) {
        if (controller == null || !initialLoadCompleted || target == null) {
            state.active = null
            state.resetSync()
            if (controller != null && initialLoadCompleted) controller.selectSecondarySubtitle(null, null)
            return@LaunchedEffect
        }
        if (controller.selectSecondarySubtitle(target.track?.id, target.addon?.url)) {
            state.resetSync()
            state.active = target
            target.addon?.takeIf { it.language == SubtitleImportedLanguageKey }?.let { imported ->
                if (importedSubtitles.none { it.url == imported.url }) importedSubtitles += imported
            }
            if (preference != null && preference.item != item) {
                val restored = target.toPreference(item, preference.delayMs)
                controller.saveSecondarySubtitlePreference(item, Json.encodeToString(restored))
            }
        } else if (state.menu) {
            playerNotificationMessage = selectionError
            playerNotificationToken++
        }
    }
    val addon = target?.addon?.takeIf { it == state.active?.addon }
    LaunchedEffect(controller, item, activeSourceUrl, addon, autoSyncEnabled, preference?.manualDelay, state.cancelAutomaticSync) {
        if (controller == null || addon == null || !autoSyncEnabled ||
            preference?.manualDelay == true || (preference?.delayMs ?: 0) != 0 ||
            state.cancelAutomaticSync || state.syncedUrl == addon.url ||
            addon.language == SubtitleImportedLanguageKey
        ) return@LaunchedEffect
        val result = controller.prepareSecondarySubtitleAutoSync(addon.url, addon.language) ?: return@LaunchedEffect
        if (result.retimedPath != null && !controller.selectSecondarySubtitle(null, result.retimedPath)) return@LaunchedEffect
        state.syncState = SubtitleAutoSyncUiState()
        state.retimedPath = result.retimedPath
        state.syncedUrl = addon.url
    }
    LaunchedEffect(controller, activeSourceUrl, initialLoadCompleted, state.style, preference?.delayMs, useLibass) {
        if (controller != null && initialLoadCompleted) {
            controller.applySecondarySubtitleStyle(state.style, preference?.delayMs ?: 0, useLibass)
        }
    }
}

internal fun SecondarySubtitlePreference.resolve(
    currentItem: String,
    tracks: List<SubtitleTrack>,
    addons: List<AddonSubtitle>,
): SecondarySubtitleTarget? = when (kind) {
    "embedded" -> {
        val candidates = tracks.filter { it.language.equals(language, ignoreCase = true) && it.isForced == forced }
        val named = candidates.filter { it.label.equals(name, ignoreCase = true) }
        val track = named.firstOrNull { item == currentItem && it.id == id }
            ?: named.singleOrNull()
            ?: candidates.singleOrNull()?.takeIf { named.isEmpty() && !language.isNullOrBlank() }
        track?.let { SecondarySubtitleTarget(track = it) }
    }
    "addon" -> {
        val candidates = addons.filter { it.addonName == provider && it.language == language }
        val addon = candidates.firstOrNull { item == currentItem && it.url == url }
            ?: candidates.firstOrNull { it.id == id && it.display == name }
            ?: candidates.filter { it.display == name }.singleOrNull()
            ?: candidates.singleOrNull()
        addon?.let { SecondarySubtitleTarget(addon = it) }
    }
    "local" -> url?.takeIf { item == currentItem }?.let { path ->
        SecondarySubtitleTarget(
            addon = addons.firstOrNull { it.url == path } ?: AddonSubtitle(
                id = path,
                url = path,
                language = SubtitleImportedLanguageKey,
                display = name.orEmpty(),
            ),
        )
    }
    else -> null
}

private fun SecondarySubtitleTarget?.toPreference(item: String, delayMs: Int): SecondarySubtitlePreference {
    val track = this?.track
    val addon = this?.addon
    return when {
        track != null -> SecondarySubtitlePreference(
            "embedded", item, track.id, track.label, track.language, track.isForced, delayMs = delayMs,
        )
        addon != null -> SecondarySubtitlePreference(
            if (addon.language == SubtitleImportedLanguageKey) "local" else "addon",
            item, addon.id, addon.display, addon.language, provider = addon.addonName, url = addon.url, delayMs = delayMs,
        )
        else -> SecondarySubtitlePreference("off", item, delayMs = delayMs)
    }
}

internal fun PlayerScreenRuntime.chooseSecondarySubtitle(target: SecondarySubtitleTarget?) {
    val item = secondaryItemKey
    if (secondarySubtitles.item != item) return
    val preference = target.toPreference(item, secondarySubtitles.preference?.delayMs ?: 0)
        .copy(manualDelay = secondarySubtitles.preference?.manualDelay == true)
    saveSecondarySubtitleChoice(preference)
    secondarySubtitles.selectionRevision++
    secondarySeriesKey?.let { playerController?.saveSecondarySubtitlePreference(it, Json.encodeToString(preference.copy(delayMs = 0, manualDelay = false))) }
}

internal fun PlayerScreenRuntime.setSecondarySubtitleDelay(delayMs: Int, manualOverride: Boolean = true) {
    val item = secondaryItemKey
    if (secondarySubtitles.item != item) return
    val preference = (secondarySubtitles.preference ?: SecondarySubtitlePreference("off", item)).copy(
        delayMs = delayMs.coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS),
        manualDelay = manualOverride,
    )
    secondarySubtitles.cancelAutomaticSync = true
    secondarySubtitles.syncedUrl = null
    saveSecondarySubtitleChoice(preference)
}

private fun PlayerScreenRuntime.saveSecondarySubtitleChoice(preference: SecondarySubtitlePreference) {
    secondarySubtitles.preference = preference
    playerController?.saveSecondarySubtitlePreference(secondaryItemKey, Json.encodeToString(preference))
}

internal fun PlayerScreenRuntime.setSecondarySubtitleStyle(style: SubtitleStyleState) {
    secondarySubtitles.style = style
    playerController?.saveSecondarySubtitlePreference("appearance", Json.encodeToString(SecondarySubtitleAppearance(style)))
}

private inline fun <reified T> decodeSecondaryPreference(value: String): T? =
    runCatching { Json.decodeFromString<T>(value) }.getOrNull()

@Serializable
private data class SecondarySubtitleAppearance(
    val textColor: Int,
    val backgroundColor: Int,
    val outlineColor: Int,
    val outlineEnabled: Boolean,
    val outlineWidth: Float,
    val outlineEffect: SubtitleOutlineEffect,
    val fontName: String,
    val importedFontId: String?,
    val bold: Boolean,
    val fontSizeSp: Int,
    val bottomOffset: Int,
    val appearance: SubtitleAppearance,
) {
    constructor(style: SubtitleStyleState) : this(
        style.textColor.toArgb(), style.backgroundColor.toArgb(), style.outlineColor.toArgb(),
        style.outlineEnabled, style.outlineWidth, style.outlineEffect, style.fontName, style.importedFontId,
        style.bold, style.fontSizeSp, style.bottomOffset, style.appearance,
    )

    fun toStyle() = SubtitleStyleState(
        textColor = Color(textColor), backgroundColor = Color(backgroundColor), outlineColor = Color(outlineColor),
        outlineEnabled = outlineEnabled, outlineWidth = outlineWidth, outlineEffect = outlineEffect,
        fontName = fontName, importedFontId = importedFontId, bold = bold, fontSizeSp = fontSizeSp,
        bottomOffset = bottomOffset, appearance = appearance,
    )
}
