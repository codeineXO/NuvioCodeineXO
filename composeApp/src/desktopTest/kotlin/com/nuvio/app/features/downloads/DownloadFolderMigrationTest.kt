package com.nuvio.app.features.downloads

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadFolderMigrationTest {
    private fun item(fileName: String, completed: Boolean = true, uri: String? = null) = DownloadItem(
        id = fileName, contentType = "movie", parentMetaId = fileName, parentMetaType = "movie",
        videoId = fileName, title = fileName, streamTitle = fileName, providerName = "test",
        sourceUrl = "https://example.test/video", fileName = fileName, localFileUri = uri,
        status = if (completed) DownloadStatus.Completed else DownloadStatus.Paused,
        createdAtEpochMs = 0, updatedAtEpochMs = 0,
    )

    @Test
    fun movesVideosPartialsAndSubtitlesWithoutTouchingOtherProfiles() = workspace { root ->
        val source = File(root, "source").apply { mkdirs() }
        val target = File(root, "target")
        val video = File(source, "movie.mkv").apply { writeText("video") }
        File(source, "episode.mkv.part").writeText("partial")
        File(source, "other-profile.mkv").writeText("unrelated")
        val subtitles = File(source, "movie.mkv.subtitles/en/sub.vtt").apply { parentFile.mkdirs(); writeText("subtitle") }
        val journal = File(root, "journal.json")
        var committedPath = ""
        var payload = ""
        val engine = DownloadFolderMigration(journal) { path, value -> committedPath = path; payload = value }
        val moved = engine.move(source, target, listOf(item(video.name, uri = video.toURI().toString()), item("episode.mkv", false)))
        assertEquals(target.canonicalPath, committedPath)
        assertEquals("video", File(target, video.name).readText())
        assertEquals("partial", File(target, "episode.mkv.part").readText())
        assertEquals("subtitle", File(target, "movie.mkv.subtitles/en/sub.vtt").readText())
        assertTrue(File(source, "other-profile.mkv").exists())
        assertFalse(video.exists())
        assertFalse(subtitles.exists())
        assertFalse(journal.exists())
        assertEquals(File(target, video.name).toURI().toString(), moved.first().localFileUri)
        assertEquals(DownloadStatus.Paused, moved.last().status)
        assertEquals(moved, DownloadsCodec.decodeItems(payload))
    }

    @Test
    fun conflictLeavesOriginalsAndSettingsUntouched() = workspace { root ->
        val source = File(root, "source").apply { mkdirs() }
        val target = File(root, "target").apply { mkdirs() }
        File(source, "movie.mkv").writeText("original")
        File(target, "movie.mkv").writeText("existing")
        var commits = 0
        val engine = DownloadFolderMigration(File(root, "journal.json")) { _, _ -> commits++ }
        assertFails { engine.move(source, target, listOf(item("movie.mkv"))) }
        assertEquals("original", File(source, "movie.mkv").readText())
        assertEquals("existing", File(target, "movie.mkv").readText())
        assertEquals(0, commits)
    }

    @Test
    fun committedMigrationRollsForwardAfterSettingsWriteFailure() = workspace { root ->
        val source = File(root, "source").apply { mkdirs() }
        val target = File(root, "target")
        File(source, "movie.mkv").writeText("original")
        val journal = File(root, "journal.json")
        val failing = DownloadFolderMigration(journal) { _, _ -> error("Simulated settings failure") }
        assertFails { failing.move(source, target, listOf(item("movie.mkv"))) }
        assertTrue(File(source, "movie.mkv").exists())
        assertTrue(File(target, "movie.mkv").exists())
        assertTrue(journal.exists())
        var committedPath = ""
        DownloadFolderMigration(journal) { path, _ -> committedPath = path }.recover()
        assertEquals(target.canonicalPath, committedPath)
        assertFalse(File(source, "movie.mkv").exists())
        assertEquals("original", File(target, "movie.mkv").readText())
        assertFalse(journal.exists())
    }

    @Test
    fun selectingCurrentDirectoryDoesNothing() = workspace { root ->
        var commits = 0
        val items = listOf(item("missing.mkv"))
        val engine = DownloadFolderMigration(File(root, "journal.json")) { _, _ -> commits++ }
        assertEquals(items, engine.move(root, root, items))
        assertEquals(0, commits)
    }

    @Test
    fun nonDirectoryDestinationDoesNotModifyDownloads() = workspace { root ->
        val source = File(root, "source").apply { mkdirs() }
        File(source, "movie.mkv").writeText("original")
        val target = File(root, "file").apply { writeText("not a directory") }
        val engine = DownloadFolderMigration(File(root, "journal.json")) { _, _ -> error("Must not commit") }
        assertFails { engine.move(source, target, listOf(item("movie.mkv"))) }
        assertEquals("original", File(source, "movie.mkv").readText())
    }

    @Test
    fun interruptedUncommittedMigrationRollsBackPromotedCopies() = workspace { root ->
        val source = File(root, "source").apply { mkdirs() }
        val target = File(root, "target")
        File(source, "movie.mkv").writeText("original")
        val journal = File(root, "journal.json")
        val interrupted = DownloadFolderMigration(journal, checkpoint = {
            if (it == "promoted") throw SimulatedProcessExit()
        }) { _, _ -> error("Must not commit") }
        assertFails { interrupted.move(source, target, listOf(item("movie.mkv"))) }
        assertTrue(File(target, "movie.mkv").exists())
        DownloadFolderMigration(journal) { _, _ -> error("Must not commit") }.recover()
        assertEquals("original", File(source, "movie.mkv").readText())
        assertFalse(File(target, "movie.mkv").exists())
        assertFalse(journal.exists())
    }

    @Test
    fun interruptedCommitRecoversPartialsAndReturnsPreviouslyActiveIds() = workspace { root ->
        val source = File(root, "source").apply { mkdirs() }
        val target = File(root, "target")
        File(source, "movie.mkv.part").writeText("partial")
        val journal = File(root, "journal.json")
        val interrupted = DownloadFolderMigration(journal, checkpoint = {
            if (it == "committed") throw SimulatedProcessExit()
        }) { _, _ -> error("Commit should happen on recovery") }
        assertFails { interrupted.move(source, target, listOf(item("movie.mkv", false)), listOf("movie.mkv")) }
        val recovered = DownloadFolderMigration(journal) { _, payload ->
            assertEquals(DownloadStatus.Paused, DownloadsCodec.decodeItems(payload).single().status)
        }.recover()
        assertEquals(listOf("movie.mkv"), recovered)
        assertEquals("partial", File(target, "movie.mkv.part").readText())
        assertFalse(File(source, "movie.mkv.part").exists())
    }

    @Test
    fun restoringDefaultMovesFilesBack() = workspace { root ->
        val original = File(root, "default").apply { mkdirs() }
        val custom = File(root, "custom")
        File(original, "movie.mkv").writeText("video")
        val journal = File(root, "journal.json")
        val engine = DownloadFolderMigration(journal) { _, _ -> }
        val moved = engine.move(original, custom, listOf(item("movie.mkv")))
        val restored = engine.move(custom, original, moved)
        assertEquals("video", File(original, "movie.mkv").readText())
        assertFalse(File(custom, "movie.mkv").exists())
        assertEquals(File(original, "movie.mkv").toURI().toString(), restored.single().localFileUri)
    }

    @Test
    fun movesBetweenSystemTempAndWorkspaceVolume() = workspace { root ->
        // On Windows this exercises C: -> workspace drive when they differ.
        val workspaceTemp = File(System.getProperty("user.dir"), "build/tmp").apply { mkdirs() }
        val target = Files.createTempDirectory(workspaceTemp.toPath(), "download-volume-test-").toFile()
        try {
            val source = File(root, "source").apply { mkdirs() }
            File(source, "movie.mkv").writeBytes(ByteArray(300_000) { (it % 251).toByte() })
            val original = File(source, "movie.mkv").readBytes()
            val engine = DownloadFolderMigration(File(root, "journal.json")) { _, _ -> }
            engine.move(source, target, listOf(item("movie.mkv")))
            assertTrue(original.contentEquals(File(target, "movie.mkv").readBytes()))
            assertFalse(File(source, "movie.mkv").exists())
        } finally {
            target.deleteRecursively()
        }
    }

    private class SimulatedProcessExit : Error()

    private fun workspace(block: (File) -> Unit) {
        val root = Files.createTempDirectory("download-migration-test-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}
