package com.nuvio.app

/**
 * Lets non-Compose input (a gamepad's bumpers) step through the main tabs. The visible tab bar
 * registers the handler while the root tabs are showing; nothing happens on other screens.
 */
internal object AppTabCycler {
    @Volatile
    private var handler: ((forward: Boolean) -> Unit)? = null

    fun register(onCycle: (forward: Boolean) -> Unit) {
        handler = onCycle
    }

    fun unregister(onCycle: (forward: Boolean) -> Unit) {
        if (handler === onCycle) handler = null
    }

    /** Must be called on the UI thread. Returns false when no tab bar is active. */
    fun cycle(forward: Boolean): Boolean {
        val current = handler ?: return false
        current(forward)
        return true
    }
}

internal fun AppScreenTab.cycled(forward: Boolean): AppScreenTab {
    val tabs = AppScreenTab.entries
    val step = if (forward) 1 else tabs.size - 1
    return tabs[(ordinal + step) % tabs.size]
}
