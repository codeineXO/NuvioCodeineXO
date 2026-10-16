package com.nuvio.app.features.onboarding

import com.nuvio.app.core.storage.DesktopStorage

internal actual object OnboardingStorage {
    private const val ONBOARDING_COMPLETED_KEY = "onboarding_completed"
    private val store = DesktopStorage.store("nuvio_onboarding_settings")

    actual fun loadCompleted(): Boolean? =
        if (store.contains(ONBOARDING_COMPLETED_KEY)) store.getBoolean(ONBOARDING_COMPLETED_KEY) else null

    actual fun saveCompleted(completed: Boolean) {
        store.putBoolean(ONBOARDING_COMPLETED_KEY, completed)
    }
}
