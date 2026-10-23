package com.nuvio.app.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.serialization.json.*

val LocalAuraSettings = staticCompositionLocalOf { AuraSettings() }

enum class AuraPalette(val key: String) {
    ORIGINAL("original"), ARCTIC("arctic"), NORTHERN_LIGHTS("northern_lights"),
    SUNSET("sunset"), ROSE_QUARTZ("rose_quartz"), GOLDEN_HOUR("golden_hour"),
    DEEP_SPACE("deep_space"), MATCH_THEME("match_theme"), CUSTOM("custom");
}
enum class AuraIntensity(val key: String, val multiplier: Float) {
    SUBTLE("subtle", 0.55f), BALANCED("balanced", 1f), VIVID("vivid", 1.4f);
}
data class AuraSettings(
    val palette: AuraPalette = AuraPalette.ORIGINAL,
    val intensity: AuraIntensity = AuraIntensity.BALANCED,
    val animated: Boolean = true,
    val customColors: CustomThemeColors = CustomThemeColors.Default,
) {
    fun encode(): String = buildJsonObject {
        put("version", 1)
        put("palette", palette.key)
        put("intensity", intensity.key)
        put("animated", animated)
        put("colors", buildJsonArray { customColors.colors.forEach { add(formatHexColor(it)) } })
    }.toString()
    companion object {
        fun decode(value: String?): AuraSettings = runCatching {
            val json = Json.parseToJsonElement(value ?: return AuraSettings()).jsonObject
            if (json["version"]?.jsonPrimitive?.intOrNull != 1) return AuraSettings()
            val colors = runCatching {
                val values = json["colors"]!!.jsonArray.map { parseHexColor(it.jsonPrimitive.content) ?: error("Invalid color") }
                require(values.size == 3)
                CustomThemeColors(values[0], values[1], values[2])
            }.getOrDefault(CustomThemeColors.Default)
            AuraSettings(
                palette = AuraPalette.entries.find { it.key == json["palette"]?.jsonPrimitive?.contentOrNull } ?: AuraPalette.ORIGINAL,
                intensity = AuraIntensity.entries.find { it.key == json["intensity"]?.jsonPrimitive?.contentOrNull } ?: AuraIntensity.BALANCED,
                animated = json["animated"]?.jsonPrimitive?.booleanOrNull ?: true,
                customColors = colors,
            )
        }.getOrDefault(AuraSettings())
    }
}
data class AuraGlow(val inner: Color, val outer: Color)
fun resolveAuraPalette(settings: AuraSettings, theme: ThemeColorPalette): List<AuraGlow> {
    fun color(rgb: Int) = Color(rgb or 0xFF000000.toInt())
    if (settings.palette == AuraPalette.ORIGINAL) return listOf(
        AuraGlow(color(0x6366F1), color(0x4338CA)),
        AuraGlow(color(0x06B6D4), color(0x0284C7)),
        AuraGlow(color(0xD946EF), color(0x9333EA)),
        AuraGlow(color(0xF43F5E), color(0xE11D48)),
    )
    val colors = when (settings.palette) {
        AuraPalette.ARCTIC -> listOf(0x38BDF8, 0x22D3EE, 0x3B82F6, 0x818CF8).map(::color)
        AuraPalette.NORTHERN_LIGHTS -> listOf(0x22C55E, 0x14B8A6, 0x8B5CF6, 0x06B6D4).map(::color)
        AuraPalette.SUNSET -> listOf(0xFB7185, 0xFB923C, 0xD946EF, 0xF43F5E).map(::color)
        AuraPalette.ROSE_QUARTZ -> listOf(0xF9A8D4, 0xC084FC, 0xA78BFA, 0xFDA4AF).map(::color)
        AuraPalette.GOLDEN_HOUR -> listOf(0xFBBF24, 0xF59E0B, 0xD97706, 0xFB923C).map(::color)
        AuraPalette.DEEP_SPACE -> listOf(0x1D4ED8, 0x4338CA, 0x7C3AED, 0x6366F1).map(::color)
        AuraPalette.CUSTOM -> settings.customColors.colors.map(::color).let { it + lerp(it[0], it[2], 0.5f) }
        AuraPalette.MATCH_THEME -> {
            val stops = theme.accentGradient.ifEmpty { listOf(theme.secondary) }
            List(4) { index ->
                val position = index / 3f * (stops.size - 1)
                val start = position.toInt()
                lerp(stops[start], stops[(start + 1).coerceAtMost(stops.lastIndex)], position - start)
            }
        }
        AuraPalette.ORIGINAL -> error("Handled above")
    }
    return colors.map { AuraGlow(it, it) }
}
