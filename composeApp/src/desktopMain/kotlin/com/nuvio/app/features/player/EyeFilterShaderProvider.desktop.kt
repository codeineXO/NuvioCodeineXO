package com.nuvio.app.features.player

import com.nuvio.app.core.storage.DesktopCache

internal object EyeFilterShaderProvider {
    private const val BW_SHADER_NAME = "eye_filter_bw.glsl"
    private const val GRAIN_SHADER_NAME = "eye_filter_grain.glsl"

    private val installedDir by lazy {
        val files = buildMap {
            listOf(BW_SHADER_NAME, GRAIN_SHADER_NAME).forEach { fileName ->
                val resourcePath = "/shaders/$fileName"
                val stream = EyeFilterShaderProvider::class.java.getResourceAsStream(resourcePath)
                if (stream != null) {
                    put(fileName, stream.use { it.readBytes() })
                }
            }
        }
        if (files.isNotEmpty()) {
            DesktopCache.installVersionedFiles("eye-filter-shaders", files).toFile()
        } else {
            null
        }
    }

    fun getBwShaderPath(): String? {
        val dir = installedDir ?: return null
        val file = dir.resolve(BW_SHADER_NAME)
        return if (file.exists()) file.absolutePath.replace('\\', '/') else null
    }

    fun getGrainShaderPath(): String? {
        val dir = installedDir ?: return null
        val file = dir.resolve(GRAIN_SHADER_NAME)
        return if (file.exists()) file.absolutePath.replace('\\', '/') else null
    }
}
