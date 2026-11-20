package com.nuvio.app.features.streams

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object StreamFilterSettingsStorage {
    private const val payloadKey = "stream_filter_settings_payload"
    private val store = DesktopStorage.store("nuvio_stream_filter_settings")

    actual fun loadPayload(): String? =
        store.getString(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of(payloadKey), payload)
    }
}
