package com.nuvio.app.core.storage

import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.UUID
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopCacheTest {
    @Test
    fun jar_resources_are_installed_once_per_entry_version() {
        val namespace = "test-${UUID.randomUUID()}"
        val workDir = Files.createTempDirectory("desktop-cache-test")
        try {
            val first = byteArrayOf(1, 2, 3)
            val firstUrl = jarResource(workDir.resolve("first.jar"), first)
            val firstDirectory = DesktopCache.installVersionedResources(namespace, mapOf("lib.bin" to firstUrl))
            assertContentEquals(first, Files.readAllBytes(firstDirectory.resolve("lib.bin")))
            assertEquals(firstDirectory, DesktopCache.installVersionedResources(namespace, mapOf("lib.bin" to firstUrl)))

            val second = byteArrayOf(4, 5, 6, 7)
            val secondUrl = jarResource(workDir.resolve("second.jar"), second)
            val secondDirectory = DesktopCache.installVersionedResources(namespace, mapOf("lib.bin" to secondUrl))
            assertNotEquals(firstDirectory, secondDirectory)
            assertContentEquals(second, Files.readAllBytes(secondDirectory.resolve("lib.bin")))
        } finally {
            deleteRecursively(DesktopStorage.cacheDir.resolve(namespace))
            deleteRecursively(workDir)
        }
    }

    @Test
    fun file_resources_are_installed() {
        val namespace = "test-${UUID.randomUUID()}"
        val workDir = Files.createTempDirectory("desktop-cache-test")
        try {
            val source = workDir.resolve("lib.bin")
            Files.write(source, byteArrayOf(9, 8, 7))
            val directory = DesktopCache.installVersionedResources(namespace, mapOf("lib.bin" to source.toUri().toURL()))
            assertContentEquals(byteArrayOf(9, 8, 7), Files.readAllBytes(directory.resolve("lib.bin")))
        } finally {
            deleteRecursively(DesktopStorage.cacheDir.resolve(namespace))
            deleteRecursively(workDir)
        }
    }

    @Test
    fun pruning_removes_other_versions_and_keeps_current() {
        val namespace = "test-${UUID.randomUUID()}"
        try {
            val old = DesktopCache.installVersionedFiles(namespace, mapOf("lib.bin" to byteArrayOf(1)))
            val current = DesktopCache.installVersionedFiles(namespace, mapOf("lib.bin" to byteArrayOf(2)))
            val unrelated = Files.createDirectories(DesktopStorage.cacheDir.resolve(namespace).resolve("keep-me"))

            DesktopCache.pruneOtherVersions(current, lockProbe = "lib.bin")

            assertFalse(Files.exists(old))
            assertTrue(Files.exists(current.resolve("lib.bin")))
            assertTrue(Files.exists(unrelated))
        } finally {
            deleteRecursively(DesktopStorage.cacheDir.resolve(namespace))
        }
    }

    @Test
    fun pruning_keeps_a_version_whose_probe_is_locked() {
        if (!System.getProperty("os.name").lowercase().contains("windows")) return
        val namespace = "test-${UUID.randomUUID()}"
        try {
            val locked = DesktopCache.installVersionedFiles(namespace, mapOf("lib.bin" to byteArrayOf(1)))
            val current = DesktopCache.installVersionedFiles(namespace, mapOf("lib.bin" to byteArrayOf(2)))
            // Windows refuses to delete a file opened without FILE_SHARE_DELETE, as with a loaded DLL.
            java.io.RandomAccessFile(locked.resolve("lib.bin").toFile(), "r").use {
                DesktopCache.pruneOtherVersions(current, lockProbe = "lib.bin")
            }
            assertTrue(Files.exists(locked.resolve("lib.bin")))
        } finally {
            deleteRecursively(DesktopStorage.cacheDir.resolve(namespace))
        }
    }

    private fun jarResource(jar: Path, bytes: ByteArray): URL {
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            output.putNextEntry(JarEntry("native/lib.bin"))
            output.write(bytes)
            output.closeEntry()
        }
        return URL("jar:${jar.toUri()}!/native/lib.bin")
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach {
                runCatching { Files.deleteIfExists(it) }.onFailure { _ ->
                    it.toFile().deleteOnExit()
                }
            }
        }
    }
}
