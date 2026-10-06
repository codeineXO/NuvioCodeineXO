package com.nuvio.app.features.p2p

import java.io.File
import java.util.Locale

object StremioEngineBinary {
    const val RUNTIME_FILE_NAME: String = "stremio-runtime.exe"
    const val SERVER_SCRIPT_NAME: String = "server.js"

    data class ResolvedEngine(
        val runtime: File,
        val script: File,
        val workingDir: File? = null
    )

    @Volatile
    private var resolved: ResolvedEngine? = null

    val isAvailable: Boolean
        get() {
            val osName = (System.getProperty("os.name") ?: "").lowercase(Locale.ROOT)
            return osName.contains("win") && resolve() != null
        }

    fun resolve(): ResolvedEngine? {
        resolved?.takeIf { it.runtime.isFile && it.script.isFile }?.let { return it }

        // Always use the bundled copy. Using a user's installed Stremio would let our orphan cleanup kill their app.

        // 2. Check bundled resources / build directories
        candidates().firstOrNull { it.runtime.isFile && it.script.isFile }?.let {
            resolved = it
            return it
        }

        // 3. Fallback: extract from resources (packaged JAR)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "nuvio-stremio-engine")
        tempDir.mkdirs()
        val targetRuntime = File(tempDir, RUNTIME_FILE_NAME)
        val targetScript = File(tempDir, SERVER_SCRIPT_NAME)

        val runtimeStream = StremioEngineBinary::class.java.getResourceAsStream("/native/windows/$RUNTIME_FILE_NAME")
            ?: StremioEngineBinary::class.java.getResourceAsStream("/$RUNTIME_FILE_NAME")
        val scriptStream = StremioEngineBinary::class.java.getResourceAsStream("/native/windows/$SERVER_SCRIPT_NAME")
            ?: StremioEngineBinary::class.java.getResourceAsStream("/$SERVER_SCRIPT_NAME")

        if (runtimeStream != null && scriptStream != null) {
            runCatching {
                runtimeStream.use { input ->
                    targetRuntime.outputStream().use { output -> input.copyTo(output) }
                }
                scriptStream.use { input ->
                    targetScript.outputStream().use { output -> input.copyTo(output) }
                }
                if (targetRuntime.isFile && targetScript.isFile) {
                    val engine = ResolvedEngine(targetRuntime, targetScript, workingDir = tempDir)
                    resolved = engine
                    return engine
                }
            }
        }

        return null
    }

    private fun candidates(): List<ResolvedEngine> {
        val baseDirs = listOfNotNull(
            System.getProperty("java.home")?.takeIf { it.isNotBlank() }?.let { File(it).parentFile?.resolve("bin") },
            System.getProperty("java.home")?.takeIf { it.isNotBlank() }?.let { File(it).parentFile },
            File("composeApp/src/desktopMain/resources/native/windows"),
            File("composeApp/build/native/windows"),
            File("build/native/windows"),
            File(System.getProperty("java.io.tmpdir"), "nuvio-stremio-engine"),
        )
        return baseDirs.mapNotNull { dir ->
            val runtime = File(dir, RUNTIME_FILE_NAME)
            val script = File(dir, SERVER_SCRIPT_NAME)
            if (runtime.isFile && script.isFile) {
                ResolvedEngine(runtime, script, workingDir = dir)
            } else {
                null
            }
        }
    }
}
