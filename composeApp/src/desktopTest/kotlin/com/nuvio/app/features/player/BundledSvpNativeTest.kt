package com.nuvio.app.features.player

import com.nuvio.app.features.player.desktop.DesktopHostOs
import com.nuvio.app.features.player.desktop.NativePlayerBridge
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BundledSvpNativeTest {
    @Test fun missingBundleDoesNotBreakBridgeAndOfflineRetryRecovers() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        val root = Files.createTempDirectory("nuvio-svp-native-failure-")
        try {
            val source = root.resolve("SvpMissingBundleProbe.java")
            val bytes = requireNotNull(javaClass.getResourceAsStream("/svp/SvpMissingBundleProbe.java"))
                .use { it.readBytes() }
            Files.write(source, bytes)
            val loaderUrls = generateSequence(javaClass.classLoader) { it.parent }
                .filterIsInstance<URLClassLoader>().flatMap { it.urLs.asSequence() }
                .map { it.toExternalForm() }.distinct().toList()
            val urls = (loaderUrls + System.getProperty("java.class.path").split(java.io.File.pathSeparator)
                .filter(String::isNotBlank).map { Path.of(it).toUri().toString() }).distinct().toMutableList()
            // Development tests normally find the bridge in build/native. Give the isolated
            // process the same resource layout that desktopJar creates, without repository cwd fallback.
            if (javaClass.getResource("/native/windows/player_bridge.dll") == null) {
                val build = listOf(Path.of("build/native"), Path.of("composeApp/build/native"))
                    .first { Files.isRegularFile(it.resolve("windows/player_bridge.dll")) }
                val resourceRoot = root.resolve("native-resources")
                val native = Files.createDirectories(resourceRoot.resolve("native/windows"))
                Files.copy(build.resolve("windows/player_bridge.dll"), native.resolve("player_bridge.dll"))
                Files.list(build.resolve("windows-runtime")).use { files ->
                    files.filter { Files.isRegularFile(it) }.forEach { Files.copy(it, native.resolve(it.fileName)) }
                }
                urls.add(resourceRoot.toUri().toString())
            }
            val classpath = root.resolve("classpath.txt")
            Files.write(classpath, urls)
            val output = root.resolve("probe.log")
            val builder = ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                source.toString(), classpath.toString(),
            ).directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile())
            builder.environment().apply {
                put("LOCALAPPDATA", root.resolve("local").toString())
                put("APPDATA", root.resolve("roaming").toString())
                put("PATH", Path.of(System.getenv("WINDIR"), "System32").toString())
            }
            val process = builder.start()
            if (!process.waitFor(45, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
                error("SVP failure probe timed out: ${Files.readString(output)}")
            }
            val log = Files.readString(output)
            assertEquals(0, process.exitValue(), log)
            assertTrue(log.contains("Offline retry recovered"), log)
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test fun bridgeRegistersBundledRuntimeBeforeFirstAvailabilityCheck() {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return
        NativePlayerBridge.ensureLoaded()
        assertTrue(SvpRuntimeInstaller.isInstalled, SvpRuntimeInstaller.state.value.toString())
        assertTrue(NativePlayerBridge.isSvpAvailable())
        val path = assertNotNull(SvpRuntimeInstaller.runtimeDirectory)
        assertTrue(path.toString().replace('\\', '/').contains("/Cache/svp-runtime/windows-x64/"))
        assertEquals(path.resolve("svp_anime.vpy").toString().replace('\\', '/'), SvpScriptProvider.getScriptPath(SvpProfile.ANIME))
        // Once Python is loaded, an unrelated installation cannot take over its DLLs.
        assertFalse(NativePlayerBridge.configureSvpRuntime(path.resolve("legacy-runtime").toString()))
        assertTrue(NativePlayerBridge.configureSvpRuntime(path.toString()))
        java.nio.file.Files.walk(path).use { files ->
            assertFalse(files.anyMatch { it.fileName.toString().endsWith(".pyc") },
                "Python must not write bytecode into the verified runtime")
        }
    }
}
