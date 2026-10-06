package com.nuvio.app.features.p2p

import java.io.File
import java.util.Locale

object StremioEngineBinary {
    const val EXECUTABLE_FILE_NAME: String = "stremio-server.exe"

    @Volatile
    private var resolvedFile: File? = null

    val isAvailable: Boolean
        get() {
            val osName = (System.getProperty("os.name") ?: "").lowercase(Locale.ROOT)
            return osName.contains("win") && resolve() != null
        }

    fun resolve(): File? {
        resolvedFile?.takeIf { it.isFile }?.let { return it }

        configuredPath()?.takeIf { it.isFile }?.let {
            resolvedFile = it
            return it
        }

        candidates().firstOrNull { it.isFile }?.let {
            resolvedFile = it
            return it
        }

        // Fallback: extract from resources (e.g. running from packaged JAR)
        val stream = StremioEngineBinary::class.java.getResourceAsStream("/native/windows/$EXECUTABLE_FILE_NAME")
            ?: StremioEngineBinary::class.java.getResourceAsStream("/$EXECUTABLE_FILE_NAME")

        if (stream != null) {
            try {
                val tempDir = File(System.getProperty("java.io.tmpdir"), "nuvio-stremio-engine")
                tempDir.mkdirs()
                val targetFile = File(tempDir, EXECUTABLE_FILE_NAME)
                stream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.isFile) {
                    resolvedFile = targetFile
                    return targetFile
                }
            } catch (_: Throwable) {
                // Ignore extraction failure
            }
        }

        return null
    }

    private fun configuredPath(): File? {
        val configured = System.getProperty("stremio.engine.binary")?.takeIf { it.isNotBlank() }
            ?: System.getenv("STREMIO_ENGINE_BINARY")?.takeIf { it.isNotBlank() }
        return configured?.let { File(it) }
    }

    private fun candidates(): List<File> {
        val packaged = System.getProperty("java.home")?.takeIf { it.isNotBlank() }?.let {
            File(it).parentFile?.resolve("bin/$EXECUTABLE_FILE_NAME")
                ?: File(it).parentFile?.resolve(EXECUTABLE_FILE_NAME)
        }
        return listOfNotNull(
            packaged,
            File("composeApp/src/desktopMain/resources/native/windows/$EXECUTABLE_FILE_NAME"),
            File("composeApp/build/native/windows/$EXECUTABLE_FILE_NAME"),
            File("build/native/windows/$EXECUTABLE_FILE_NAME"),
            File(System.getProperty("java.io.tmpdir"), "nuvio-stremio-engine/$EXECUTABLE_FILE_NAME"),
        )
    }
}
