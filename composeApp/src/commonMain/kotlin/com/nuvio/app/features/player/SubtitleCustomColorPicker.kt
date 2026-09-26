package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.HsvColor
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.formatHexColor
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.parseHexColor
import kotlin.math.abs
import kotlin.math.roundToInt
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.custom_theme_brightness
import nuvio.composeapp.generated.resources.custom_theme_hex_error
import nuvio.composeapp.generated.resources.custom_theme_hex_title
import nuvio.composeapp.generated.resources.custom_theme_hue
import nuvio.composeapp.generated.resources.custom_theme_saturation
import org.jetbrains.compose.resources.stringResource

enum class SubtitleColorEditTarget {
    TEXT,
    OUTLINE,
    BACKGROUND,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitleCustomColorPicker(
    selectedColor: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
    swatches: List<Color> = SubtitleColorSwatches,
    showAlpha: Boolean = false,
    previewStyle: SubtitleStyleState? = null,
    target: SubtitleColorEditTarget = SubtitleColorEditTarget.TEXT,
) {
    val tokens = MaterialTheme.nuvio
    val selectedRgb = remember(selectedColor) { selectedColor.toRgbInt() }
    var hsv by remember { mutableStateOf(HsvColor.fromRgb(selectedRgb)) }
    var emittedRgb by remember { mutableStateOf(selectedRgb) }
    var hexCode by remember { mutableStateOf(formatHexColor(selectedRgb)) }

    LaunchedEffect(selectedColor) {
        val currentRgb = selectedColor.toRgbInt()
        if (currentRgb != emittedRgb) {
            hsv = HsvColor.fromRgb(currentRgb)
            emittedRgb = currentRgb
            hexCode = formatHexColor(currentRgb)
        }
    }

    fun updateColorFromHsv(value: HsvColor) {
        hsv = value
        val rgb = value.toRgb()
        emittedRgb = rgb
        hexCode = formatHexColor(rgb)
        val newColor = Color(0xFF000000.toInt() or rgb).copy(alpha = selectedColor.alpha)
        onColorChanged(newColor)
    }

    fun updateColorFromHex(hex: String) {
        hexCode = hex
        parseHexColor(hex)?.let { rgb ->
            hsv = HsvColor.fromRgb(rgb)
            emittedRgb = rgb
            val newColor = Color(0xFF000000.toInt() or rgb).copy(alpha = selectedColor.alpha)
            onColorChanged(newColor)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
    ) {
        // Live Preview Box
        SubtitleLivePreviewCard(
            selectedColor = selectedColor,
            previewStyle = previewStyle,
            target = target,
        )

        // Swatches Row
        if (swatches.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
                verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
            ) {
                swatches.forEach { swatch ->
                    val isSelected = isSameColor(selectedColor, swatch)
                    Box(
                        modifier = Modifier
                            .size(NuvioTokens.Space.s36)
                            .clip(CircleShape)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    onColorChanged(swatch)
                                },
                            )
                            .semantics { contentDescription = swatch.toStorageHexString() }
                            .padding(2.dp)
                            .border(
                                tokens.borders.medium,
                                if (isSelected) tokens.colors.textPrimary else tokens.colors.borderDefault,
                                CircleShape,
                            )
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(if (swatch.alpha == 0f) Color.Transparent else swatch),
                    )
                }
            }
        }

        // HSV Sliders matching ThemeColorPicker
        ColorChannelSlider(
            title = stringResource(Res.string.custom_theme_hue),
            value = hsv.hue,
            valueRange = 0f..359f,
            valueLabel = "${hsv.hue.roundToInt()}°",
            brush = remember { Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f, 1f, 1f) }) },
            onValueChange = { updateColorFromHsv(hsv.copy(hue = it)) },
        )

        ColorChannelSlider(
            title = stringResource(Res.string.custom_theme_saturation),
            value = hsv.saturation,
            valueLabel = "${(hsv.saturation * 100).roundToInt()}%",
            brush = Brush.horizontalGradient(listOf(Color.White, Color.hsv(hsv.hue, 1f, 1f))),
            onValueChange = { updateColorFromHsv(hsv.copy(saturation = it)) },
        )

        ColorChannelSlider(
            title = stringResource(Res.string.custom_theme_brightness),
            value = hsv.brightness,
            valueLabel = "${(hsv.brightness * 100).roundToInt()}%",
            brush = Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hsv.hue, hsv.saturation, 1f))),
            onValueChange = { updateColorFromHsv(hsv.copy(brightness = it)) },
        )

        if (showAlpha) {
            ColorChannelSlider(
                title = "Opacity",
                value = selectedColor.alpha,
                valueLabel = "${(selectedColor.alpha * 100).roundToInt()}%",
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0x00000000),
                        Color(0xFF000000.toInt() or emittedRgb),
                    ),
                ),
                onValueChange = { alpha ->
                    onColorChanged(selectedColor.copy(alpha = alpha))
                },
            )
        }

        // Hex Code Input Field
        Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s4)) {
            val hexLabel = stringResource(Res.string.custom_theme_hex_title)
            Text(
                text = hexLabel,
                style = MaterialTheme.typography.labelLarge,
                color = tokens.colors.textPrimary,
            )
            NuvioInputField(
                value = hexCode,
                onValueChange = ::updateColorFromHex,
                placeholder = "#FFFFFF",
                modifier = Modifier.semantics { contentDescription = hexLabel },
            )
            if (hexCode.isNotEmpty() && parseHexColor(hexCode) == null) {
                Text(
                    text = stringResource(Res.string.custom_theme_hex_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.danger,
                )
            }
        }
    }
}

@Composable
private fun SubtitleLivePreviewCard(
    selectedColor: Color,
    previewStyle: SubtitleStyleState?,
    target: SubtitleColorEditTarget,
) {
    val style = previewStyle ?: SubtitleStyleState.DEFAULT
    val textColor = when (target) {
        SubtitleColorEditTarget.TEXT -> selectedColor
        else -> style.textColor
    }
    val outlineColor = when (target) {
        SubtitleColorEditTarget.OUTLINE -> selectedColor
        else -> style.outlineColor
    }
    val bgColor = when (target) {
        SubtitleColorEditTarget.BACKGROUND -> selectedColor
        else -> style.backgroundColor
    }
    val isBold = style.bold
    val outlineWidth = style.outlineWidth.coerceIn(1, 8)
    val fontFamily = getSubtitleFontFamily(style.fontName)

    val effectiveBgColor = when (style.outlineEffect) {
        SubtitleOutlineEffect.BACKGROUND_BOX -> {
            if (bgColor.alpha == 0f) Color.Black.copy(alpha = 0.70f) else bgColor
        }
        else -> bgColor
    }

    val showOutline = when (style.outlineEffect) {
        SubtitleOutlineEffect.OUTLINE,
        SubtitleOutlineEffect.OUTLINE_AND_SHADOW -> style.outlineEnabled
        else -> false
    }

    val showShadow = when (style.outlineEffect) {
        SubtitleOutlineEffect.OUTLINE_AND_SHADOW -> true
        else -> false
    }

    val showGlow = style.outlineEffect == SubtitleOutlineEffect.SOFT_GLOW

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141416))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(effectiveBgColor)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Drop shadow layer
            if (showShadow) {
                val shadowOffset = (outlineWidth + 1).dp
                Text(
                    text = "Sample Subtitle Text 123",
                    fontSize = 18.sp,
                    fontFamily = fontFamily,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    color = outlineColor.copy(alpha = 0.85f),
                    modifier = Modifier.offset(x = shadowOffset, y = shadowOffset),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            // Soft glow / blur layer
            if (showGlow) {
                val glowBlur = (outlineWidth * 1.5f).dp
                val strokeWidth = (outlineWidth * 2.5f).coerceAtLeast(4f)
                Box(modifier = Modifier.blur(radius = glowBlur)) {
                    Text(
                        text = "Sample Subtitle Text 123",
                        fontSize = 18.sp,
                        fontFamily = fontFamily,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.titleMedium.copy(
                            drawStyle = Stroke(width = strokeWidth, join = StrokeJoin.Round),
                            color = outlineColor.copy(alpha = 0.9f),
                        ),
                    )
                }
            }
            // Classic outline stroke layer
            if (showOutline) {
                Text(
                    text = "Sample Subtitle Text 123",
                    fontSize = 18.sp,
                    fontFamily = fontFamily,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.titleMedium.copy(
                        drawStyle = Stroke(width = outlineWidth.toFloat() * 1.5f, join = StrokeJoin.Round),
                        color = outlineColor,
                    ),
                )
            }
            // Foreground text layer
            Text(
                text = "Sample Subtitle Text 123",
                fontSize = 18.sp,
                fontFamily = fontFamily,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorChannelSlider(
    title: String,
    value: Float,
    valueLabel: String,
    brush: Brush,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    val tokens = MaterialTheme.nuvio
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = tokens.colors.textPrimary)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = tokens.colors.textSecondary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
            track = {
                Box(
                    Modifier.fillMaxWidth().height(NuvioTokens.Space.s8)
                        .clip(tokens.shapes.chip).background(brush),
                )
            },
        )
    }
}

private fun Color.toRgbInt(): Int {
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return (r shl 16) or (g shl 8) or b
}

private fun isSameColor(a: Color, b: Color): Boolean =
    abs(a.red - b.red) < 0.01f &&
        abs(a.green - b.green) < 0.01f &&
        abs(a.blue - b.blue) < 0.01f &&
        abs(a.alpha - b.alpha) < 0.02f
