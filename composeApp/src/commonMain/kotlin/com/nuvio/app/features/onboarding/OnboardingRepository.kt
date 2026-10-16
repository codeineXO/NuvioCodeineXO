package com.nuvio.app.features.onboarding

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OnboardingRepository {
    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        _completed.value = OnboardingStorage.loadCompleted() ?: false
    }

    fun isOnboardingCompleted(): Boolean {
        ensureLoaded()
        return _completed.value
    }

    fun setOnboardingCompleted(completed: Boolean) {
        ensureLoaded()
        if (_completed.value == completed) return
        _completed.value = completed
        OnboardingStorage.saveCompleted(completed)
    }
}
