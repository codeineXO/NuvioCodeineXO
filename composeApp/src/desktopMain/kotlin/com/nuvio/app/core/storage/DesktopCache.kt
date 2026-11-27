package com.nuvio.app.core.storage

import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Comparator
import kotlin.concurrent.thread

internal object DesktopCache {
    private val versionNamePattern = Regex("[0-9a-f]{24}")

    fun installVersionedFiles(namespace: String, files: Map<String, ByteArray>): Path {
        require(files.isNotEmpty())
        val directory = versionedDirectory(namespace, files)
        files.forEach { (relativePath, bytes) ->
            writeIfChanged(resolveTarget(directory, relativePath), bytes)
        }
        return directory
    }

    fun installVersionedResources(namespace: String, resources: Map<String, URL>): Path {
        require(resources.isNotEmpty())
        val directory = versionedDirectory(
            namespace,
            resources.mapValues { (_, url) -> resourceStamp(url).toByteArray(Charsets.UTF_8) },
        )
        resources.forEach { (relativePath, url) ->
            val target = resolveTarget(directory, relativePath)
            if (!Files.exists(target)) {
                val connection = url.openConnection().apply { useCaches = false }
                connection.getInputStream().use { input ->
                    writeAtomically(target) { pending -> Files.copy(input, pending, StandardCopyOption.REPLACE_EXISTING) }
                }
                if (connection is JarURLConnection) {
                    runCatching { connection.jarFile.close() }
                }
            }
        }
        return directory
    }

    /**
     * Deletes every other version folder next to [current], which was returned by one of the
     * install functions. When [lockProbe] is set, that file is deleted first and a folder whose
     * probe can't be deleted is kept: on Windows a loaded DLL is locked, so the folder still
     * belongs to a running instance.
     */
    fun pruneOtherVersions(current: Path, lockProbe: String? = null) {
        val namespaceDir = current.parent ?: return
        if (namespaceDir == DesktopStorage.cacheDir || !namespaceDir.startsWith(DesktopStorage.cacheDir)) return
        val stale = runCatching {
            Files.newDirectoryStream(namespaceDir).use { entries ->
                entries.filter { entry ->
                    entry != current &&
                        Files.isDirectory(entry) &&
                        versionNamePattern.matches(entry.fileName.toString())
                }
            }
        }.getOrDefault(emptyList())
        stale.forEach { directory ->
            if (lockProbe != null && runCatching { Files.deleteIfExists(directory.resolve(lockProbe)) }.isFailure) {
                return@forEach
            }
            deleteTree(directory)
        }
    }

    /** Runs [pruneOtherVersions] on a background thread so startup doesn't wait on file deletion. */
    fun pruneOtherVersionsInBackground(current: Path, lockProbe: String? = null) {
        thread(isDaemon = true, name = "desktop-cache-prune") {
            runCatching { pruneOtherVersions(current, lockProbe) }
        }
    }

    /**
     * Bounds a flat cache folder: deletes files last modified more than [maxAgeMs] ago, then the
     * oldest remaining ones until the folder is at most [maxBytes]. Files that can't be deleted
     * (still open) are skipped. Subfolders are left alone.
     */
    fun trimDirectory(directory: Path, maxAgeMs: Long, maxBytes: Long, nowMs: Long = System.currentTimeMillis()) {
        if (!directory.normalize().startsWith(DesktopStorage.cacheDir) || directory.normalize() == DesktopStorage.cacheDir) return
        val files = runCatching {
            Files.newDirectoryStream(directory).use { entries ->
                entries.filter { Files.isRegularFile(it) }.mapNotNull { file ->
                    runCatching {
                        CachedFile(file, Files.getLastModifiedTime(file).toMillis(), Files.size(file))
                    }.getOrNull()
                }
            }
        }.getOrDefault(emptyList()).sortedBy(CachedFile::modifiedMs)
        var totalBytes = files.sumOf(CachedFile::bytes)
        files.forEach { file ->
            if (nowMs - file.modifiedMs <= maxAgeMs && totalBytes <= maxBytes) return@forEach
            if (runCatching { Files.deleteIfExists(file.path) }.getOrDefault(false)) {
                totalBytes -= file.bytes
            }
        }
    }

    /** Runs [trimDirectory] on a background thread. */
    fun trimDirectoryInBackground(directory: Path, maxAgeMs: Long, maxBytes: Long) {
        thread(isDaemon = true, name = "desktop-cache-trim") {
            runCatching { trimDirectory(directory, maxAgeMs, maxBytes) }
        }
    }

    private class CachedFile(val path: Path, val modifiedMs: Long, val bytes: Long)

    private fun deleteTree(root: Path) {
        runCatching {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    runCatching { Files.deleteIfExists(path) }
                }
            }
        }
    }

    private fun versionedDirectory(namespace: String, versionInputs: Map<String, ByteArray>): Path {
        require(namespace.isNotBlank())
        val digest = MessageDigest.getInstance("SHA-256")
        versionInputs.toSortedMap().forEach { (path, bytes) ->
            digest.update(path.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(bytes)
        }
        val version = digest.digest().joinToString("") { byte -> "%02x".format(byte) }.take(24)
        val directory = DesktopStorage.cacheDir.resolve(namespace).resolve(version).normalize()
        require(directory.startsWith(DesktopStorage.cacheDir))
        return directory
    }

    private fun resolveTarget(directory: Path, relativePath: String): Path {
        val target = directory.resolve(relativePath).normalize()
        require(target.startsWith(directory))
        return target
    }

    private fun resourceStamp(url: URL): String {
        val connection = url.openConnection().apply { useCaches = false }
        return when (connection) {
            is JarURLConnection -> {
                val entry = connection.jarEntry
                val stamp = "${entry.size}:${entry.crc}"
                runCatching { connection.jarFile.close() }
                stamp
            }
            else -> "${connection.contentLengthLong}:${connection.lastModified}"
        }
    }

    private fun writeIfChanged(target: Path, bytes: ByteArray) {
        if (Files.exists(target) && Files.readAllBytes(target).contentEquals(bytes)) return
        writeAtomically(target) { pending -> Files.write(pending, bytes) }
    }

    private fun writeAtomically(target: Path, write: (Path) -> Unit) {
        Files.createDirectories(target.parent)
        val pending = Files.createTempFile(target.parent, target.fileName.toString(), ".part")
        try {
            write(pending)
            runCatching {
                Files.move(
                    pending,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(pending)
        }
    }
}
