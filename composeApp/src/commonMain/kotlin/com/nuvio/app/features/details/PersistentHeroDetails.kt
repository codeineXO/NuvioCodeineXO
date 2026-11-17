package com.nuvio.app.features.details

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.navigation.DetailRoute

internal data class PersistentHeroDetails(
    val preview: MetaPreview?,
    val backdrop: Painter?,
    val logo: Painter?,
)

internal class PersistentHeroDetailsSource {
    var capture: ((DetailRoute) -> PersistentHeroDetails?)? = null
}

internal val LocalPersistentHeroDetailsSource = staticCompositionLocalOf<PersistentHeroDetailsSource?> { null }
