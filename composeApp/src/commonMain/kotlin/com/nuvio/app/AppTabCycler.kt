package com.nuvio.app

/**
 * Lets non-Compose input (a gamepad's bumpers) step through the main sections. The desktop sidebar
 * registers the handler while the root sections are showing; nothing happens on other screens.
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

    /** Must be called on the UI thread. Returns false when no sidebar is active. */
    fun cycle(forward: Boolean): Boolean {
        val current = handler ?: return false
        current(forward)
        return true
    }
}

/** A stop in the desktop sidebar's order: Home, Search, Shuffle, Library, Settings. */
internal sealed interface SidebarCycleStop {
    data class Tab(val tab: AppScreenTab) : SidebarCycleStop
    data object Shuffle : SidebarCycleStop

    fun cycled(forward: Boolean): SidebarCycleStop {
        val index = order.indexOf(this).coerceAtLeast(0)
        val step = if (forward) 1 else order.size - 1
        return order[(index + step) % order.size]
    }

    companion object {
        val order: List<SidebarCycleStop> = listOf(
            Tab(AppScreenTab.Home),
            Tab(AppScreenTab.Search),
            Shuffle,
            Tab(AppScreenTab.Library),
            Tab(AppScreenTab.Settings),
        )
    }
}
