package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.DesktopStorage

internal actual object DiscordRichPresencePlatform {
    actual val isSupported: Boolean = true
}

internal actual object DiscordRichPresenceStorage {
    private const val enabledKey = "enabled"
    private const val activityModeKey = "activity_mode"
    private val store = DesktopStorage.store("nuvio_discord_rich_presence")

    actual fun loadEnabled(): Boolean? =
        if (store.contains(enabledKey)) store.getBoolean(enabledKey) else null

    actual fun saveEnabled(enabled: Boolean) {
        store.putBoolean(enabledKey, enabled)
    }

    actual fun loadActivityMode(): DiscordActivityMode? =
        store.getString(activityModeKey)?.let { name ->
            runCatching { DiscordActivityMode.valueOf(name) }.getOrNull()
        }

    actual fun saveActivityMode(mode: DiscordActivityMode) {
        store.putString(activityModeKey, mode.name)
    }
}
