package com.nuvio.app.features.player

import com.nuvio.app.core.storage.DesktopCache

internal enum class SvpProfile(val fileName: String) {
    ANIME("svp_anime.vpy"),
    CINEMA("svp_cinema.vpy");

    companion object {
        fun fromString(value: String?): SvpProfile? = when (value?.lowercase()) {
            "anime" -> ANIME
            "cinema" -> CINEMA
            else -> null
        }
    }
}

internal object SvpScriptProvider {
    private val installedDir by lazy {
        val files = buildMap {
            SvpProfile.entries.forEach { profile ->
                val resourcePath = "/vapoursynth/${profile.fileName}"
                val stream = SvpScriptProvider::class.java.getResourceAsStream(resourcePath)
                if (stream != null) {
                    put(profile.fileName, stream.use { it.readBytes() })
                }
            }
        }
        if (files.isNotEmpty()) {
            DesktopCache.installVersionedFiles("vapoursynth-scripts", files).toFile()
        } else {
            null
        }
    }

    fun getScriptPath(profile: SvpProfile): String? {
        if (com.nuvio.app.features.player.desktop.DesktopHostOs.current ==
            com.nuvio.app.features.player.desktop.DesktopHostOs.WINDOWS
        ) {
            return SvpRuntimeInstaller.runtimeDirectory?.resolve(profile.fileName)?.toFile()
                ?.takeIf { it.isFile }?.absolutePath?.replace('\\', '/')
        }
        val dir = installedDir ?: return null
        val file = dir.resolve(profile.fileName)
        return if (file.exists()) {
            file.absolutePath.replace('\\', '/')
        } else {
            null
        }
    }
}
