package com.nuvio.app.features.autosync

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoSyncPreferencesDesktopTest {
    @AfterTest
    fun resetPersistence() {
        AutoSyncPreferencesRepository.installPersistence(load = { null }, save = {})
        AutoSyncPreferencesRepository.ensureLoaded()
    }

    @Test
    fun missingDesktopPreferenceDefaultsToEnabled() {
        AutoSyncPreferencesRepository.installPersistence(load = { null }, save = {})
        AutoSyncPreferencesRepository.ensureLoaded()
        assertTrue(AutoSyncPreferencesRepository.preferredSubtitleAutoSyncOnStart.value)
    }

    @Test
    fun savedOffOverridesDefaultAndSurvivesReload() {
        var saved: Boolean? = null
        AutoSyncPreferencesRepository.installPersistence(load = { saved }, save = { saved = it })
        AutoSyncPreferencesRepository.setPreferredSubtitleAutoSyncOnStart(false)
        assertEquals(false, saved)
        AutoSyncPreferencesRepository.installPersistence(load = { saved }, save = { saved = it })
        AutoSyncPreferencesRepository.ensureLoaded()
        assertFalse(AutoSyncPreferencesRepository.preferredSubtitleAutoSyncOnStart.value)
        assertFalse(AutoSyncPreferencesRepository.claimStartupRun(1, "video"))
    }

    @Test
    fun savedOnRemainsEnabled() {
        AutoSyncPreferencesRepository.installPersistence(load = { true }, save = {})
        AutoSyncPreferencesRepository.ensureLoaded()
        assertTrue(AutoSyncPreferencesRepository.preferredSubtitleAutoSyncOnStart.value)
    }
}
