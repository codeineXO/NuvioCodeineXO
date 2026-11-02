package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

enum class NavigationFeel(val key: String) {
    Smooth("smooth"), Snappy("snappy");

    companion object {
        fun fromKey(key: String?): NavigationFeel = entries.firstOrNull { it.key == key } ?: Smooth
    }
}

internal data class NavigationMotionProfile(
    val responseRate: Double,
    val arrowResponseRate: Double,
    val durationScale: Float,
    val scrollStiffness: Float,
    val dragMomentum: Boolean,
) {
    fun duration(smoothMillis: Int): Int = (smoothMillis * durationScale).roundToInt().coerceAtLeast(1)
}

private val smoothMotion = NavigationMotionProfile(20.0, 16.0, 1f, 200f, true)
private val snappyMotion = NavigationMotionProfile(48.0, 44.0, 0.4f, 1_200f, false)
internal val NavigationFeel.motionProfile: NavigationMotionProfile
    get() = if (this == NavigationFeel.Smooth) smoothMotion else snappyMotion

/** Device-local preference. It is deliberately separate from profile/server-synced settings. */
internal object NavigationMotionSettings {
    private val mutableFeel = MutableStateFlow(NavigationFeel.Smooth)
    val feel = mutableFeel.asStateFlow()
    val profile: NavigationMotionProfile get() = mutableFeel.value.motionProfile
    private var persist: (String) -> Unit = {}

    fun initialize(storedKey: String?, save: (String) -> Unit) {
        mutableFeel.value = NavigationFeel.fromKey(storedKey)
        persist = save
    }

    fun select(feel: NavigationFeel) {
        persist(feel.key)
        mutableFeel.value = feel
    }
}

@Composable
internal fun rememberNavigationMotionProfile(): NavigationMotionProfile {
    val feel by NavigationMotionSettings.feel.collectAsState()
    return feel.motionProfile
}

internal fun navigationMotionDuration(smoothMillis: Int): Int = NavigationMotionSettings.profile.duration(smoothMillis)

internal fun navigationScrollStep(distance: Float, velocity: Float, seconds: Float): SoftScrollStep =
    softScrollStep(distance, velocity, seconds, NavigationMotionSettings.profile.responseRate)
