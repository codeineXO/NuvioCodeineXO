package com.nuvio.app.features.player

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BundledSvpRuntimeTest {
    private val files = linkedMapOf("python.dll" to "python".toByteArray(), "vs-plugins/svp.dll" to "plugin".toByteArray())
    private val manifest = files.entries.joinToString("\n") { (name, bytes) ->
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } + "  " + name
    }

    private fun withCache(test: (Path) -> Unit) {
        val root = Files.createTempDirectory("nuvio-svp-test-")
        try {
            test(root)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private fun installer(root: Path, source: Map<String, ByteArray> = files) =
        BundledSvpRuntime(root, manifest) { source[it]?.inputStream() }

    @Test fun installsNestedFilesAndReusesVerifiedCopyWithoutReadingResources() = withCache { root ->
        val target = installer(root).install()
        assertEquals("plugin", Files.readString(target.resolve("vs-plugins/svp.dll")))
        val timestamp = Files.getLastModifiedTime(target.resolve("python.dll"))
        val reused = BundledSvpRuntime(root, manifest) { error("Valid cache must not read bundled resources") }.install()
        assertEquals(target, reused)
        assertEquals(timestamp, Files.getLastModifiedTime(target.resolve("python.dll")))
    }

    @Test fun repairsMissingCorruptAndUnexpectedFiles() = withCache { root ->
        val target = installer(root).install()
        Files.delete(target.resolve("python.dll"))
        installer(root).install()
        assertEquals("python", Files.readString(target.resolve("python.dll")))
        Files.writeString(target.resolve("vs-plugins/svp.dll"), "corrupted")
        Files.writeString(target.resolve("vs-plugins/unlisted.dll"), "unwanted")
        installer(root).install()
        assertEquals("plugin", Files.readString(target.resolve("vs-plugins/svp.dll")))
        assertFalse(Files.exists(target.resolve("vs-plugins/unlisted.dll")))
    }

    @Test fun missingOrCorruptResourceNeverActivatesPartialRuntime() = withCache { root ->
        assertFailsWith<IllegalStateException> { installer(root, files - "vs-plugins/svp.dll").install() }
        assertEquals(0L, Files.list(root).use { it.count() })
        assertFailsWith<IllegalStateException> { installer(root, files + ("python.dll" to byteArrayOf(1))).install() }
        assertEquals(0L, Files.list(root).use { it.count() })
        assertTrue(Files.isDirectory(installer(root).install()))
    }

    @Test fun interruptedPreparationIsDiscardedAndRetrySucceeds() = withCache { root ->
        val orphan = Files.createDirectory(root.resolve(".staging-interrupted"))
        Files.writeString(orphan.resolve("python.dll"), "partial")
        assertFailsWith<IOException> {
            BundledSvpRuntime(root, manifest) { name ->
                if (name.contains('/')) throw IOException("Interrupted resource read")
                files[name]?.inputStream()
            }.install()
        }
        assertEquals(0L, Files.list(root).use { it.count() })
        assertTrue(Files.isDirectory(installer(root).install()))
    }

    @Test fun preparationFailurePreservesPreviousVersionAndLegacyDirectory() = withCache { root ->
        val previous = installer(root).install()
        val legacy = Files.createDirectory(root.resolve("legacy-runtime"))
        Files.writeString(legacy.resolve("user-file"), "keep")
        val nextManifest = manifest + "\n" + "0".repeat(64) + "  absent.dll"
        assertFailsWith<IllegalStateException> {
            BundledSvpRuntime(root, nextManifest) { files[it]?.inputStream() }.install()
        }
        assertEquals("python", Files.readString(previous.resolve("python.dll")))
        assertEquals("keep", Files.readString(legacy.resolve("user-file")))
    }

    @Test fun rejectsEmptyDuplicateAndEscapingManifests() {
        for (text in listOf("", manifest + "\n" + manifest, "0".repeat(64) + "  ../outside.dll",
            "0".repeat(64) + "  C:/outside.dll", "0".repeat(64) + "  /outside.dll")) {
            assertFailsWith<IllegalArgumentException> { BundledSvpRuntime.parseManifest(text) }
        }
    }
}
