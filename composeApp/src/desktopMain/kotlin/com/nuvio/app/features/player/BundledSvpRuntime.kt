package com.nuvio.app.features.player

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest

/** Installs only manifest-listed resources. No network, external tools, or legacy runtime paths. */
internal class BundledSvpRuntime(
    private val cacheRoot: Path,
    manifest: String,
    private val resource: (String) -> InputStream?,
) {
    private val entries = parseManifest(manifest)
    private val version = sha256(manifest.byteInputStream()).take(24)

    fun install(): Path = synchronized(installationLock) {
        Files.createDirectories(cacheRoot)
        Files.list(cacheRoot).use { paths ->
            paths.filter { it.fileName.toString().startsWith(".staging-") || it.fileName.toString().startsWith(".invalid-") }
                .forEach(::removeTree)
        }
        val target = cacheRoot.resolve(version)
        if (isValid(target)) return@synchronized target
        val staging = Files.createTempDirectory(cacheRoot, ".staging-")
        var displaced: Path? = null
        try {
            entries.forEach { (name, expected) ->
                val file = staging.resolve(name)
                Files.createDirectories(file.parent)
                val input = resource(name) ?: error("Missing bundled motion smoothing file: $name")
                input.use { Files.copy(it, file) }
                check(sha256(Files.newInputStream(file)) == expected) { "Motion smoothing checksum mismatch: $name" }
            }
            check(isValid(staging)) { "Incomplete motion smoothing runtime" }
            if (Files.exists(target, NOFOLLOW_LINKS)) {
                displaced = cacheRoot.resolve(".invalid-${java.util.UUID.randomUUID()}")
                Files.move(target, displaced)
            }
            try {
                Files.move(staging, target, ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(staging, target)
            }
            target
        } finally {
            removeTree(staging)
            displaced?.let(::removeTree)
        }
    }

    private fun isValid(directory: Path): Boolean = runCatching {
        if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) return@runCatching false
        val actual = Files.walk(directory).use { paths ->
            paths.filter { it != directory && !Files.isDirectory(it, NOFOLLOW_LINKS) }
                .map { directory.relativize(it).toString().replace('\\', '/') }.toList().toSet()
        }
        actual == entries.keys && entries.all { (name, hash) ->
            val file = directory.resolve(name)
            Files.isRegularFile(file, NOFOLLOW_LINKS) && sha256(Files.newInputStream(file)) == hash
        }
    }.getOrDefault(false)

    private fun removeTree(directory: Path) {
        require(directory.normalize().parent == cacheRoot.normalize())
        if (!Files.exists(directory, NOFOLLOW_LINKS)) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    companion object {
        private val installationLock = Any()
        internal fun parseManifest(manifest: String): Map<String, String> {
            val entries = linkedMapOf<String, String>()
            manifest.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
                val match = requireNotNull(Regex("([0-9a-f]{64})  ([A-Za-z0-9_./-]+)").matchEntire(line)) {
                    "Invalid motion smoothing manifest entry"
                }
                val (hash, name) = match.destructured
                require(!name.startsWith('/') && name.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
                    "Unsafe motion smoothing resource path"
                }
                require(entries.put(name, hash) == null) { "Duplicate motion smoothing resource: $name" }
            }
            require(entries.isNotEmpty()) { "Empty motion smoothing manifest" }
            return entries
        }

        private fun sha256(input: InputStream): String = input.use {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
