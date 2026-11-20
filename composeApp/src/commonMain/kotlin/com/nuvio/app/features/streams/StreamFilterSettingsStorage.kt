package com.nuvio.app.features.streams

/** Local-only storage for [StreamFilterSettings]. Deliberately not part of profile settings sync. */
internal expect object StreamFilterSettingsStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
