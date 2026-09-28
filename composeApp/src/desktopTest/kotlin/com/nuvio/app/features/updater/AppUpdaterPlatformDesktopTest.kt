package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppFeaturePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun currentVersionIsPublishedAndParseable() {
        val current = AppUpdaterPlatform.currentVersionName
        assertTrue(current.isNotBlank(), "the published desktop version must not be blank")
        assertNotNull(
            VersionUtils.parse(current),
            "the published desktop version '$current' must parse as a semantic version",
        )
    }

    @Test
    fun versionComparisonRecognizesNewerRemoteVersion() {
        assertNewerRemoteIsOffered(VersionUtils::isRemoteNewer)
    }

    @Test
    fun legacyVersionComparisonWorksForDesktopAllReleases() {
        assertNewerRemoteIsOffered(VersionUtils::isRemoteNewerLegacy)
    }
}

/**
 * These checks previously pinned a hardcoded "v3.0.0" and compared literals such as
 * "v3.0.1" against it, so every version bump broke them. They now derive the newer
 * and older candidates from whatever version the build actually publishes, which
 * keeps the comparison behaviour covered without going stale again.
 */
private fun assertNewerRemoteIsOffered(
    compare: (remote: String?, local: String?) -> Boolean,
) {
    val current = AppUpdaterPlatform.currentVersionName
    val parsed = requireNotNull(VersionUtils.parse(current)) {
        "the published desktop version '$current' must parse as a semantic version"
    }
    val older = when {
        parsed.major > 0L -> "${parsed.major - 1}.${parsed.minor}.${parsed.patch}"
        parsed.minor > 0L -> "0.${parsed.minor - 1}.${parsed.patch}"
        parsed.patch > 0L -> "0.0.${parsed.patch - 1}"
        else -> null
    }
    val newer = "${parsed.major + 1}.${parsed.minor}.${parsed.patch}"

    val newerRemotes = listOf(newer, VersionUtils.normalize(newer))
    for (remote in newerRemotes) {
        assertTrue(
            compare(remote, current),
            "'$remote' should be treated as newer than '$current'",
        )
    }

    val notNewerRemotes = buildList {
        add(current)
        add(VersionUtils.normalize(current))
        if (older != null) {
            add(older)
            add(VersionUtils.normalize(older))
        }
    }
    for (remote in notNewerRemotes) {
        assertFalse(
            compare(remote, current),
            "'$remote' should not be treated as newer than '$current'",
        )
    }
}
