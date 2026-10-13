package com.nuvio.app.features.settings

internal actual object DiscordRichPresencePlatform {
    actual val isSupported: Boolean = false
}

internal actual object DiscordRichPresenceStorage {
    actual fun loadEnabled(): Boolean? = null
    actual fun saveEnabled(enabled: Boolean) = Unit
    actual fun loadActivityMode(): DiscordActivityMode? = null
    actual fun saveActivityMode(mode: DiscordActivityMode) = Unit
    actual fun loadActivityType(): DiscordActivityType? = null
    actual fun saveActivityType(type: DiscordActivityType) = Unit
    actual fun loadFullPosterEnabled(): Boolean? = null
    actual fun saveFullPosterEnabled(enabled: Boolean) = Unit
}
