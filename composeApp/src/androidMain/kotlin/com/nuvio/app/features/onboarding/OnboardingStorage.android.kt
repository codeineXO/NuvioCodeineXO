package com.nuvio.app.features.onboarding

internal actual object OnboardingStorage {
    private var completed: Boolean? = null

    actual fun loadCompleted(): Boolean? = completed

    actual fun saveCompleted(completed: Boolean) {
        this.completed = completed
    }
}
