package com.nuvio.app.features.settings

import org.jetbrains.compose.resources.StringResource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_surface_style_flat
import nuvio.composeapp.generated.resources.settings_surface_style_dark_glass
import nuvio.composeapp.generated.resources.settings_surface_style_frosted_glass

enum class SurfaceStyle(val key: String) {
    Flat("flat"),
    DarkGlass("dark_glass"),
    FrostedGlass("frosted_glass");

    companion object {
        fun fromStored(value: String?, legacyFrostedEnabled: Boolean?): SurfaceStyle =
            entries.firstOrNull { it.key == value }
                ?: when (legacyFrostedEnabled) {
                    true -> FrostedGlass
                    false -> Flat
                    null -> DarkGlass
                }
    }
}

internal val SurfaceStyle.labelRes: StringResource
    get() = when (this) {
        SurfaceStyle.Flat -> Res.string.settings_surface_style_flat
        SurfaceStyle.DarkGlass -> Res.string.settings_surface_style_dark_glass
        SurfaceStyle.FrostedGlass -> Res.string.settings_surface_style_frosted_glass
    }
