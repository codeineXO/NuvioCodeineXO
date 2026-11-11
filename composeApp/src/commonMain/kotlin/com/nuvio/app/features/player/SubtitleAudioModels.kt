package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nuvio.app.isDesktop
import com.nuvio.app.isIos
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_track_number
import org.jetbrains.compose.resources.stringResource
import kotlin.math.pow
import kotlin.math.roundToInt

data class AudioTrack(
    val index: Int,
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
)

data class SubtitleTrack(
    val index: Int,
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
    val isForced: Boolean = false,
)

data class AddonSubtitle(
    val id: String,
    val url: String,
    val language: String,
    val display: String,
    val addonName: String? = null,
    val isSelected: Boolean = false,
    val label: String? = null,
)

internal enum class SubtitleTab {
    BuiltIn,
    Addons,
    Style,
}

const val SUBTITLE_DELAY_MIN_MS = -60_000
const val SUBTITLE_DELAY_MAX_MS = 60_000
const val SUBTITLE_DELAY_STEP_MS = 100
const val SUBTITLE_AUTO_SYNC_REACTION_COMPENSATION_MS = 300L
const val AUDIO_DELAY_MIN_MS = -60_000
const val AUDIO_DELAY_MAX_MS = 60_000
const val AUDIO_DELAY_STEP_MS = 25
const val AUDIO_AMPLIFICATION_MIN_DB = 0
const val AUDIO_AMPLIFICATION_MAX_DB = 10
/** Center channel level in the FFmpeg downmix, relative to the stream's own (or -3 dB). */
const val CENTER_MIX_LEVEL_MIN_DB = -10
const val CENTER_MIX_LEVEL_MAX_DB = 30

/** Speaker layouts the FFmpeg downmix can produce. */
enum class AudioOutputChannels(
    val settingValue: String,
    val channelCount: Int,
    val ffmpegLayoutName: String,
) {
    CHANNELS_2_0("2.0", 2, "stereo"),
    CHANNELS_2_1("2.1", 3, "2.1"),
    CHANNELS_3_0("3.0", 3, "3.0"),
    CHANNELS_3_1("3.1", 4, "3.1"),
    CHANNELS_4_0("4.0", 4, "4.0"),
    CHANNELS_4_1("4.1", 5, "4.1"),
    CHANNELS_5_0("5.0", 5, "5.0"),
    CHANNELS_5_1("5.1", 6, "5.1"),
    CHANNELS_7_0("7.0", 7, "7.0"),
    CHANNELS_7_1("7.1", 8, "7.1");

    companion object {
        /** Phones and tablets play stereo. */
        val default = CHANNELS_2_0

        fun fromSettingValue(value: String?): AudioOutputChannels =
            entries.firstOrNull { it.settingValue == value } ?: default
    }
}

/** The downmix setting only works through the app's FFmpeg audio decoder ("Prefer app decoders"). */
val PlayerSettingsUiState.effectiveDownmixEnabled: Boolean
    get() = downmixEnabled && decoderPriority == 2

/** Linear gain factor for an amplification of [db] decibels. */
internal fun audioAmplificationGain(db: Int): Double =
    10.0.pow(db.coerceIn(AUDIO_AMPLIFICATION_MIN_DB, AUDIO_AMPLIFICATION_MAX_DB) / 20.0)

internal val subtitleFontSizeRangeSp: IntRange
    get() = if (isDesktop || isIos) 6..40 else 12..40

enum class SubtitleOutlineEffect(val id: String, val label: String) {
    OUTLINE("outline", "Classic Outline"),
    SOFT_GLOW("soft_glow", "Glow"),
    OUTLINE_AND_SHADOW("outline_shadow", "Halo"),
    BACKGROUND_BOX("background_box", "Background Box"),
    NONE("none", "None");

    /**
     * Whether this effect needs a stroked border to render at all. The effects
     * that build their look on top of an outline must never be zeroed out by a
     * stale `outlineEnabled` flag, otherwise they degrade to plain text.
     */
    val needsOutline: Boolean
        get() = this != NONE && this != BACKGROUND_BOX

    companion object {
        fun fromId(id: String?): SubtitleOutlineEffect =
            entries.firstOrNull { it.id == id } ?: OUTLINE_AND_SHADOW
    }
}

val SubtitleFontOptions = listOf(
    "Trebuchet MS",
    "Segoe UI",
    "Arial",
    "Verdana",
    "Tahoma",
    "Calibri",
    "Georgia",
    "Impact",
    "Comic Sans MS",
    "Consolas",
)

data class SubtitleStyleState(
    val textColor: Color = Color(0xFFFFF3B2),
    val backgroundColor: Color = Color.Transparent,
    val outlineColor: Color = Color.Black,
    val outlineEnabled: Boolean = true,
    val outlineWidth: Float = 1f,
    val outlineEffect: SubtitleOutlineEffect = SubtitleOutlineEffect.OUTLINE_AND_SHADOW,
    val fontName: String = "Arial",
    val importedFontId: String? = null,
    val bold: Boolean = true,
    val fontSizeSp: Int = 10,
    val bottomOffset: Int = 10,
    val stripSdh: Boolean = false,
    val useForcedSubtitles: Boolean = false,
    val showOnlyPreferredLanguages: Boolean = false,
    val appearance: SubtitleAppearance = SubtitleAppearance(),
) {
    companion object {
        val DEFAULT = SubtitleStyleState()
    }
}

data class SubtitleSyncCue(
    val startTimeMs: Long,
    val endTimeMs: Long = startTimeMs + 5_000L,
    val text: String,
)

data class SubtitleAutoSyncUiState(
    val capturedPositionMs: Long? = null,
    val cues: List<SubtitleSyncCue> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

val SubtitleColorSwatches = listOf(
    Color.White,
    SubtitleStyleState.DEFAULT.textColor,
    Color(0xFF00E5FF),
    Color(0xFFFF5C5C),
    Color(0xFF00FF88),
    Color(0xFF9B59B6),
    Color(0xFFFFD700),
    Color(0xFF22C55E),
    Color(0xFF3B82F6),
    Color.Black,
)

val SubtitleBackgroundColorSwatches = listOf(
    Color.Transparent,
    Color.Black.copy(alpha = 0.55f),
    Color(0xFF111827).copy(alpha = 0.72f),
    Color(0xFF7F1D1D).copy(alpha = 0.68f),
    Color(0xFF064E3B).copy(alpha = 0.68f),
    Color(0xFF1E3A8A).copy(alpha = 0.68f),
)

val SubtitleOutlineColorSwatches = listOf(
    Color.Black,
    Color.White,
    Color(0xFF00E5FF),
    Color(0xFFFF5C5C),
    Color(0xFFFFD700),
    Color(0xFF00FF88),
    Color(0xFF9B59B6),
)

internal val SubtitleOutlineWidths = listOf(0.5f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f)

internal fun normalizeSubtitleOutlineWidth(width: Float): Float {
    val finiteWidth = width.takeIf { it.isFinite() } ?: 1f
    return SubtitleOutlineWidths.minBy { kotlin.math.abs(it - finiteWidth) }
}

internal fun stepSubtitleOutlineWidth(width: Float, direction: Int): Float {
    val index = SubtitleOutlineWidths.indexOf(normalizeSubtitleOutlineWidth(width))
    return SubtitleOutlineWidths[(index + direction.coerceIn(-1, 1)).coerceIn(SubtitleOutlineWidths.indices)]
}

internal fun Float.outlineWidthLabel(): String = if (this == toInt().toFloat()) toInt().toString() else toString()

internal fun SubtitleStyleState.withOutlineColor(color: Color): SubtitleStyleState =
    copy(outlineColor = color.copy(alpha = outlineColor.alpha))

internal fun SubtitleStyleState.withOutlineOpacity(percent: Int): SubtitleStyleState =
    copy(outlineColor = outlineColor.copy(alpha = percent.coerceIn(0, 100) / 100f))

internal val SubtitleStyleState.backgroundBoxColor: Color
    get() = if (backgroundColor.alpha == 0f && !appearance.backgroundBoxColorConfigured) {
        Color.Black.copy(alpha = 0.65f)
    } else backgroundColor

internal val SubtitleStyleState.effectColor: Color
    get() = if (outlineEffect == SubtitleOutlineEffect.BACKGROUND_BOX) backgroundBoxColor else outlineColor

internal fun SubtitleStyleState.withEffectColor(color: Color): SubtitleStyleState =
    if (outlineEffect == SubtitleOutlineEffect.BACKGROUND_BOX) {
        copy(
            backgroundColor = color.copy(alpha = backgroundBoxColor.alpha),
            appearance = appearance.copy(backgroundBoxColorConfigured = true),
        )
    } else withOutlineColor(color)

internal fun SubtitleStyleState.withEffectOpacity(percent: Int): SubtitleStyleState =
    if (outlineEffect == SubtitleOutlineEffect.BACKGROUND_BOX) {
        copy(
            backgroundColor = backgroundBoxColor.copy(alpha = percent.coerceIn(0, 100) / 100f),
            appearance = appearance.copy(backgroundBoxColorConfigured = true),
        )
    } else withOutlineOpacity(percent)

internal fun SubtitleStyleState.withEffectSwatch(index: Int): SubtitleStyleState =
    SubtitleOutlineColorSwatches.getOrNull(index)?.let { withEffectColor(it) } ?: this

fun Color.toStorageHexString(): String {
    fun component(value: Float): String =
        (value * 255f).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()

    return buildString {
        append('#')
        append(component(alpha))
        append(component(red))
        append(component(green))
        append(component(blue))
    }
}

fun subtitleColorFromStorage(value: String?): Color? {
    val normalized = value
        ?.trim()
        ?.removePrefix("#")
        ?.takeIf { it.length == 6 || it.length == 8 }
        ?: return null

    val argb = if (normalized.length == 6) {
        "FF$normalized"
    } else {
        normalized
    }

    val parsed = argb.toLongOrNull(16) ?: return null
    return Color(
        red = ((parsed shr 16) and 0xFF).toFloat() / 255f,
        green = ((parsed shr 8) and 0xFF).toFloat() / 255f,
        blue = (parsed and 0xFF).toFloat() / 255f,
        alpha = ((parsed shr 24) and 0xFF).toFloat() / 255f,
    )
}

data class SubtitleAudioUiState(
    val audioTracks: List<AudioTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val addonSubtitles: List<AddonSubtitle> = emptyList(),
    val isLoadingAddonSubtitles: Boolean = false,
    val addonSubtitleError: String? = null,
    val selectedAudioIndex: Int = -1,
    val selectedSubtitleIndex: Int = -1,
    val selectedAddonSubtitleId: String? = null,
    val useCustomSubtitles: Boolean = false,
    val subtitleStyle: SubtitleStyleState = SubtitleStyleState.DEFAULT,
    val showAudioModal: Boolean = false,
    val showSubtitleModal: Boolean = false,
)

@Composable
fun localizedTrackDisplayName(label: String?, language: String?, index: Int): String {
    if (!label.isNullOrBlank()) return label
    if (!language.isNullOrBlank()) return languageLabelForCode(language)
    return stringResource(Res.string.compose_player_track_number, index + 1)
}
