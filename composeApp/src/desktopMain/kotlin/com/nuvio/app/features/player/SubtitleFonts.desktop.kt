package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import com.nuvio.app.core.storage.DesktopStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import javax.swing.SwingUtilities
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal actual object SubtitleFontStorage {
    actual fun defaultFamily(): FontFamily = FontFamily.SansSerif

    private fun file(name: String): Path {
        require(name == "fonts.json" || name.matches(Regex("custom-[a-f0-9-]{36}")))
        return DesktopStorage.rootDir.resolve("subtitle-fonts").resolve(name)
    }

    actual fun read(name: String): ByteArray? = file(name).takeIf {
        Files.isRegularFile(it) && Files.size(it) <= MaxSubtitleFontBytes
    }?.let(Files::readAllBytes)

    actual fun write(name: String, bytes: ByteArray) {
        val target = file(name)
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.write(temporary, bytes)
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
    }

    actual fun remove(name: String) {
        Files.deleteIfExists(file(name))
    }

    actual fun fontFamily(id: String): FontFamily? =
        file(id).takeIf(Files::isRegularFile)?.let { FontFamily(Font(id, Files.readAllBytes(it))) }
}

@Composable
internal actual fun rememberSubtitleFontImporter(onResult: (Result<SubtitleFont>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val latestResult = rememberUpdatedState(onResult)
    return {
        scope.launch {
            val result = runCatching {
                val path = withContext(Dispatchers.Swing) {
                    val parent = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
                        ?: Frame.getFrames().firstOrNull { it.isVisible }
                    val dialog = FileDialog(parent, "Import font", FileDialog.LOAD)
                    try {
                        dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".ttf") || it.endsWith(".otf") } }
                        dialog.isVisible = true
                        val name = dialog.file ?: return@withContext null
                        Path.of(dialog.directory, name)
                    } finally {
                        dialog.dispose()
                    }
                } ?: return@runCatching null
                withContext(Dispatchers.IO) {
                    require(Files.size(path) <= MaxSubtitleFontBytes)
                    SubtitleFonts.import(path.fileName.toString(), Files.readAllBytes(path))
                }
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            result.getOrNull()?.let { latestResult.value(Result.success(it)) }
                ?: result.exceptionOrNull()?.let { latestResult.value(Result.failure(it)) }
        }
    }
}

internal object SubtitleFontResolver {
    private val activeDir: Path by lazy { DesktopStorage.cacheDir.resolve("subtitle-font-active") }

    fun mpvFontName(id: String): String = when (id) {
        "serif" -> "serif"
        "mono" -> "monospace"
        "default", "sans" -> "sans-serif"
        else -> activate(id) ?: "sans-serif"
    }

    fun fontsDir(): String = activeDir.takeIf(Files::isDirectory)?.toString().orEmpty()

    fun resolveImported(id: String): String? = runCatching { activate(id) }.getOrNull()

    private var activeId: String? = null
    private var activeFamily: String? = null

    @Synchronized
    private fun activate(id: String): String? {
        if (activeId == id && Files.isRegularFile(DesktopStorage.rootDir.resolve("subtitle-fonts").resolve(id)) && Files.isDirectory(activeDir)) return activeFamily
        val bytes = SubtitleFontStorage.read(id) ?: return null
        val family = familyName(bytes) ?: return null
        Files.createDirectories(activeDir)
        Files.list(activeDir).use { it.forEach(Files::deleteIfExists) }
        Files.write(activeDir.resolve("$id.ttf"), bytes)
        activeId = id
        activeFamily = family
        return family
    }

    private fun familyName(bytes: ByteArray): String? = runCatching {
        java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, java.io.ByteArrayInputStream(bytes)).family
    }.getOrNull()

}
