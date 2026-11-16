package com.nuvio.app.features.home.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.nuvio.app.core.ui.AuraGlow
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skiko.GraphicsApi

@Composable
internal fun rememberDesktopAuroraRenderer(
    window: ComposeWindow,
    background: Color,
    colors: List<AuraGlow>,
    intensity: Float,
): AuroraRenderer {
    val renderer = remember(window, background, colors, intensity) {
        DesktopAuroraRenderer(window, background, colors, intensity)
    }
    DisposableEffect(renderer) {
        onDispose { renderer.close() }
    }
    return renderer
}

private class DesktopAuroraRenderer(
    private val window: ComposeWindow,
    background: Color,
    colors: List<AuraGlow>,
    intensity: Float,
) : AuroraRenderer, AutoCloseable {
    private val shaderBuilder = lazy {
        RuntimeShaderBuilder(auroraEffect).apply {
            uniformColor("background", background)
            colors.forEachIndexed { index, color ->
                uniformColor("outer$index", color.outer)
                uniformColor("inner$index", color.inner)
            }
            uniform("intensity", intensity)
        }
    }
    private val paint = Paint()

    override fun draw(scope: DrawScope, phase: Float, heroBottomPx: Float, heroFadePx: Float): Boolean {
        val (width, height) = scope.size
        if (width <= 0f || height <= 0f) return true
        // Software rendering uses the mesh to keep the full-window animation responsive.
        when (window.renderApi) {
            GraphicsApi.UNKNOWN, GraphicsApi.SOFTWARE_FAST, GraphicsApi.SOFTWARE_COMPAT -> return false
            else -> Unit
        }
        val builder = shaderBuilder.value
        builder.uniform("viewport", width, height)
        builder.uniform("phase", phase)
        builder.uniform("heroBottom", heroBottomPx)
        builder.uniform("heroFade", heroFadePx)
        builder.makeShader().use { shader ->
            paint.shader = shader
            try {
                scope.drawContext.canvas.nativeCanvas.drawRect(Rect.makeWH(width, height), paint)
            } finally {
                paint.shader = null
            }
        }
        return true
    }

    override fun close() {
        paint.close()
        if (shaderBuilder.isInitialized()) shaderBuilder.value.close()
    }
}

private fun RuntimeShaderBuilder.uniformColor(name: String, color: Color) {
    val srgb = color.convert(ColorSpaces.Srgb)
    uniform(name, srgb.red, srgb.green, srgb.blue, srgb.alpha)
}

private val auroraEffect by lazy {
    RuntimeEffect.makeForShader(
        """
        uniform float2 viewport;
        uniform float phase;
        uniform float intensity;
        uniform float heroBottom;
        uniform float heroFade;

        layout(color) uniform float4 background;
        layout(color) uniform float4 outer0;
        layout(color) uniform float4 outer1;
        layout(color) uniform float4 outer2;
        layout(color) uniform float4 outer3;
        layout(color) uniform float4 inner0;
        layout(color) uniform float4 inner1;
        layout(color) uniform float4 inner2;
        layout(color) uniform float4 inner3;

        float3 palette(float t, float3 a, float3 b, float3 c, float3 d) {
            if (t < 1.0) return mix(a, b, t);
            if (t < 2.0) return mix(b, c, t - 1.0);
            return mix(c, d, t - 2.0);
        }

        float falloff(float distance) {
            return exp(-distance * distance);
        }

        half4 main(float2 pixel) {
            float2 uv = pixel / viewport;
            float x = uv.x;
            float y = uv.y;

            // Send a shallow wave along the main curve while its overall position stays anchored.
            float swell = sin(x * 9.0 - phase) * (0.65 + 0.35 * sin(x * 3.14159));
            float ridge = 0.55 - 0.19 * (x - 0.5) + 0.10 * sin(x * 6.3 + 0.6)
                + 0.025 * sin(x * 13.0 - 0.7) + 0.032 * swell;
            float distance = y - ridge;
            float band = falloff(distance / 0.085);
            float veil = falloff((distance + 0.12) / 0.34);
            float fringe = falloff((distance - 0.12) / 0.25);
            float opacity = intensity * (0.017 + 0.13 * band + 0.076 * veil + 0.047 * fringe);

            float t = 3.0 * clamp(0.94 * x + 0.18 * y - 0.06 + 0.017 * swell, 0.0, 1.0);
            float3 outer = palette(t, outer0.rgb, outer1.rgb, outer2.rgb, outer3.rgb);
            float3 inner = palette(t, inner0.rgb, inner1.rgb, inner2.rgb, inner3.rgb);
            float3 light = mix(outer, inner, 0.35 + 0.65 * band);

            float heroMask = clamp(heroBottom / heroFade, 0.0, 1.0)
                * (1.0 - smoothstep(heroBottom, heroBottom + heroFade, pixel.y));
            float3 rgb = mix(background.rgb, light, opacity * (1.0 - 0.78 * heroMask));

            // Dither the final color by at most one 8-bit step. The pattern is fixed to the screen.
            float threshold = fract(52.9829189 * fract(dot(floor(pixel), float2(0.06711056, 0.00583715))));
            rgb = floor(clamp(rgb, 0.0, 1.0) * 255.0 + threshold) / 255.0;
            return half4(half3(rgb), 1.0);
        }
        """.trimIndent(),
    )
}
