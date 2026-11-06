package com.nuvio.app.features.player

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SubtitleAppearance(
    val fontId: String = "default",
    val backgroundRadius: Int = 0,
    val shadowEnabled: Boolean = false,
    val shadowOpacity: Int = 75,
    val shadowSize: Int = 2,
    // Fractional override; integer widths remain in the legacy platform setting.
    val fractionalOutlineWidth: Float? = null,
    // Distinguishes an explicitly transparent box from the legacy default.
    val backgroundBoxColorConfigured: Boolean = false,
) {
    fun normalized(): SubtitleAppearance = copy(
        fontId = fontId.takeIf { it in BuiltInSubtitleFonts || it.matches(Regex("custom-[a-f0-9-]{36}")) } ?: "default",
        backgroundRadius = backgroundRadius.coerceIn(0, 24),
        shadowOpacity = shadowOpacity.coerceIn(0, 100),
        shadowSize = shadowSize.coerceIn(1, 8),
        fractionalOutlineWidth = fractionalOutlineWidth?.takeIf { it == 0.5f },
    )
}

internal val BuiltInSubtitleFonts = listOf("default", "sans", "serif", "mono")
private val subtitleAppearanceJson = Json { ignoreUnknownKeys = true }

internal fun decodeSubtitleAppearance(value: String?): SubtitleAppearance =
    value?.let { runCatching { subtitleAppearanceJson.decodeFromString<SubtitleAppearance>(it).normalized() }.getOrNull() }
        ?: SubtitleAppearance()

internal fun SubtitleAppearance.encode(): String = subtitleAppearanceJson.encodeToString(normalized())

internal fun SubtitleAppearance.resolveOutlineWidth(legacyWidth: Int?): Float =
    fractionalOutlineWidth ?: normalizeSubtitleOutlineWidth(legacyWidth?.toFloat() ?: SubtitleStyleState.DEFAULT.outlineWidth)

internal fun SubtitleStyleState.appearanceForStorage(): SubtitleAppearance = appearance.copy(
    fontId = importedFontId ?: "default",
    fractionalOutlineWidth = outlineWidth.takeIf { it == 0.5f },
)

internal val SubtitleStyleState.usesStyledOverlay: Boolean
    get() = appearance.fontId != "default" || backgroundColor.alpha > 0f || appearance.shadowEnabled

object SubtitleRendering {
    fun supportsTextOverlay(codec: String): Boolean = codec.lowercase() in setOf(
        "subrip", "srt", "webvtt", "text", "mov_text", "microdvd", "mpl2", "subviewer", "subviewer1", "sami", "realtext",
    )
}

internal fun SubtitleStyleState.resetAppearance(): SubtitleStyleState = SubtitleStyleState.DEFAULT.copy(
    stripSdh = stripSdh,
    useForcedSubtitles = useForcedSubtitles,
    showOnlyPreferredLanguages = showOnlyPreferredLanguages,
)
