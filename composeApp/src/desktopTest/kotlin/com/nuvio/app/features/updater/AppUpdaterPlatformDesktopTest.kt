package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppFeaturePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdaterPlatformDesktopTest {

    @Test
    fun inAppUpdaterIsEnabledOnDesktop() {
        assertTrue(AppFeaturePolicy.inAppUpdaterEnabled)
    }

    @Test
    fun releaseSourcePointsToFork() {
        assertEquals("codeineXO", AppUpdaterPlatform.releaseSource.owner)
        assertEquals("NuvioCodeineXO", AppUpdaterPlatform.releaseSource.repo)
        assertEquals("NuvioCodeineXO", AppUpdaterPlatform.releaseSource.userAgent)
    }

    @Test
    fun currentVersionIsV201() {
        assertEquals("v2.0.1", AppUpdaterPlatform.currentVersionName)
    }

    @Test
    fun versionComparisonRecognizesNewerRemoteVersion() {
        val currentVersion = AppUpdaterPlatform.currentVersionName
        assertTrue(VersionUtils.isRemoteNewer("v2.0.2", currentVersion))
        assertTrue(VersionUtils.isRemoteNewer("2.0.2", currentVersion))
        assertFalse(VersionUtils.isRemoteNewer("v2.0.1", currentVersion))
        assertFalse(VersionUtils.isRemoteNewer("2.0.1", currentVersion))
        assertFalse(VersionUtils.isRemoteNewer("v2.0.0", currentVersion))
        assertFalse(VersionUtils.isRemoteNewer("v1.9.9", currentVersion))
    }

    @Test
    fun legacyVersionComparisonWorksForDesktopAllReleases() {
        val currentVersion = AppUpdaterPlatform.currentVersionName
        assertTrue(VersionUtils.isRemoteNewerLegacy("v2.0.2", currentVersion))
        assertTrue(VersionUtils.isRemoteNewerLegacy("2.0.2", currentVersion))
        assertFalse(VersionUtils.isRemoteNewerLegacy("v2.0.1", currentVersion))
        assertFalse(VersionUtils.isRemoteNewerLegacy("2.0.1", currentVersion))
        assertFalse(VersionUtils.isRemoteNewerLegacy("v2.0.0", currentVersion))
    }
}
