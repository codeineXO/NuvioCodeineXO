package com.nuvio.app.features.downloads

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDownloadTransferRegistryTest {
    @Test
    fun removalWaitsForOpenWriterToClose() = runBlocking {
        withTempFile { file ->
            val registry = DesktopDownloadTransferRegistry()
            val release = CompletableDeferred<Unit>()
            val worker = launch(start = CoroutineStart.UNDISPATCHED) {
                FileOutputStream(file).use {
                    withContext(NonCancellable) { release.await() }
                }
            }
            registry.register(file, worker)
            try {
                assertTrue(registry.removePartialFile(file))
                assertTrue(file.exists())
            } finally {
                release.complete(Unit)
                worker.join()
            }
            assertFalse(file.exists())
        }
    }

    @Test
    fun removalAlsoCleansFileCreatedAfterCancellation() = runBlocking {
        withTempFile { file ->
            val registry = DesktopDownloadTransferRegistry()
            val release = CompletableDeferred<Unit>()
            val worker = launch(start = CoroutineStart.UNDISPATCHED) {
                withContext(NonCancellable) {
                    release.await()
                    file.writeText("partial")
                }
            }
            registry.register(file, worker)
            try {
                assertTrue(registry.removePartialFile(file))
            } finally {
                release.complete(Unit)
                worker.join()
            }
            assertFalse(file.exists())
        }
    }

    @Test
    fun pausingPreservesPartialFileUntilExplicitRemoval() = runBlocking {
        withTempFile { file ->
            val registry = DesktopDownloadTransferRegistry()
            val release = CompletableDeferred<Unit>()
            val worker = launch(start = CoroutineStart.UNDISPATCHED) {
                FileOutputStream(file).use {
                    withContext(NonCancellable) { release.await() }
                }
            }
            registry.register(file, worker)
            worker.cancel()
            release.complete(Unit)
            worker.join()
            assertTrue(file.exists())
            assertTrue(registry.removePartialFile(file))
            assertFalse(file.exists())
            assertTrue(registry.removePartialFile(file))
        }
    }

    @Test
    fun removalWaitsForBothOldAndResumedWorkers() = runBlocking {
        withTempFile { file ->
            val registry = DesktopDownloadTransferRegistry()
            val oldRelease = CompletableDeferred<Unit>()
            val resumedRelease = CompletableDeferred<Unit>()
            val oldWorker = launch(start = CoroutineStart.UNDISPATCHED) {
                FileOutputStream(file).use {
                    withContext(NonCancellable) { oldRelease.await() }
                }
            }
            registry.register(file, oldWorker)
            oldWorker.cancel()
            val resumedWorker = launch(start = CoroutineStart.UNDISPATCHED) {
                FileOutputStream(file, true).use {
                    withContext(NonCancellable) { resumedRelease.await() }
                }
            }
            registry.register(file, resumedWorker)
            try {
                assertTrue(registry.removePartialFile(file))
                oldRelease.complete(Unit)
                oldWorker.join()
                assertTrue(file.exists())
            } finally {
                oldRelease.complete(Unit)
                resumedRelease.complete(Unit)
                oldWorker.join()
                resumedWorker.join()
            }
            assertFalse(file.exists())
        }
    }

    private suspend fun withTempFile(block: suspend (File) -> Unit) {
        val directory = Files.createTempDirectory("nuvio-download-cleanup-").toFile()
        try {
            block(File(directory, "video.mkv.part"))
        } finally {
            File(directory, "video.mkv.part").delete()
            directory.delete()
        }
    }
}
