package com.nuvio.app.features.player

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BundledSvpRuntimePruneTest {
    private fun runtime(root: Path, files: Map<String, String>) =
        BundledSvpRuntime(
            root,
            files.entries.joinToString("\n") { (name, body) -> "${sha256(body)}  $name" },
            resource = { name -> files[name]?.byteInputStream() },
            lockProbe = "VSScript.dll",
        )

    private val v1 = mapOf("VSScript.dll" to "script-1", "python.dll" to "python")
    private val v2 = mapOf("VSScript.dll" to "script-2", "python.dll" to "python")

    @Test
    fun successfulInstallRemovesOtherVersionsButKeepsLegacyFolders() = withRoot { root ->
        val old = runtime(root, v1).install()
        val legacy = Files.createDirectories(root.resolve("legacy-runtime"))

        val current = runtime(root, v2).install()

        assertFalse(Files.exists(old))
        assertTrue(Files.exists(current.resolve("VSScript.dll")))
        assertTrue(Files.exists(legacy))
    }

    @Test
    fun failedInstallKeepsThePreviousVersion() = withRoot { root ->
        val old = runtime(root, v1).install()
        val broken = BundledSvpRuntime(
            root,
            "${sha256("missing")}  VSScript.dll",
            resource = { null },
            lockProbe = "VSScript.dll",
        )

        assertFailsWith<IllegalStateException> { broken.install() }

        assertTrue(Files.exists(old.resolve("VSScript.dll")))
    }

    @Test
    fun versionInUseByAnotherInstanceIsKept() = withRoot { root ->
        if (!System.getProperty("os.name").lowercase().contains("windows")) return@withRoot
        val old = runtime(root, v1).install()
        // Windows refuses to delete a file opened without FILE_SHARE_DELETE, as with a loaded DLL.
        RandomAccessFile(old.resolve("VSScript.dll").toFile(), "r").use {
            runtime(root, v2).install()
        }
        assertTrue(Files.exists(old.resolve("VSScript.dll")))
    }

    private fun withRoot(test: (Path) -> Unit) {
        val root = Files.createTempDirectory("svp-prune-test")
        try {
            test(root)
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } }
            }
        }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
