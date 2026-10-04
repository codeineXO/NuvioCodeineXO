package com.nuvio.app.features.player

import com.nuvio.app.core.storage.DesktopCache
import java.io.File

internal object AnimeShaderProvider {
    private val shaderResourceMap = mapOf(
        AnimeUpscalerMode.FAST to "anime4k_fast.glsl",
        AnimeUpscalerMode.LINE_RECOVERY to "anime4k_line_recovery.glsl",
        AnimeUpscalerMode.SHARP_DETAIL to "anime4k_sharp_hq.glsl",
    )

    private val installedDir by lazy {
        val files = buildMap {
            shaderResourceMap.values.distinct().forEach { fileName ->
                val resourcePath = "/shaders/$fileName"
                val stream = AnimeShaderProvider::class.java.getResourceAsStream(resourcePath)
                if (stream != null) {
                    put(fileName, stream.use { it.readBytes() })
                }
            }
        }
        if (files.isNotEmpty()) {
            DesktopCache.installVersionedFiles("anime-shaders", files).toFile()
        } else {
            null
        }
    }

    fun getShaderPath(mode: AnimeUpscalerMode): String? {
        val fileName = shaderResourceMap[mode] ?: return null
        val dir = installedDir ?: return null
        val file = dir.resolve(fileName)
        return if (file.exists()) {
            file.absolutePath.replace('\\', '/')
        } else {
            null
        }
    }
}
