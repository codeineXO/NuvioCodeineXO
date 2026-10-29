package com.nuvio.app.features.player

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncFloat
import com.nuvio.app.core.sync.decodeSyncInt
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.decodeSyncStringSet
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncFloat
import com.nuvio.app.core.sync.encodeSyncInt
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.core.sync.encodeSyncStringSet
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object PlayerSettingsStorage : PlaybackBufferPresetStorage {
    private const val playbackBufferPresetKey = DesktopPlaybackBufferPresetStorage.KEY
    private val playbackBufferStorage by lazy { DesktopPlaybackBufferPresetStorage(store) }
    override fun loadPlaybackBufferPreset(): String? = playbackBufferStorage.loadPlaybackBufferPreset()
    override fun savePlaybackBufferPreset(preset: String) = playbackBufferStorage.savePlaybackBufferPreset(preset)
    private const val pendingExternalPlaybackKey = "pending_external_playback"
    private const val playbackBrightnessKey = "playback_brightness"
    private const val useLegacyPlayerLayoutKey = "use_legacy_player_layout"
    private const val playerUiModeKey = "player_ui_mode"
    private const val showSeekbarWhileSeekingKey = "show_seekbar_while_seeking"
    private const val showClockInPlayerKey = "show_clock_in_player"
    private const val seekPreviewEnabledKey = "seek_preview_enabled"
    private const val seekPreviewWhileDraggingKey = "seek_preview_while_dragging"
    private const val seekPreviewWhileHoveringKey = "seek_preview_while_hovering"
    private const val seekPreviewWhileKeyboardSeekingKey = "seek_preview_while_keyboard_seeking"
    private const val seekPreviewThumbnailCountKey = "seek_preview_thumbnail_count"
    private const val seekrApiKeyKey = "seekr_api_key"
    private const val showRemainingTimeKey = "show_remaining_time"
    private const val autoSkipMovieCreditsKey = "auto_skip_movie_credits"
    private const val preloadNextEpisodeSourcesKey = "preload_next_episode_sources"
    private const val exoNativeMemoryEnabledKey = "exo_native_memory_enabled"
    private const val vodCacheEnabledKey = "vod_cache_enabled"
    private const val vodCacheSizeModeKey = "vod_cache_size_mode"
    private const val vodCacheSizeMbKey = "vod_cache_size_mb"
    private const val bufferEngineEnabledKey = "buffer_engine_enabled"
    private const val minBufferMsKey = "min_buffer_ms"
    private const val maxBufferMsKey = "max_buffer_ms"
    private const val bufferForPlaybackMsKey = "buffer_for_playback_ms"
    private const val bufferForPlaybackAfterRebufferMsKey = "buffer_for_playback_after_rebuffer_ms"
    private const val backBufferDurationMsKey = "back_buffer_duration_ms"
    private const val targetBufferSizeMbKey = "target_buffer_size_mb"
    private const val autoSkipPostCreditsKey = "auto_skip_post_credits"
    private const val showLoadingOverlayKey = "show_loading_overlay"
    private const val showPlayerLoadingStatusKey = "show_player_loading_status"
    private const val pauseOverlayEnabledKey = "pause_overlay_enabled"
    private const val showParentalGuideKey = "show_parental_guide"
    private const val resizeModeKey = "resize_mode"
    private const val holdToSpeedEnabledKey = "hold_to_speed_enabled"
    private const val holdToSpeedValueKey = "hold_to_speed_value"
    private const val touchGesturesEnabledKey = "touch_gestures_enabled"
    private const val externalPlayerEnabledKey = "external_player_enabled"
    private const val externalPlayerForwardSubtitlesKey = "external_player_forward_subtitles"
    private const val externalPlayerSendSkipSegmentsKey = "external_player_send_skip_segments"
    private const val externalPlayerIdKey = "external_player_id"
    private const val preferredAudioLanguageKey = "preferred_audio_language"
    private const val secondaryPreferredAudioLanguageKey = "secondary_preferred_audio_language"
    private const val preferredSubtitleLanguageKey = "preferred_subtitle_language"
    private const val secondaryPreferredSubtitleLanguageKey = "secondary_preferred_subtitle_language"
    private const val subtitleTextColorKey = "subtitle_text_color"
    private const val subtitleBackgroundColorKey = "subtitle_background_color"
    private const val subtitleOutlineColorKey = "subtitle_outline_color"
    private const val subtitleOutlineEnabledKey = "subtitle_outline_enabled"
    private const val subtitleOutlineWidthKey = "subtitle_outline_width"
    private const val subtitleOutlineEffectKey = "subtitle_outline_effect"
    private const val subtitleFontKey = "subtitle_font"
    private const val subtitleBoldKey = "subtitle_bold"
    private const val subtitleFontSizeSpKey = "subtitle_font_size_sp"
    private const val subtitleBottomOffsetKey = "subtitle_bottom_offset"
    private const val subtitleStripSdhKey = "subtitle_strip_sdh"
    private const val subtitleUseForcedSubtitlesKey = "subtitle_use_forced_subtitles"
    private const val subtitleShowOnlyPreferredLanguagesKey = "subtitle_show_only_preferred_languages"
    private const val addonSubtitleStartupModeKey = "addon_subtitle_startup_mode"
    private const val streamReuseLastLinkEnabledKey = "stream_reuse_last_link_enabled"
    private const val streamReuseLastLinkCacheHoursKey = "stream_reuse_last_link_cache_hours"
    private const val androidPlaybackEngineKey = "android_playback_engine"
    private const val autoSwitchPlaybackEngineOnErrorKey = "auto_switch_playback_engine_on_error"
    private const val androidLibmpvVideoOutputKey = "android_libmpv_video_output"
    private const val androidLibmpvHardwareDecodingEnabledKey = "android_libmpv_hardware_decoding_enabled"
    private const val androidLibmpvYuv420pEnabledKey = "android_libmpv_yuv420p_enabled"
    private const val androidExoChaptersEnabledKey = "android_exo_chapters_enabled"
    private const val decoderPriorityKey = "decoder_priority"
    private const val mapDV7ToHevcKey = "map_dv7_to_hevc"
    private const val tunnelingEnabledKey = "tunneling_enabled"
    private const val streamAutoPlayModeKey = "stream_auto_play_mode"
    private const val streamAutoPlaySourceKey = "stream_auto_play_source"
    private const val streamAutoPlaySelectedAddonsKey = "stream_auto_play_selected_addons"
    private const val streamAutoPlaySelectedPluginsKey = "stream_auto_play_selected_plugins"
    private const val streamAutoPlayRegexKey = "stream_auto_play_regex"
    private const val streamAutoPlayTimeoutSecondsKey = "stream_auto_play_timeout_seconds"
    private const val skipIntroEnabledKey = "skip_intro_enabled"
    private const val autoSkipSegmentTypesKey = "auto_skip_segment_types"
    private const val animeSkipEnabledKey = "animeskip_enabled"
    private const val animeSkipClientIdKey = "animeskip_client_id"
    private const val introDbApiKeyKey = "introdb_api_key"
    private const val introSubmitEnabledKey = "intro_submit_enabled"
    private const val streamAutoPlayNextEpisodeEnabledKey = "stream_auto_play_next_episode_enabled"
    private const val streamAutoPlayNextEpisodeFallbackEnabledKey = "stream_auto_play_next_episode_fallback_enabled"
    private const val streamAutoPlayPreferBingeGroupKey = "stream_auto_play_prefer_binge_group"
    private const val streamAutoPlayReuseBingeGroupKey = "stream_auto_play_reuse_binge_group"
    private const val nextEpisodeThresholdModeKey = "next_episode_threshold_mode"
    private const val nextEpisodeThresholdPercentKey = "next_episode_threshold_percent_v2"
    private const val nextEpisodeThresholdMinutesBeforeEndKey = "next_episode_threshold_minutes_before_end_v2"
    private const val useLibassKey = "use_libass"
    private const val libassRenderTypeKey = "libass_render_type"
    private const val libassRenderScaleKey = "libass_render_scale"
    private const val libassRenderModeKey = "libass_render_mode"
    private const val iosVideoOutputPresetKey = "ios_video_output_preset"
    private const val iosToneMappingModeKey = "ios_tone_mapping_mode"
    private const val iosTargetPrimariesKey = "ios_target_primaries"
    private const val iosTargetTransferKey = "ios_target_transfer"
    private const val iosHardwareDecoderModeKey = "ios_hardware_decoder_mode"
    private const val iosAudioOutputModeKey = "ios_audio_output_mode"
    private const val iosExtendedDynamicRangeEnabledKey = "ios_extended_dynamic_range_enabled"
    private const val iosTargetColorspaceHintEnabledKey = "ios_target_colorspace_hint_enabled"
    private const val iosHdrComputePeakEnabledKey = "ios_hdr_compute_peak_enabled"
    private const val iosDebandEnabledKey = "ios_deband_enabled"
    private const val iosInterpolationEnabledKey = "ios_interpolation_enabled"
    private const val iosBrightnessKey = "ios_brightness"
    private const val iosContrastKey = "ios_contrast"
    private const val iosSaturationKey = "ios_saturation"
    private const val iosGammaKey = "ios_gamma"
    private const val nvidiaRtxSuperResolutionEnabledKey = "nvidia_rtx_super_resolution_enabled"
    private const val animeUpscalerEnabledKey = "anime_upscaler_enabled"
    private const val animeUpscalerOptionKey = "anime_upscaler_option"
    private const val animeUpscalerModeKey = "anime_upscaler_mode"
    private const val svpMotionOptionKey = "svp_motion_option"
    private const val audioNightModeEnabledKey = "audio_night_mode_enabled"
    private val store = DesktopStorage.store("nuvio_player_settings")
    private val subtitleRendererPreference = DesktopSubtitleRendererPreference(store)

    actual fun loadPendingExternalPlayback(): String? = store.getString(pendingExternalPlaybackKey)
    actual fun savePendingExternalPlayback(value: String?) = store.putString(pendingExternalPlaybackKey, value)
    actual fun loadPlaybackBrightness(): Float? = loadFloat(playbackBrightnessKey)
    actual fun savePlaybackBrightness(level: Float) = saveFloat(playbackBrightnessKey, level)
    actual fun loadUseLegacyPlayerLayout(): Boolean? = loadBoolean(useLegacyPlayerLayoutKey)
    actual fun saveUseLegacyPlayerLayout(enabled: Boolean) = saveBoolean(useLegacyPlayerLayoutKey, enabled)
    actual fun loadPlayerUiMode(): String? = loadString(playerUiModeKey)
    actual fun savePlayerUiMode(mode: String) = saveString(playerUiModeKey, mode)
    actual fun loadPreloadNextEpisodeSources(): Boolean? = loadBoolean(preloadNextEpisodeSourcesKey)
    actual fun savePreloadNextEpisodeSources(enabled: Boolean) = saveBoolean(preloadNextEpisodeSourcesKey, enabled)
    actual fun loadExoNativeMemoryEnabled(): Boolean? = loadBoolean(exoNativeMemoryEnabledKey)
    actual fun saveExoNativeMemoryEnabled(enabled: Boolean) = saveBoolean(exoNativeMemoryEnabledKey, enabled)
    actual fun loadVodCacheEnabled(): Boolean? = loadBoolean(vodCacheEnabledKey)
    actual fun saveVodCacheEnabled(enabled: Boolean) = saveBoolean(vodCacheEnabledKey, enabled)
    actual fun loadVodCacheSizeMode(): String? = loadString(vodCacheSizeModeKey)
    actual fun saveVodCacheSizeMode(mode: String) = saveString(vodCacheSizeModeKey, mode)
    actual fun loadVodCacheSizeMb(): Int? = loadInt(vodCacheSizeMbKey)
    actual fun saveVodCacheSizeMb(sizeMb: Int) = saveInt(vodCacheSizeMbKey, sizeMb)
    actual fun loadBufferEngineEnabled(): Boolean? = loadBoolean(bufferEngineEnabledKey)
    actual fun saveBufferEngineEnabled(enabled: Boolean) = saveBoolean(bufferEngineEnabledKey, enabled)
    actual fun loadMinBufferMs(): Int? = loadInt(minBufferMsKey)
    actual fun saveMinBufferMs(value: Int) = saveInt(minBufferMsKey, value)
    actual fun loadMaxBufferMs(): Int? = loadInt(maxBufferMsKey)
    actual fun saveMaxBufferMs(value: Int) = saveInt(maxBufferMsKey, value)
    actual fun loadBufferForPlaybackMs(): Int? = loadInt(bufferForPlaybackMsKey)
    actual fun saveBufferForPlaybackMs(value: Int) = saveInt(bufferForPlaybackMsKey, value)
    actual fun loadBufferForPlaybackAfterRebufferMs(): Int? = loadInt(bufferForPlaybackAfterRebufferMsKey)
    actual fun saveBufferForPlaybackAfterRebufferMs(value: Int) = saveInt(bufferForPlaybackAfterRebufferMsKey, value)
    actual fun loadBackBufferDurationMs(): Int? = loadInt(backBufferDurationMsKey)
    actual fun saveBackBufferDurationMs(value: Int) = saveInt(backBufferDurationMsKey, value)
    actual fun loadTargetBufferSizeMb(): Int? = loadInt(targetBufferSizeMbKey)
    actual fun saveTargetBufferSizeMb(value: Int) = saveInt(targetBufferSizeMbKey, value)
    actual fun loadAutoSkipMovieCredits(): Boolean? = loadBoolean(autoSkipMovieCreditsKey)
    actual fun saveAutoSkipMovieCredits(enabled: Boolean) = saveBoolean(autoSkipMovieCreditsKey, enabled)
    actual fun loadAutoSkipPostCredits(): Boolean? = loadBoolean(autoSkipPostCreditsKey)
    actual fun saveAutoSkipPostCredits(enabled: Boolean) = saveBoolean(autoSkipPostCreditsKey, enabled)
    actual fun loadShowLoadingOverlay(): Boolean? = loadBoolean(showLoadingOverlayKey)
    actual fun saveShowLoadingOverlay(enabled: Boolean) = saveBoolean(showLoadingOverlayKey, enabled)
    actual fun loadShowPlayerLoadingStatus(): Boolean? = loadBoolean(showPlayerLoadingStatusKey)
    actual fun saveShowPlayerLoadingStatus(enabled: Boolean) = saveBoolean(showPlayerLoadingStatusKey, enabled)
    actual fun loadPauseOverlayEnabled(): Boolean? = loadBoolean(pauseOverlayEnabledKey)
    actual fun savePauseOverlayEnabled(enabled: Boolean) = saveBoolean(pauseOverlayEnabledKey, enabled)
    actual fun loadShowParentalGuide(): Boolean? = loadBoolean(showParentalGuideKey)
    actual fun saveShowParentalGuide(enabled: Boolean) = saveBoolean(showParentalGuideKey, enabled)
    actual fun loadResizeMode(): String? = loadString(resizeModeKey)
    actual fun saveResizeMode(mode: String) = saveString(resizeModeKey, mode)
    actual fun loadHoldToSpeedEnabled(): Boolean? = loadBoolean(holdToSpeedEnabledKey)
    actual fun saveHoldToSpeedEnabled(enabled: Boolean) = saveBoolean(holdToSpeedEnabledKey, enabled)
    actual fun loadHoldToSpeedValue(): Float? = loadFloat(holdToSpeedValueKey)
    actual fun saveHoldToSpeedValue(speed: Float) = saveFloat(holdToSpeedValueKey, speed)
    actual fun loadTouchGesturesEnabled(): Boolean? = loadBoolean(touchGesturesEnabledKey)
    actual fun saveTouchGesturesEnabled(enabled: Boolean) = saveBoolean(touchGesturesEnabledKey, enabled)
    actual fun loadExternalPlayerEnabled(): Boolean? = loadBoolean(externalPlayerEnabledKey)
    actual fun saveExternalPlayerEnabled(enabled: Boolean) = saveBoolean(externalPlayerEnabledKey, enabled)
    actual fun loadExternalPlayerForwardSubtitles(): Boolean? = loadBoolean(externalPlayerForwardSubtitlesKey)
    actual fun saveExternalPlayerForwardSubtitles(enabled: Boolean) = saveBoolean(externalPlayerForwardSubtitlesKey, enabled)
    actual fun loadExternalPlayerSendSkipSegments(): Boolean? = loadBoolean(externalPlayerSendSkipSegmentsKey)
    actual fun saveExternalPlayerSendSkipSegments(enabled: Boolean) =
        saveBoolean(externalPlayerSendSkipSegmentsKey, enabled)
    actual fun loadExternalPlayerId(): String? = loadString(externalPlayerIdKey)
    actual fun saveExternalPlayerId(playerId: String?) = saveOptionalString(externalPlayerIdKey, playerId)
    actual fun loadPreferredAudioLanguage(): String? = loadString(preferredAudioLanguageKey)
    actual fun savePreferredAudioLanguage(language: String) = saveString(preferredAudioLanguageKey, language)
    actual fun loadSecondaryPreferredAudioLanguage(): String? = loadString(secondaryPreferredAudioLanguageKey)
    actual fun saveSecondaryPreferredAudioLanguage(language: String?) = saveOptionalString(secondaryPreferredAudioLanguageKey, language)
    actual fun loadPreferredSubtitleLanguage(): String? = loadString(preferredSubtitleLanguageKey)
    actual fun savePreferredSubtitleLanguage(language: String) = saveString(preferredSubtitleLanguageKey, language)
    actual fun loadSecondaryPreferredSubtitleLanguage(): String? = loadString(secondaryPreferredSubtitleLanguageKey)
    actual fun saveSecondaryPreferredSubtitleLanguage(language: String?) = saveOptionalString(secondaryPreferredSubtitleLanguageKey, language)
    actual fun loadSubtitleTextColor(): String? = loadString(subtitleTextColorKey)
    actual fun saveSubtitleTextColor(colorHex: String) = saveString(subtitleTextColorKey, colorHex)
    actual fun loadSubtitleBackgroundColor(): String? = loadString(subtitleBackgroundColorKey)
    actual fun saveSubtitleBackgroundColor(colorHex: String) = saveString(subtitleBackgroundColorKey, colorHex)
    actual fun loadSubtitleOutlineColor(): String? = loadString(subtitleOutlineColorKey)
    actual fun saveSubtitleOutlineColor(colorHex: String) = saveString(subtitleOutlineColorKey, colorHex)
    actual fun loadSubtitleOutlineEnabled(): Boolean? = loadBoolean(subtitleOutlineEnabledKey)
    actual fun saveSubtitleOutlineEnabled(enabled: Boolean) = saveBoolean(subtitleOutlineEnabledKey, enabled)
    actual fun loadSubtitleOutlineWidth(): Int? = loadInt(subtitleOutlineWidthKey)
    actual fun saveSubtitleOutlineWidth(width: Int) = saveInt(subtitleOutlineWidthKey, width)
    actual fun loadSubtitleOutlineEffect(): String? = loadString(subtitleOutlineEffectKey)
    actual fun saveSubtitleOutlineEffect(effect: String) = saveString(subtitleOutlineEffectKey, effect)
    actual fun loadSubtitleFont(): String? = loadString(subtitleFontKey)
    actual fun saveSubtitleFont(font: String) = saveString(subtitleFontKey, font)
    actual fun loadSubtitleBold(): Boolean? = loadBoolean(subtitleBoldKey)
    actual fun saveSubtitleBold(enabled: Boolean) = saveBoolean(subtitleBoldKey, enabled)
    actual fun loadSubtitleFontSizeSp(): Int? = loadInt(subtitleFontSizeSpKey)
    actual fun saveSubtitleFontSizeSp(fontSizeSp: Int) = saveInt(subtitleFontSizeSpKey, fontSizeSp)
    actual fun loadSubtitleBottomOffset(): Int? = loadInt(subtitleBottomOffsetKey)
    actual fun saveSubtitleBottomOffset(bottomOffset: Int) = saveInt(subtitleBottomOffsetKey, bottomOffset)
    actual fun loadSubtitleStripSdh(): Boolean? = loadBoolean(subtitleStripSdhKey)
    actual fun saveSubtitleStripSdh(enabled: Boolean) = saveBoolean(subtitleStripSdhKey, enabled)
    actual fun loadSubtitleUseForcedSubtitles(): Boolean? = loadBoolean(subtitleUseForcedSubtitlesKey)
    actual fun saveSubtitleUseForcedSubtitles(enabled: Boolean) = saveBoolean(subtitleUseForcedSubtitlesKey, enabled)
    actual fun loadSubtitleShowOnlyPreferredLanguages(): Boolean? = loadBoolean(subtitleShowOnlyPreferredLanguagesKey)
    actual fun saveSubtitleShowOnlyPreferredLanguages(enabled: Boolean) = saveBoolean(subtitleShowOnlyPreferredLanguagesKey, enabled)
    fun loadAddonSubtitleStartupMode(): String? = loadString(addonSubtitleStartupModeKey)
    fun saveAddonSubtitleStartupMode(mode: String) = saveString(addonSubtitleStartupModeKey, mode)
    actual fun loadStreamReuseLastLinkEnabled(): Boolean? = loadBoolean(streamReuseLastLinkEnabledKey)
    actual fun saveStreamReuseLastLinkEnabled(enabled: Boolean) = saveBoolean(streamReuseLastLinkEnabledKey, enabled)
    actual fun loadStreamReuseLastLinkCacheHours(): Int? = loadInt(streamReuseLastLinkCacheHoursKey)
    actual fun saveStreamReuseLastLinkCacheHours(hours: Int) = saveInt(streamReuseLastLinkCacheHoursKey, hours)
    actual fun loadAndroidPlaybackEngine(): String? = loadString(androidPlaybackEngineKey)
    actual fun saveAndroidPlaybackEngine(engine: String) = saveString(androidPlaybackEngineKey, engine)
    actual fun loadAutoSwitchPlaybackEngineOnError(): Boolean? = loadBoolean(autoSwitchPlaybackEngineOnErrorKey)
    actual fun saveAutoSwitchPlaybackEngineOnError(enabled: Boolean) = saveBoolean(autoSwitchPlaybackEngineOnErrorKey, enabled)
    actual fun loadAndroidLibmpvVideoOutput(): String? = loadString(androidLibmpvVideoOutputKey)
    actual fun saveAndroidLibmpvVideoOutput(output: String) = saveString(androidLibmpvVideoOutputKey, output)
    actual fun loadAndroidLibmpvHardwareDecodingEnabled(): Boolean? = loadBoolean(androidLibmpvHardwareDecodingEnabledKey)
    actual fun saveAndroidLibmpvHardwareDecodingEnabled(enabled: Boolean) =
        saveBoolean(androidLibmpvHardwareDecodingEnabledKey, enabled)
    actual fun loadAndroidLibmpvYuv420pEnabled(): Boolean? = loadBoolean(androidLibmpvYuv420pEnabledKey)
    actual fun saveAndroidLibmpvYuv420pEnabled(enabled: Boolean) = saveBoolean(androidLibmpvYuv420pEnabledKey, enabled)
    actual fun loadAndroidExoChaptersEnabled(): Boolean? = loadBoolean(androidExoChaptersEnabledKey)
    actual fun saveAndroidExoChaptersEnabled(enabled: Boolean) = saveBoolean(androidExoChaptersEnabledKey, enabled)
    actual fun loadDecoderPriority(): Int? = loadInt(decoderPriorityKey)
    actual fun saveDecoderPriority(priority: Int) = saveInt(decoderPriorityKey, priority)
    actual fun loadMapDV7ToHevc(): Boolean? = loadBoolean(mapDV7ToHevcKey)
    actual fun saveMapDV7ToHevc(enabled: Boolean) = saveBoolean(mapDV7ToHevcKey, enabled)
    actual fun loadTunnelingEnabled(): Boolean? = loadBoolean(tunnelingEnabledKey)
    actual fun saveTunnelingEnabled(enabled: Boolean) = saveBoolean(tunnelingEnabledKey, enabled)
    actual fun loadStreamAutoPlayMode(): String? = loadString(streamAutoPlayModeKey)
    actual fun saveStreamAutoPlayMode(mode: String) = saveString(streamAutoPlayModeKey, mode)
    actual fun loadStreamAutoPlaySource(): String? = loadString(streamAutoPlaySourceKey)
    actual fun saveStreamAutoPlaySource(source: String) = saveString(streamAutoPlaySourceKey, source)
    actual fun loadStreamAutoPlaySelectedAddons(): Set<String>? = loadStringSet(streamAutoPlaySelectedAddonsKey)
    actual fun saveStreamAutoPlaySelectedAddons(addons: Set<String>) = saveStringSet(streamAutoPlaySelectedAddonsKey, addons)
    actual fun loadStreamAutoPlaySelectedPlugins(): Set<String>? = loadStringSet(streamAutoPlaySelectedPluginsKey)
    actual fun saveStreamAutoPlaySelectedPlugins(plugins: Set<String>) = saveStringSet(streamAutoPlaySelectedPluginsKey, plugins)
    actual fun loadStreamAutoPlayRegex(): String? = loadString(streamAutoPlayRegexKey)
    actual fun saveStreamAutoPlayRegex(regex: String) = saveString(streamAutoPlayRegexKey, regex)
    actual fun loadStreamAutoPlayTimeoutSeconds(): Int? = loadInt(streamAutoPlayTimeoutSecondsKey)
    actual fun saveStreamAutoPlayTimeoutSeconds(seconds: Int) = saveInt(streamAutoPlayTimeoutSecondsKey, seconds)
    actual fun loadSkipIntroEnabled(): Boolean? = loadBoolean(skipIntroEnabledKey)
    actual fun saveSkipIntroEnabled(enabled: Boolean) = saveBoolean(skipIntroEnabledKey, enabled)
    actual fun loadAutoSkipSegmentTypes(): Set<String>? = loadStringSet(autoSkipSegmentTypesKey)
    actual fun saveAutoSkipSegmentTypes(segmentTypes: Set<String>) = saveStringSet(autoSkipSegmentTypesKey, segmentTypes)
    actual fun loadAnimeSkipEnabled(): Boolean? = loadBoolean(animeSkipEnabledKey)
    actual fun saveAnimeSkipEnabled(enabled: Boolean) = saveBoolean(animeSkipEnabledKey, enabled)
    actual fun loadAnimeSkipClientId(): String? = loadString(animeSkipClientIdKey)
    actual fun saveAnimeSkipClientId(clientId: String) = saveString(animeSkipClientIdKey, clientId)
    actual fun loadIntroDbApiKey(): String? = loadString(introDbApiKeyKey)
    actual fun saveIntroDbApiKey(apiKey: String) = saveString(introDbApiKeyKey, apiKey)
    actual fun loadIntroSubmitEnabled(): Boolean? = loadBoolean(introSubmitEnabledKey)
    actual fun saveIntroSubmitEnabled(enabled: Boolean) = saveBoolean(introSubmitEnabledKey, enabled)
    actual fun loadStreamAutoPlayNextEpisodeEnabled(): Boolean? = loadBoolean(streamAutoPlayNextEpisodeEnabledKey)
    actual fun saveStreamAutoPlayNextEpisodeEnabled(enabled: Boolean) = saveBoolean(streamAutoPlayNextEpisodeEnabledKey, enabled)
    actual fun loadStreamAutoPlayNextEpisodeFallbackEnabled(): Boolean? =
        loadBoolean(streamAutoPlayNextEpisodeFallbackEnabledKey)
    actual fun saveStreamAutoPlayNextEpisodeFallbackEnabled(enabled: Boolean) =
        saveBoolean(streamAutoPlayNextEpisodeFallbackEnabledKey, enabled)
    actual fun loadStreamAutoPlayPreferBingeGroup(): Boolean? = loadBoolean(streamAutoPlayPreferBingeGroupKey)
    actual fun saveStreamAutoPlayPreferBingeGroup(enabled: Boolean) = saveBoolean(streamAutoPlayPreferBingeGroupKey, enabled)
    actual fun loadStreamAutoPlayReuseBingeGroup(): Boolean? = loadBoolean(streamAutoPlayReuseBingeGroupKey)
    actual fun saveStreamAutoPlayReuseBingeGroup(enabled: Boolean) = saveBoolean(streamAutoPlayReuseBingeGroupKey, enabled)
    actual fun loadNextEpisodeThresholdMode(): String? = loadString(nextEpisodeThresholdModeKey)
    actual fun saveNextEpisodeThresholdMode(mode: String) = saveString(nextEpisodeThresholdModeKey, mode)
    actual fun loadNextEpisodeThresholdPercent(): Float? = loadFloat(nextEpisodeThresholdPercentKey)
    actual fun saveNextEpisodeThresholdPercent(percent: Float) = saveFloat(nextEpisodeThresholdPercentKey, percent)
    actual fun loadNextEpisodeThresholdMinutesBeforeEnd(): Float? = loadFloat(nextEpisodeThresholdMinutesBeforeEndKey)
    actual fun saveNextEpisodeThresholdMinutesBeforeEnd(minutes: Float) = saveFloat(nextEpisodeThresholdMinutesBeforeEndKey, minutes)
    actual fun loadUseLibass(): Boolean? = subtitleRendererPreference.load()
    actual fun saveUseLibass(enabled: Boolean) = subtitleRendererPreference.save(enabled)
    actual fun loadLibassRenderType(): String? = loadString(libassRenderTypeKey)
    actual fun saveLibassRenderType(renderType: String) = saveString(libassRenderTypeKey, renderType)
    actual fun loadLibassRenderScale(): String? = loadString(libassRenderScaleKey)
    actual fun saveLibassRenderScale(scale: String) = saveString(libassRenderScaleKey, scale)
    actual fun loadLibassRenderMode(): String? = loadString(libassRenderModeKey)
    actual fun saveLibassRenderMode(mode: String) = saveString(libassRenderModeKey, mode)
    actual fun loadIosVideoOutputPreset(): String? = loadString(iosVideoOutputPresetKey)
    actual fun saveIosVideoOutputPreset(preset: String) = saveString(iosVideoOutputPresetKey, preset)
    actual fun loadIosToneMappingMode(): String? = loadString(iosToneMappingModeKey)
    actual fun saveIosToneMappingMode(mode: String) = saveString(iosToneMappingModeKey, mode)
    actual fun loadIosTargetPrimaries(): String? = loadString(iosTargetPrimariesKey)
    actual fun saveIosTargetPrimaries(primaries: String) = saveString(iosTargetPrimariesKey, primaries)
    actual fun loadIosTargetTransfer(): String? = loadString(iosTargetTransferKey)
    actual fun saveIosTargetTransfer(transfer: String) = saveString(iosTargetTransferKey, transfer)
    actual fun loadIosHardwareDecoderMode(): String? = loadString(iosHardwareDecoderModeKey)
    actual fun saveIosHardwareDecoderMode(mode: String) = saveString(iosHardwareDecoderModeKey, mode)
    actual fun loadIosAudioOutputMode(): String? = loadString(iosAudioOutputModeKey)
    actual fun saveIosAudioOutputMode(mode: String) = saveString(iosAudioOutputModeKey, mode)
    actual fun loadIosExtendedDynamicRangeEnabled(): Boolean? = loadBoolean(iosExtendedDynamicRangeEnabledKey)
    actual fun saveIosExtendedDynamicRangeEnabled(enabled: Boolean) = saveBoolean(iosExtendedDynamicRangeEnabledKey, enabled)
    actual fun loadIosTargetColorspaceHintEnabled(): Boolean? = loadBoolean(iosTargetColorspaceHintEnabledKey)
    actual fun saveIosTargetColorspaceHintEnabled(enabled: Boolean) = saveBoolean(iosTargetColorspaceHintEnabledKey, enabled)
    actual fun loadIosHdrComputePeakEnabled(): Boolean? = loadBoolean(iosHdrComputePeakEnabledKey)
    actual fun saveIosHdrComputePeakEnabled(enabled: Boolean) = saveBoolean(iosHdrComputePeakEnabledKey, enabled)
    actual fun loadIosDebandEnabled(): Boolean? = loadBoolean(iosDebandEnabledKey)
    actual fun saveIosDebandEnabled(enabled: Boolean) = saveBoolean(iosDebandEnabledKey, enabled)
    actual fun loadIosInterpolationEnabled(): Boolean? = loadBoolean(iosInterpolationEnabledKey)
    actual fun saveIosInterpolationEnabled(enabled: Boolean) = saveBoolean(iosInterpolationEnabledKey, enabled)
    actual fun loadIosBrightness(): Int? = loadInt(iosBrightnessKey)
    actual fun saveIosBrightness(value: Int) = saveInt(iosBrightnessKey, value)
    actual fun loadIosContrast(): Int? = loadInt(iosContrastKey)
    actual fun saveIosContrast(value: Int) = saveInt(iosContrastKey, value)
    actual fun loadIosSaturation(): Int? = loadInt(iosSaturationKey)
    actual fun saveIosSaturation(value: Int) = saveInt(iosSaturationKey, value)
    actual fun loadIosGamma(): Int? = loadInt(iosGammaKey)
    actual fun saveIosGamma(value: Int) = saveInt(iosGammaKey, value)

    actual fun loadNvidiaRtxSuperResolutionEnabled(): Boolean? = loadBoolean(nvidiaRtxSuperResolutionEnabledKey)
    actual fun saveNvidiaRtxSuperResolutionEnabled(enabled: Boolean) = saveBoolean(nvidiaRtxSuperResolutionEnabledKey, enabled)

    actual fun loadAnimeUpscalerEnabled(): Boolean? = loadBoolean(animeUpscalerEnabledKey)
    actual fun saveAnimeUpscalerEnabled(enabled: Boolean) = saveBoolean(animeUpscalerEnabledKey, enabled)
    actual fun loadAnimeUpscalerOption(): String? = loadString(animeUpscalerOptionKey)
    actual fun saveAnimeUpscalerOption(option: String) = saveString(animeUpscalerOptionKey, option)
    actual fun loadAnimeUpscalerMode(): String? = loadString(animeUpscalerModeKey)
    actual fun saveAnimeUpscalerMode(mode: String) = saveString(animeUpscalerModeKey, mode)
    actual fun loadSvpMotionOption(): String? = loadString(svpMotionOptionKey)
    actual fun saveSvpMotionOption(option: String) = saveString(svpMotionOptionKey, option)

    actual fun loadAudioNightModeEnabled(): Boolean? = loadBoolean(audioNightModeEnabledKey)
    actual fun saveAudioNightModeEnabled(enabled: Boolean) = saveBoolean(audioNightModeEnabledKey, enabled)

    actual fun loadShowSeekbarWhileSeeking(): Boolean? = loadBoolean(showSeekbarWhileSeekingKey)
    actual fun saveShowSeekbarWhileSeeking(enabled: Boolean) = saveBoolean(showSeekbarWhileSeekingKey, enabled)

    actual fun loadShowClockInPlayer(): Boolean? = loadBoolean(showClockInPlayerKey)
    actual fun saveShowClockInPlayer(enabled: Boolean) = saveBoolean(showClockInPlayerKey, enabled)

    actual fun loadSeekPreviewEnabled(): Boolean? = loadBoolean(seekPreviewEnabledKey)
    actual fun saveSeekPreviewEnabled(enabled: Boolean) = saveBoolean(seekPreviewEnabledKey, enabled)

    actual fun loadSeekPreviewWhileDragging(): Boolean? = loadBoolean(seekPreviewWhileDraggingKey)
    actual fun saveSeekPreviewWhileDragging(value: Boolean) = saveBoolean(seekPreviewWhileDraggingKey, value)

    actual fun loadSeekPreviewWhileHovering(): Boolean? = loadBoolean(seekPreviewWhileHoveringKey)
    actual fun saveSeekPreviewWhileHovering(value: Boolean) = saveBoolean(seekPreviewWhileHoveringKey, value)

    actual fun loadSeekPreviewWhileKeyboardSeeking(): Boolean? = loadBoolean(seekPreviewWhileKeyboardSeekingKey)
    actual fun saveSeekPreviewWhileKeyboardSeeking(value: Boolean) = saveBoolean(seekPreviewWhileKeyboardSeekingKey, value)

    actual fun loadSeekPreviewThumbnailCount(): Int? = loadInt(seekPreviewThumbnailCountKey)
    actual fun saveSeekPreviewThumbnailCount(value: Int) = saveInt(seekPreviewThumbnailCountKey, value)

    actual fun loadSeekrApiKey(): String? = loadString(seekrApiKeyKey)
    actual fun saveSeekrApiKey(apiKey: String) = saveString(seekrApiKeyKey, apiKey)

    actual fun loadShowRemainingTime(): Boolean? = loadBoolean(showRemainingTimeKey)
    actual fun saveShowRemainingTime(enabled: Boolean) = saveBoolean(showRemainingTimeKey, enabled)

    private fun scoped(key: String): String = ProfileScopedKey.of(key)
    private fun loadString(key: String): String? = store.getString(scoped(key))
    private fun saveString(key: String, value: String) = store.putString(scoped(key), value)
    private fun saveOptionalString(key: String, value: String?) = store.putString(scoped(key), value?.takeIf { it.isNotBlank() })
    private fun loadBoolean(key: String): Boolean? = store.getBoolean(scoped(key))
    private fun saveBoolean(key: String, value: Boolean) = store.putBoolean(scoped(key), value)
    private fun loadInt(key: String): Int? = store.getInt(scoped(key))
    private fun saveInt(key: String, value: Int) = store.putInt(scoped(key), value)
    private fun loadFloat(key: String): Float? = store.getFloat(scoped(key))
    private fun saveFloat(key: String, value: Float) = store.putFloat(scoped(key), value)
    private fun loadStringSet(key: String): Set<String>? = store.getStringSet(scoped(key))
    private fun saveStringSet(key: String, values: Set<String>) = store.putStringSet(scoped(key), values)

    private val syncStorage by lazy { SyncStorage(store) }

    actual fun exportToSyncPayload(): JsonObject = syncStorage.exportToSyncPayload()

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        syncStorage.replaceFromSyncPayload(payload)
    }

    /** Account sync only touches shared keys; local preferences use the same store unchanged. */
    internal class SyncStorage(
        private val store: DesktopStorage.Store,
        private val profileKey: (String) -> String = ProfileScopedKey::of,
    ) {
        private fun scoped(key: String): String = profileKey(key)

        private val syncKeys = listOf(
            showLoadingOverlayKey,
            showPlayerLoadingStatusKey,
            pauseOverlayEnabledKey,
            showParentalGuideKey,
            resizeModeKey,
            holdToSpeedEnabledKey,
            holdToSpeedValueKey,
            nvidiaRtxSuperResolutionEnabledKey,
            touchGesturesEnabledKey,
            externalPlayerEnabledKey,
            externalPlayerForwardSubtitlesKey,
            externalPlayerSendSkipSegmentsKey,
            externalPlayerIdKey,
            preferredAudioLanguageKey,
            secondaryPreferredAudioLanguageKey,
            preferredSubtitleLanguageKey,
            secondaryPreferredSubtitleLanguageKey,
            subtitleTextColorKey,
            subtitleBackgroundColorKey,
            subtitleOutlineColorKey,
            subtitleOutlineEnabledKey,
            subtitleOutlineWidthKey,
            subtitleBoldKey,
            subtitleFontSizeSpKey,
            subtitleBottomOffsetKey,
            subtitleStripSdhKey,
            subtitleUseForcedSubtitlesKey,
            subtitleShowOnlyPreferredLanguagesKey,
            addonSubtitleStartupModeKey,
            streamReuseLastLinkEnabledKey,
            streamReuseLastLinkCacheHoursKey,
            androidPlaybackEngineKey,
            autoSwitchPlaybackEngineOnErrorKey,
            androidLibmpvVideoOutputKey,
            androidLibmpvHardwareDecodingEnabledKey,
            androidLibmpvYuv420pEnabledKey,
            androidExoChaptersEnabledKey,
            decoderPriorityKey,
            mapDV7ToHevcKey,
            tunnelingEnabledKey,
            streamAutoPlayModeKey,
            streamAutoPlaySourceKey,
            streamAutoPlaySelectedAddonsKey,
            streamAutoPlaySelectedPluginsKey,
            streamAutoPlayRegexKey,
            streamAutoPlayTimeoutSecondsKey,
            skipIntroEnabledKey,
            autoSkipSegmentTypesKey,
            preloadNextEpisodeSourcesKey,
            exoNativeMemoryEnabledKey,
            vodCacheEnabledKey,
            vodCacheSizeModeKey,
            vodCacheSizeMbKey,
            bufferEngineEnabledKey,
            minBufferMsKey,
            maxBufferMsKey,
            bufferForPlaybackMsKey,
            bufferForPlaybackAfterRebufferMsKey,
            backBufferDurationMsKey,
            targetBufferSizeMbKey,
            autoSkipMovieCreditsKey,
            autoSkipPostCreditsKey,
            animeSkipEnabledKey,
            animeSkipClientIdKey,
            streamAutoPlayNextEpisodeEnabledKey,
            streamAutoPlayNextEpisodeFallbackEnabledKey,
            streamAutoPlayPreferBingeGroupKey,
            streamAutoPlayReuseBingeGroupKey,
            nextEpisodeThresholdModeKey,
            nextEpisodeThresholdPercentKey,
            nextEpisodeThresholdMinutesBeforeEndKey,
            useLibassKey,
            libassRenderTypeKey,
            libassRenderScaleKey,
            libassRenderModeKey,
            iosVideoOutputPresetKey,
            iosToneMappingModeKey,
            iosTargetPrimariesKey,
            iosTargetTransferKey,
            iosHardwareDecoderModeKey,
            iosAudioOutputModeKey,
            iosExtendedDynamicRangeEnabledKey,
            iosTargetColorspaceHintEnabledKey,
            iosHdrComputePeakEnabledKey,
            iosDebandEnabledKey,
            iosInterpolationEnabledKey,
            iosBrightnessKey,
            iosContrastKey,
            iosSaturationKey,
            iosGammaKey,
        )

        fun exportToSyncPayload(): JsonObject = buildJsonObject {
            store.getBoolean(scoped(showLoadingOverlayKey))?.let { put(showLoadingOverlayKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(showPlayerLoadingStatusKey))?.let { put(showPlayerLoadingStatusKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(pauseOverlayEnabledKey))?.let { put(pauseOverlayEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(showParentalGuideKey))?.let { put(showParentalGuideKey, encodeSyncBoolean(it)) }
            store.getString(scoped(resizeModeKey))?.let { put(resizeModeKey, encodeSyncString(it)) }
            store.getBoolean(scoped(holdToSpeedEnabledKey))?.let { put(holdToSpeedEnabledKey, encodeSyncBoolean(it)) }
            store.getFloat(scoped(holdToSpeedValueKey))?.let { put(holdToSpeedValueKey, encodeSyncFloat(it)) }
            store.getBoolean(scoped(touchGesturesEnabledKey))?.let { put(touchGesturesEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(externalPlayerEnabledKey))?.let { put(externalPlayerEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(externalPlayerForwardSubtitlesKey))?.let { put(externalPlayerForwardSubtitlesKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(externalPlayerSendSkipSegmentsKey))?.let { put(externalPlayerSendSkipSegmentsKey, encodeSyncBoolean(it)) }
            store.getString(scoped(externalPlayerIdKey))?.let { put(externalPlayerIdKey, encodeSyncString(it)) }
            store.getString(scoped(preferredAudioLanguageKey))?.let { put(preferredAudioLanguageKey, encodeSyncString(it)) }
            store.getString(scoped(secondaryPreferredAudioLanguageKey))?.let { put(secondaryPreferredAudioLanguageKey, encodeSyncString(it)) }
            store.getString(scoped(preferredSubtitleLanguageKey))?.let { put(preferredSubtitleLanguageKey, encodeSyncString(it)) }
            store.getString(scoped(secondaryPreferredSubtitleLanguageKey))?.let { put(secondaryPreferredSubtitleLanguageKey, encodeSyncString(it)) }
            store.getString(scoped(subtitleTextColorKey))?.let { put(subtitleTextColorKey, encodeSyncString(it)) }
            store.getString(scoped(subtitleBackgroundColorKey))?.let { put(subtitleBackgroundColorKey, encodeSyncString(it)) }
            store.getString(scoped(subtitleOutlineColorKey))?.let { put(subtitleOutlineColorKey, encodeSyncString(it)) }
            store.getBoolean(scoped(subtitleOutlineEnabledKey))?.let { put(subtitleOutlineEnabledKey, encodeSyncBoolean(it)) }
            store.getInt(scoped(subtitleOutlineWidthKey))?.let { put(subtitleOutlineWidthKey, encodeSyncInt(it)) }
            store.getBoolean(scoped(subtitleBoldKey))?.let { put(subtitleBoldKey, encodeSyncBoolean(it)) }
            store.getInt(scoped(subtitleFontSizeSpKey))?.let { put(subtitleFontSizeSpKey, encodeSyncInt(it)) }
            store.getInt(scoped(subtitleBottomOffsetKey))?.let { put(subtitleBottomOffsetKey, encodeSyncInt(it)) }
            store.getBoolean(scoped(subtitleStripSdhKey))?.let { put(subtitleStripSdhKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(subtitleUseForcedSubtitlesKey))?.let { put(subtitleUseForcedSubtitlesKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(subtitleShowOnlyPreferredLanguagesKey))?.let { put(subtitleShowOnlyPreferredLanguagesKey, encodeSyncBoolean(it)) }
            store.getString(scoped(addonSubtitleStartupModeKey))?.let { put(addonSubtitleStartupModeKey, encodeSyncString(it)) }
            store.getBoolean(scoped(streamReuseLastLinkEnabledKey))?.let { put(streamReuseLastLinkEnabledKey, encodeSyncBoolean(it)) }
            store.getInt(scoped(streamReuseLastLinkCacheHoursKey))?.let { put(streamReuseLastLinkCacheHoursKey, encodeSyncInt(it)) }
            store.getString(scoped(androidPlaybackEngineKey))?.let { put(androidPlaybackEngineKey, encodeSyncString(it)) }
            store.getString(scoped(androidLibmpvVideoOutputKey))?.let { put(androidLibmpvVideoOutputKey, encodeSyncString(it)) }
            store.getBoolean(scoped(androidLibmpvHardwareDecodingEnabledKey))?.let { put(androidLibmpvHardwareDecodingEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(androidLibmpvYuv420pEnabledKey))?.let { put(androidLibmpvYuv420pEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(androidExoChaptersEnabledKey))?.let { put(androidExoChaptersEnabledKey, encodeSyncBoolean(it)) }
            store.getInt(scoped(decoderPriorityKey))?.let { put(decoderPriorityKey, encodeSyncInt(it)) }
            store.getBoolean(scoped(mapDV7ToHevcKey))?.let { put(mapDV7ToHevcKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(tunnelingEnabledKey))?.let { put(tunnelingEnabledKey, encodeSyncBoolean(it)) }
            store.getString(scoped(streamAutoPlayModeKey))?.let { put(streamAutoPlayModeKey, encodeSyncString(it)) }
            store.getString(scoped(streamAutoPlaySourceKey))?.let { put(streamAutoPlaySourceKey, encodeSyncString(it)) }
            store.getStringSet(scoped(streamAutoPlaySelectedAddonsKey))?.let { put(streamAutoPlaySelectedAddonsKey, encodeSyncStringSet(it)) }
            store.getStringSet(scoped(streamAutoPlaySelectedPluginsKey))?.let { put(streamAutoPlaySelectedPluginsKey, encodeSyncStringSet(it)) }
            store.getString(scoped(streamAutoPlayRegexKey))?.let { put(streamAutoPlayRegexKey, encodeSyncString(it)) }
            store.getInt(scoped(streamAutoPlayTimeoutSecondsKey))?.let { put(streamAutoPlayTimeoutSecondsKey, encodeSyncInt(it)) }
            store.getBoolean(scoped(skipIntroEnabledKey))?.let { put(skipIntroEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(preloadNextEpisodeSourcesKey))?.let { put(preloadNextEpisodeSourcesKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(autoSkipMovieCreditsKey))?.let { put(autoSkipMovieCreditsKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(autoSkipPostCreditsKey))?.let { put(autoSkipPostCreditsKey, encodeSyncBoolean(it)) }
            store.getStringSet(scoped(autoSkipSegmentTypesKey))?.let { put(autoSkipSegmentTypesKey, encodeSyncStringSet(it)) }
            store.getBoolean(scoped(animeSkipEnabledKey))?.let { put(animeSkipEnabledKey, encodeSyncBoolean(it)) }
            store.getString(scoped(animeSkipClientIdKey))?.let { put(animeSkipClientIdKey, encodeSyncString(it)) }
            store.getBoolean(scoped(streamAutoPlayNextEpisodeEnabledKey))?.let { put(streamAutoPlayNextEpisodeEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(streamAutoPlayNextEpisodeFallbackEnabledKey))?.let { put(streamAutoPlayNextEpisodeFallbackEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(streamAutoPlayPreferBingeGroupKey))?.let { put(streamAutoPlayPreferBingeGroupKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(streamAutoPlayReuseBingeGroupKey))?.let { put(streamAutoPlayReuseBingeGroupKey, encodeSyncBoolean(it)) }
            store.getString(scoped(nextEpisodeThresholdModeKey))?.let { put(nextEpisodeThresholdModeKey, encodeSyncString(it)) }
            store.getFloat(scoped(nextEpisodeThresholdPercentKey))?.let { put(nextEpisodeThresholdPercentKey, encodeSyncFloat(it)) }
            store.getFloat(scoped(nextEpisodeThresholdMinutesBeforeEndKey))?.let { put(nextEpisodeThresholdMinutesBeforeEndKey, encodeSyncFloat(it)) }
            store.getBoolean(scoped(useLibassKey))?.let { put(useLibassKey, encodeSyncBoolean(it)) }
            store.getString(scoped(libassRenderTypeKey))?.let { put(libassRenderTypeKey, encodeSyncString(it)) }
            store.getString(scoped(libassRenderScaleKey))?.let { put(libassRenderScaleKey, encodeSyncString(it)) }
            store.getString(scoped(iosVideoOutputPresetKey))?.let { put(iosVideoOutputPresetKey, encodeSyncString(it)) }
            store.getString(scoped(iosToneMappingModeKey))?.let { put(iosToneMappingModeKey, encodeSyncString(it)) }
            store.getString(scoped(iosTargetPrimariesKey))?.let { put(iosTargetPrimariesKey, encodeSyncString(it)) }
            store.getString(scoped(iosTargetTransferKey))?.let { put(iosTargetTransferKey, encodeSyncString(it)) }
            store.getString(scoped(iosHardwareDecoderModeKey))?.let { put(iosHardwareDecoderModeKey, encodeSyncString(it)) }
            store.getString(scoped(iosAudioOutputModeKey))?.let { put(iosAudioOutputModeKey, encodeSyncString(it)) }
            store.getBoolean(scoped(iosExtendedDynamicRangeEnabledKey))?.let { put(iosExtendedDynamicRangeEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(iosTargetColorspaceHintEnabledKey))?.let { put(iosTargetColorspaceHintEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(iosHdrComputePeakEnabledKey))?.let { put(iosHdrComputePeakEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(iosDebandEnabledKey))?.let { put(iosDebandEnabledKey, encodeSyncBoolean(it)) }
            store.getBoolean(scoped(iosInterpolationEnabledKey))?.let { put(iosInterpolationEnabledKey, encodeSyncBoolean(it)) }
            store.getInt(scoped(iosBrightnessKey))?.let { put(iosBrightnessKey, encodeSyncInt(it)) }
            store.getInt(scoped(iosContrastKey))?.let { put(iosContrastKey, encodeSyncInt(it)) }
            store.getInt(scoped(iosSaturationKey))?.let { put(iosSaturationKey, encodeSyncInt(it)) }
            store.getInt(scoped(iosGammaKey))?.let { put(iosGammaKey, encodeSyncInt(it)) }
            store.getBoolean(scoped(nvidiaRtxSuperResolutionEnabledKey))?.let { put(nvidiaRtxSuperResolutionEnabledKey, encodeSyncBoolean(it)) }
        }

        fun replaceFromSyncPayload(payload: JsonObject) {
            store.removeAll(syncKeys.map(::scoped))
            payload.decodeSyncBoolean(showLoadingOverlayKey)?.let { store.putBoolean(scoped(showLoadingOverlayKey), it) }
            payload.decodeSyncBoolean(showPlayerLoadingStatusKey)?.let { store.putBoolean(scoped(showPlayerLoadingStatusKey), it) }
            payload.decodeSyncBoolean(pauseOverlayEnabledKey)?.let { store.putBoolean(scoped(pauseOverlayEnabledKey), it) }
            payload.decodeSyncBoolean(showParentalGuideKey)?.let { store.putBoolean(scoped(showParentalGuideKey), it) }
            payload.decodeSyncString(resizeModeKey)?.let { store.putString(scoped(resizeModeKey), it) }
            payload.decodeSyncBoolean(holdToSpeedEnabledKey)?.let { store.putBoolean(scoped(holdToSpeedEnabledKey), it) }
            payload.decodeSyncFloat(holdToSpeedValueKey)?.let { store.putFloat(scoped(holdToSpeedValueKey), it) }
            payload.decodeSyncBoolean(touchGesturesEnabledKey)?.let { store.putBoolean(scoped(touchGesturesEnabledKey), it) }
            payload.decodeSyncBoolean(externalPlayerEnabledKey)?.let { store.putBoolean(scoped(externalPlayerEnabledKey), it) }
            payload.decodeSyncBoolean(externalPlayerForwardSubtitlesKey)?.let { store.putBoolean(scoped(externalPlayerForwardSubtitlesKey), it) }
            payload.decodeSyncBoolean(externalPlayerSendSkipSegmentsKey)?.let { store.putBoolean(scoped(externalPlayerSendSkipSegmentsKey), it) }
            payload.decodeSyncString(externalPlayerIdKey)?.let { store.putString(scoped(externalPlayerIdKey), it.takeIf(String::isNotBlank)) }
            payload.decodeSyncString(preferredAudioLanguageKey)?.let { store.putString(scoped(preferredAudioLanguageKey), it) }
            payload.decodeSyncString(secondaryPreferredAudioLanguageKey)?.let { store.putString(scoped(secondaryPreferredAudioLanguageKey), it.takeIf(String::isNotBlank)) }
            payload.decodeSyncString(preferredSubtitleLanguageKey)?.let { store.putString(scoped(preferredSubtitleLanguageKey), it) }
            payload.decodeSyncString(secondaryPreferredSubtitleLanguageKey)?.let { store.putString(scoped(secondaryPreferredSubtitleLanguageKey), it.takeIf(String::isNotBlank)) }
            payload.decodeSyncString(subtitleTextColorKey)?.let { store.putString(scoped(subtitleTextColorKey), it) }
            payload.decodeSyncString(subtitleBackgroundColorKey)?.let { store.putString(scoped(subtitleBackgroundColorKey), it) }
            payload.decodeSyncString(subtitleOutlineColorKey)?.let { store.putString(scoped(subtitleOutlineColorKey), it) }
            payload.decodeSyncBoolean(subtitleOutlineEnabledKey)?.let { store.putBoolean(scoped(subtitleOutlineEnabledKey), it) }
            payload.decodeSyncInt(subtitleOutlineWidthKey)?.let { store.putInt(scoped(subtitleOutlineWidthKey), it) }
            payload.decodeSyncBoolean(subtitleBoldKey)?.let { store.putBoolean(scoped(subtitleBoldKey), it) }
            payload.decodeSyncInt(subtitleFontSizeSpKey)?.let { store.putInt(scoped(subtitleFontSizeSpKey), it) }
            payload.decodeSyncInt(subtitleBottomOffsetKey)?.let { store.putInt(scoped(subtitleBottomOffsetKey), it) }
            payload.decodeSyncBoolean(subtitleStripSdhKey)?.let { store.putBoolean(scoped(subtitleStripSdhKey), it) }
            payload.decodeSyncBoolean(subtitleUseForcedSubtitlesKey)?.let { store.putBoolean(scoped(subtitleUseForcedSubtitlesKey), it) }
            payload.decodeSyncBoolean(subtitleShowOnlyPreferredLanguagesKey)?.let { store.putBoolean(scoped(subtitleShowOnlyPreferredLanguagesKey), it) }
            payload.decodeSyncString(addonSubtitleStartupModeKey)?.let { store.putString(scoped(addonSubtitleStartupModeKey), it) }
            payload.decodeSyncBoolean(streamReuseLastLinkEnabledKey)?.let { store.putBoolean(scoped(streamReuseLastLinkEnabledKey), it) }
            payload.decodeSyncInt(streamReuseLastLinkCacheHoursKey)?.let { store.putInt(scoped(streamReuseLastLinkCacheHoursKey), it) }
            payload.decodeSyncString(androidPlaybackEngineKey)?.let { store.putString(scoped(androidPlaybackEngineKey), it) }
            payload.decodeSyncBoolean(autoSwitchPlaybackEngineOnErrorKey)?.let { store.putBoolean(scoped(autoSwitchPlaybackEngineOnErrorKey), it) }
            payload.decodeSyncString(androidLibmpvVideoOutputKey)?.let { store.putString(scoped(androidLibmpvVideoOutputKey), it) }
            payload.decodeSyncBoolean(androidLibmpvHardwareDecodingEnabledKey)?.let { store.putBoolean(scoped(androidLibmpvHardwareDecodingEnabledKey), it) }
            payload.decodeSyncBoolean(androidLibmpvYuv420pEnabledKey)?.let { store.putBoolean(scoped(androidLibmpvYuv420pEnabledKey), it) }
            payload.decodeSyncBoolean(androidExoChaptersEnabledKey)?.let { store.putBoolean(scoped(androidExoChaptersEnabledKey), it) }
            payload.decodeSyncInt(decoderPriorityKey)?.let { store.putInt(scoped(decoderPriorityKey), it) }
            payload.decodeSyncBoolean(mapDV7ToHevcKey)?.let { store.putBoolean(scoped(mapDV7ToHevcKey), it) }
            payload.decodeSyncBoolean(tunnelingEnabledKey)?.let { store.putBoolean(scoped(tunnelingEnabledKey), it) }
            payload.decodeSyncString(streamAutoPlayModeKey)?.let { store.putString(scoped(streamAutoPlayModeKey), it) }
            payload.decodeSyncString(streamAutoPlaySourceKey)?.let { store.putString(scoped(streamAutoPlaySourceKey), it) }
            payload.decodeSyncStringSet(streamAutoPlaySelectedAddonsKey)?.let { store.putStringSet(scoped(streamAutoPlaySelectedAddonsKey), it) }
            payload.decodeSyncStringSet(streamAutoPlaySelectedPluginsKey)?.let { store.putStringSet(scoped(streamAutoPlaySelectedPluginsKey), it) }
            payload.decodeSyncString(streamAutoPlayRegexKey)?.let { store.putString(scoped(streamAutoPlayRegexKey), it) }
            payload.decodeSyncInt(streamAutoPlayTimeoutSecondsKey)?.let { store.putInt(scoped(streamAutoPlayTimeoutSecondsKey), it) }
            payload.decodeSyncBoolean(skipIntroEnabledKey)?.let { store.putBoolean(scoped(skipIntroEnabledKey), it) }
            payload.decodeSyncBoolean(preloadNextEpisodeSourcesKey)?.let { store.putBoolean(scoped(preloadNextEpisodeSourcesKey), it) }
            payload.decodeSyncBoolean(autoSkipMovieCreditsKey)?.let { store.putBoolean(scoped(autoSkipMovieCreditsKey), it) }
            payload.decodeSyncBoolean(autoSkipPostCreditsKey)?.let { store.putBoolean(scoped(autoSkipPostCreditsKey), it) }
            payload.decodeSyncStringSet(autoSkipSegmentTypesKey)?.let { store.putStringSet(scoped(autoSkipSegmentTypesKey), it) }
            payload.decodeSyncBoolean(animeSkipEnabledKey)?.let { store.putBoolean(scoped(animeSkipEnabledKey), it) }
            payload.decodeSyncString(animeSkipClientIdKey)?.let { store.putString(scoped(animeSkipClientIdKey), it) }
            payload.decodeSyncString(introDbApiKeyKey)?.let { store.putString(scoped(introDbApiKeyKey), it) }
            payload.decodeSyncBoolean(introSubmitEnabledKey)?.let { store.putBoolean(scoped(introSubmitEnabledKey), it) }
            payload.decodeSyncBoolean(streamAutoPlayNextEpisodeEnabledKey)?.let { store.putBoolean(scoped(streamAutoPlayNextEpisodeEnabledKey), it) }
            payload.decodeSyncBoolean(streamAutoPlayNextEpisodeFallbackEnabledKey)?.let { store.putBoolean(scoped(streamAutoPlayNextEpisodeFallbackEnabledKey), it) }
            payload.decodeSyncBoolean(streamAutoPlayPreferBingeGroupKey)?.let { store.putBoolean(scoped(streamAutoPlayPreferBingeGroupKey), it) }
            payload.decodeSyncBoolean(streamAutoPlayReuseBingeGroupKey)?.let { store.putBoolean(scoped(streamAutoPlayReuseBingeGroupKey), it) }
            payload.decodeSyncString(nextEpisodeThresholdModeKey)?.let { store.putString(scoped(nextEpisodeThresholdModeKey), it) }
            payload.decodeSyncFloat(nextEpisodeThresholdPercentKey)?.let { store.putFloat(scoped(nextEpisodeThresholdPercentKey), it) }
            payload.decodeSyncFloat(nextEpisodeThresholdMinutesBeforeEndKey)?.let { store.putFloat(scoped(nextEpisodeThresholdMinutesBeforeEndKey), it) }
            payload.decodeSyncBoolean(useLibassKey)?.let { store.putBoolean(scoped(useLibassKey), it) }
            payload.decodeSyncString(libassRenderTypeKey)?.let { store.putString(scoped(libassRenderTypeKey), it) }
            payload.decodeSyncString(libassRenderScaleKey)?.let { store.putString(scoped(libassRenderScaleKey), it) }
            payload.decodeSyncString(libassRenderModeKey)?.let { store.putString(scoped(libassRenderModeKey), it) }
            payload.decodeSyncString(iosVideoOutputPresetKey)?.let { store.putString(scoped(iosVideoOutputPresetKey), it) }
            payload.decodeSyncString(iosToneMappingModeKey)?.let { store.putString(scoped(iosToneMappingModeKey), it) }
            payload.decodeSyncString(iosTargetPrimariesKey)?.let { store.putString(scoped(iosTargetPrimariesKey), it) }
            payload.decodeSyncString(iosTargetTransferKey)?.let { store.putString(scoped(iosTargetTransferKey), it) }
            payload.decodeSyncString(iosHardwareDecoderModeKey)?.let { store.putString(scoped(iosHardwareDecoderModeKey), it) }
            payload.decodeSyncString(iosAudioOutputModeKey)?.let { store.putString(scoped(iosAudioOutputModeKey), it) }
            payload.decodeSyncBoolean(iosExtendedDynamicRangeEnabledKey)?.let { store.putBoolean(scoped(iosExtendedDynamicRangeEnabledKey), it) }
            payload.decodeSyncBoolean(iosTargetColorspaceHintEnabledKey)?.let { store.putBoolean(scoped(iosTargetColorspaceHintEnabledKey), it) }
            payload.decodeSyncBoolean(iosHdrComputePeakEnabledKey)?.let { store.putBoolean(scoped(iosHdrComputePeakEnabledKey), it) }
            payload.decodeSyncBoolean(iosDebandEnabledKey)?.let { store.putBoolean(scoped(iosDebandEnabledKey), it) }
            payload.decodeSyncBoolean(iosInterpolationEnabledKey)?.let { store.putBoolean(scoped(iosInterpolationEnabledKey), it) }
            payload.decodeSyncInt(iosBrightnessKey)?.let { store.putInt(scoped(iosBrightnessKey), it) }
            payload.decodeSyncInt(iosContrastKey)?.let { store.putInt(scoped(iosContrastKey), it) }
            payload.decodeSyncInt(iosSaturationKey)?.let { store.putInt(scoped(iosSaturationKey), it) }
            payload.decodeSyncInt(iosGammaKey)?.let { store.putInt(scoped(iosGammaKey), it) }
            payload.decodeSyncBoolean(nvidiaRtxSuperResolutionEnabledKey)?.let { store.putBoolean(scoped(nvidiaRtxSuperResolutionEnabledKey), it) }
        }
    }
}

internal class DesktopSubtitleRendererPreference(
    private val store: DesktopStorage.Store,
    private val profileKey: (String) -> String = ProfileScopedKey::of,
) {
    fun load(): Boolean? = store.getBoolean(profileKey("use_libass"))
    fun save(enabled: Boolean) = store.putBoolean(profileKey("use_libass"), enabled)
}
