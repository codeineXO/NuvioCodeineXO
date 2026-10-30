package com.nuvio.app.features.downloads

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.KeyboardFocusManager
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

internal object DesktopDownloadLocation : DownloadLocationPlatform {
    private val settings get() = DesktopStorage.store("nuvio_download_locations")
    private val defaultDirectory get() = DesktopStorage.rootDir.resolve("downloads").toFile()

    fun install() {
        DownloadLocation.platform = this
    }

    fun directory(profileId: Int = ProfileRepository.activeProfileId): File =
        settings.getString(ProfileScopedKey.of("directory", profileId))?.let(::File) ?: defaultDirectory

    private fun migration(profileId: Int) = DownloadFolderMigration(
        DesktopStorage.rootDir.resolve("download-migration-$profileId.json").toFile(),
    ) { path, payload ->
        val downloads = DesktopStorage.store("nuvio_downloads")
        downloads.putString(ProfileScopedKey.of("downloads", profileId), payload)
        downloads.flushChecked()
        settings.putString(ProfileScopedKey.of("directory", profileId), path.takeUnless { File(it) == defaultDirectory })
        settings.flushChecked()
    }

    fun recover(profileId: Int = ProfileRepository.activeProfileId) {
        val activeIds = migration(profileId).recover()
        if (activeIds.isNotEmpty()) DownloadLocation.recoveredActiveIds = activeIds
    }

    override fun currentPath(): String = directory().absolutePath

    override fun isCurrentPath(path: String?): Boolean =
        (path?.let(::File) ?: defaultDirectory).canonicalFile == directory().canonicalFile

    override suspend fun pickFolder(): String? {
        val title = getString(Res.string.downloads_choose_directory)
        return suspendCancellableCoroutine { continuation ->
            SwingUtilities.invokeLater {
                if (!continuation.isActive) return@invokeLater
                val chooser = JFileChooser(directory()).apply {
                    dialogTitle = title
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    isAcceptAllFileFilterUsed = false
                }
                val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                val selected = if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
                    chooser.selectedFile.absolutePath
                } else null
                if (continuation.isActive) continuation.resume(selected)
            }
        }
    }

    override suspend fun migrate(path: String?, items: List<DownloadItem>, activeIds: List<String>): List<DownloadItem> = withContext(Dispatchers.IO) {
        DownloadsPlatformDownloader.awaitTransfersClosed(items.map { it.fileName })
        migration(ProfileRepository.activeProfileId).move(directory(), path?.let(::File) ?: defaultDirectory, items, activeIds)
    }
}

/** Copies are journaled before touching originals; a commit always rolls forward on recovery. */
internal class DownloadFolderMigration(
    private val journalFile: File,
    private val checkpoint: (String) -> Unit = {},
    private val commit: (path: String, payload: String) -> Unit,
) {
    @Serializable
    private data class Entry(val source: String, val target: String, val staged: String)

    @Serializable
    private data class Journal(
        val directory: String,
        val payload: String,
        val staging: String,
        val entries: List<Entry>,
        val sidecars: List<String>,
        val activeIds: List<String> = emptyList(),
        val committed: Boolean = false,
        val applied: Boolean = false,
    )

    private val json = Json { encodeDefaults = true }

    fun recover(): List<String> {
        if (!journalFile.exists()) return emptyList()
        var journal = json.decodeFromString<Journal>(journalFile.readText())
        val resumeIds = if (journal.applied) emptyList() else journal.activeIds
        if (journal.committed) {
            if (!journal.applied) {
                commit(journal.directory, journal.payload)
                journal = journal.copy(applied = true)
                writeJournal(journal)
            }
            // Retain the journal when a locked source needs another cleanup attempt.
            var cleaned = true
            journal.entries.forEach { entry ->
                val source = File(entry.source)
                val target = File(entry.target)
                if (source.exists()) {
                    // Never remove a source if the committed target disappeared or changed.
                    if (!sameContent(source, target) || !source.delete()) cleaned = false
                }
            }
            journal.sidecars.forEach { File(it).delete() } // Only empty sidecar directories.
            if (!cleaned) return resumeIds
        } else {
            journal.entries.forEach { entry ->
                val target = File(entry.target)
                if (target.exists()) {
                    check(sameContent(File(entry.source), target)) { "A destination file changed during recovery: ${target.name}" }
                    check(target.delete()) { "Cannot remove staged destination: ${target.name}" }
                }
            }
        }
        deleteStaging(File(journal.staging))
        check(journalFile.delete()) { "Cannot clear download migration journal" }
        return resumeIds
    }

    fun move(sourceDirectory: File, destinationDirectory: File, items: List<DownloadItem>, activeIds: List<String> = emptyList()): List<DownloadItem> {
        recover()
        check(!journalFile.exists()) { "Close files still open in the previous download folder and try again." }
        val sourceRoot = sourceDirectory.canonicalFile
        val targetRoot = destinationDirectory.canonicalFile
        if (sourceRoot == targetRoot) return items
        check(targetRoot.isDirectory || targetRoot.mkdirs()) { "Cannot create the selected folder." }
        check(Files.isWritable(targetRoot.toPath())) { "The selected folder is not writable." }
        val staging = File(targetRoot, ".nuvio-move-${UUID.randomUUID()}")
        val entries = mutableListOf<Entry>()
        val sidecars = mutableListOf<String>()
        val moved = items.map { item ->
            require(File(item.fileName).name == item.fileName && item.fileName !in listOf(".", "..")) { "Invalid download filename" }
            val video = item.localFileUri?.let { runCatching { File(URI(it)) }.getOrNull() }
                ?: File(sourceRoot, item.fileName)
            fun add(source: File, target: File) {
                require(!Files.isSymbolicLink(source.toPath())) { "Symbolic links cannot be migrated." }
                if (source.isFile) entries += Entry(source.canonicalPath, target.path,
                    File(staging, target.relativeTo(targetRoot).path).path)
            }
            val targetVideo = File(targetRoot, item.fileName)
            add(video, targetVideo)
            add(File(sourceRoot, "${item.fileName}.part"), File(targetRoot, "${item.fileName}.part"))
            val subtitles = File(video.path + ".subtitles")
            require(!targetRoot.toPath().startsWith(subtitles.canonicalFile.toPath())) { "Choose a folder outside the subtitle directory." }
            if (subtitles.isDirectory) {
                subtitles.walkTopDown().forEach { file ->
                    require(!Files.isSymbolicLink(file.toPath())) { "Symbolic links cannot be migrated." }
                    if (file.isFile) add(file, File(targetVideo.path + ".subtitles", file.relativeTo(subtitles).path))
                    else if (file.isDirectory) sidecars.add(0, file.canonicalPath)
                }
            }
            if (video.isFile && item.status == DownloadStatus.Completed) item.copy(localFileUri = targetVideo.toURI().toString()) else item
        }
        val uniqueEntries = entries.distinctBy { it.source }
        require(uniqueEntries.map { it.target.lowercase() }.distinct().size == uniqueEntries.size) { "Downloads have conflicting filenames." }
        uniqueEntries.forEach { check(!File(it.target).exists()) { "A file already exists in the selected folder: ${File(it.target).name}" } }
        val needed = uniqueEntries.sumOf { File(it.source).length() }
        check(needed <= targetRoot.usableSpace / 2) { "Not enough free space in the selected folder." }
        var journal = Journal(targetRoot.path, DownloadsCodec.encodeItems(moved), staging.path, uniqueEntries, sidecars, activeIds)
        writeJournal(journal)
        try {
            uniqueEntries.forEach { entry ->
                val staged = File(entry.staged)
                check(staged.parentFile.isDirectory || staged.parentFile.mkdirs()) { "Cannot create staging folder." }
                File(entry.source).copyTo(staged)
                check(sameContent(File(entry.source), staged)) { "Download copy verification failed." }
            }
            checkpoint("staged")
            uniqueEntries.forEach { entry ->
                val target = File(entry.target)
                check(target.parentFile.isDirectory || target.parentFile.mkdirs()) { "Cannot create download folder." }
                Files.move(File(entry.staged).toPath(), target.toPath())
                check(sameContent(File(entry.source), target)) { "Download copy verification failed." }
            }
            checkpoint("promoted")
            journal = journal.copy(committed = true)
            writeJournal(journal)
            checkpoint("committed")
        } catch (error: Exception) {
            runCatching { recover() }.onFailure { error.addSuppressed(it) }
            throw error
        }
        // Commit failure leaves a complete journal and verified copies for roll-forward recovery.
        recover()
        return moved
    }

    private fun writeJournal(journal: Journal) {
        journalFile.parentFile.mkdirs()
        val temporary = File(journalFile.path + ".tmp")
        FileOutputStream(temporary).use { output ->
            output.write(json.encodeToString(journal).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), journalFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), journalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sameContent(first: File, second: File): Boolean =
        first.isFile && second.isFile && Files.mismatch(first.toPath(), second.toPath()) == -1L

    private fun deleteStaging(directory: File) {
        // Only this transaction's UUID staging directory is eligible for recursive deletion.
        require(directory.name.startsWith(".nuvio-move-"))
        check(!directory.exists() || directory.deleteRecursively()) { "Cannot remove download staging files." }
    }
}
