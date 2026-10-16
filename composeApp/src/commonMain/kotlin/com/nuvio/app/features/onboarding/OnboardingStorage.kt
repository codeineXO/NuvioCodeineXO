package com.nuvio.app.features.onboarding

internal expect object OnboardingStorage {
    fun loadCompleted(): Boolean?
    fun saveCompleted(completed: Boolean)
}
