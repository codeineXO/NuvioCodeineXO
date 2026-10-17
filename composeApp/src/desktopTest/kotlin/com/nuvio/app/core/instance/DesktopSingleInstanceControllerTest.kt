package com.nuvio.app.core.instance

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSingleInstanceControllerTest {
    private val tempDir = Files.createTempDirectory("nuvio-single-instance-test")

    @BeforeTest
    fun setUp() {
        DesktopSingleInstanceController.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        DesktopSingleInstanceController.resetForTest()
        runCatching {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun primary_instance_acquires_lock_and_writes_port() {
        val started = DesktopSingleInstanceController.startOrForward(
            args = emptyArray(),
            lockDir = tempDir,
            onExit = {},
        )
        assertTrue(started, "Primary instance should start successfully")
        assertTrue(Files.exists(tempDir.resolve("app.lock")), "Lock file should be created")
        assertTrue(Files.exists(tempDir.resolve("app.port")), "Port file should be created")
    }

    @Test
    fun secondary_instance_forwards_arguments_and_exits() {
        val latch = CountDownLatch(1)
        var receivedArgs: List<String>? = null
        DesktopSingleInstanceController.onPingReceivedForTest = { args ->
            receivedArgs = args
            latch.countDown()
        }

        // 1. Primary instance starts
        val primaryStarted = DesktopSingleInstanceController.startOrForward(
            args = emptyArray(),
            lockDir = tempDir,
            onExit = {},
        )
        assertTrue(primaryStarted, "Primary instance should start")

        // 2. Secondary instance tries to start
        var secondaryExited = false
        var exitCode = -1
        val secondaryStarted = DesktopSingleInstanceController.startOrForward(
            args = arrayOf("nuvio://meta?type=movie&id=tt1234567"),
            lockDir = tempDir,
            onExit = { code ->
                secondaryExited = true
                exitCode = code
            },
        )

        assertFalse(secondaryStarted, "Secondary instance should not start")
        assertTrue(secondaryExited, "Secondary instance should trigger exit")
        assertEquals(0, exitCode, "Secondary instance should exit with 0")

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Primary should receive forwarded args")
        assertEquals(listOf("nuvio://meta?type=movie&id=tt1234567"), receivedArgs)
    }

    @Test
    fun stale_lock_file_from_abnormal_crash_recovers_safely() {
        // Create a pre-existing (stale) lock file and port file as if an ungraceful crash happened
        val staleLock = tempDir.resolve("app.lock")
        val stalePort = tempDir.resolve("app.port")
        Files.writeString(staleLock, "stale-lock-data")
        Files.writeString(stalePort, "99999\ninvalid-token\n12345\n")

        val started = DesktopSingleInstanceController.startOrForward(
            args = emptyArray(),
            lockDir = tempDir,
            onExit = {},
        )
        assertTrue(started, "New instance should acquire stale lock safely")
        assertTrue(Files.exists(staleLock))
        assertTrue(Files.exists(stalePort))
    }

    @Test
    fun release_cleans_up_files_and_allows_subsequent_start() {
        val started = DesktopSingleInstanceController.startOrForward(
            args = emptyArray(),
            lockDir = tempDir,
            onExit = {},
        )
        assertTrue(started)

        DesktopSingleInstanceController.release()
        assertFalse(Files.exists(tempDir.resolve("app.port")), "Port file should be deleted on release")

        // Subsequent start should succeed
        DesktopSingleInstanceController.resetForTest()
        val startedAgain = DesktopSingleInstanceController.startOrForward(
            args = emptyArray(),
            lockDir = tempDir,
            onExit = {},
        )
        assertTrue(startedAgain, "Should be able to acquire lock again after release")
    }

    @Test
    fun clean_argument_and_isDesktopAppUrl_validation() {
        assertEquals("nuvio://meta", DesktopSingleInstanceController.cleanArgument("\"nuvio://meta\""))
        assertEquals("stremio://detail", DesktopSingleInstanceController.cleanArgument("  'stremio://detail'  "))

        assertTrue(DesktopSingleInstanceController.isDesktopAppUrl("nuvio://meta?type=movie&id=tt123"))
        assertTrue(DesktopSingleInstanceController.isDesktopAppUrl("STREMIO://addon/install"))
        assertTrue(DesktopSingleInstanceController.isDesktopAppUrl("\"nuvio://watch\""))
        assertTrue(DesktopSingleInstanceController.isDesktopAppUrl("https://nuvio.tv/party/K7M2QX"))
        assertTrue(DesktopSingleInstanceController.isDesktopAppUrl("nuvio://party/K7M2QX"))
        assertFalse(DesktopSingleInstanceController.isDesktopAppUrl("https://nuvio.tv/party/invalid"))
        assertFalse(DesktopSingleInstanceController.isDesktopAppUrl("https://otherdomain.com/party/K7M2QX"))
        assertFalse(DesktopSingleInstanceController.isDesktopAppUrl("https://example.com"))
        assertFalse(DesktopSingleInstanceController.isDesktopAppUrl("--some-cli-flag"))
    }
}
