package com.nuvio.app.features.player

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Typeface
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

actual fun getSubtitleFontFamily(fontName: String): FontFamily =
    runCatching {
        val typeface = FontMgr.default.matchFamilyStyle(fontName, FontStyle.NORMAL)
        typeface?.let { FontFamily(Typeface(it)) }
    }.getOrNull() ?: FontFamily.SansSerif
