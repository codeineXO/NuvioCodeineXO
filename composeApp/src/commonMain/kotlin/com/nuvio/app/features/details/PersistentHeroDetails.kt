package com.nuvio.app.features.details

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import com.nuvio.app.features.home.MetaPreview

internal data class PersistentHeroDetails(
    val preview: MetaPreview?,
    val backdrop: Painter?,
    val logo: Painter?,
    val showMetadataBadge: Boolean,
)

internal class PersistentHeroDetailsSource {
    /** Captures the hero currently shown for the title `type:id`, if any. */
    var capture: ((type: String, id: String) -> PersistentHeroDetails?)? = null
}

internal val LocalPersistentHeroDetailsSource = staticCompositionLocalOf<PersistentHeroDetailsSource?> { null }
