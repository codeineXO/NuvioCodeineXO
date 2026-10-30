package com.nuvio.app.features.downloads

import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadLocationTransferTest {
    @Test
    fun migrationWaitsForItsWritersWithoutCancellingOtherProfiles() = runBlocking {
        val root = Files.createTempDirectory("download-transfer-test-").toFile()
        val current = root.resolve("current.part")
        val other = root.resolve("other.part")
        val registry = DesktopDownloadTransferRegistry()
        val release = CompletableDeferred<Unit>()
        val currentJob = launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { release.await() }
        }
        val otherJob = launch(start = CoroutineStart.UNDISPATCHED) { awaitCancellation() }
        registry.register(current, currentJob)
        registry.register(other, otherJob)
        val migration = launch(start = CoroutineStart.UNDISPATCHED) { registry.awaitClosed(setOf(current)) }
        try {
            assertTrue(currentJob.isCancelled)
            assertFalse(migration.isCompleted)
            assertTrue(otherJob.isActive)
            release.complete(Unit)
            migration.join()
            assertTrue(currentJob.isCompleted)
            assertTrue(otherJob.isActive)
        } finally {
            release.complete(Unit)
            currentJob.join()
            otherJob.cancel()
            otherJob.join()
            migration.join()
            root.deleteRecursively()
        }
    }
}
