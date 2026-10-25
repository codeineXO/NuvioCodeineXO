package com.nuvio.app.core.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopStartupClassLoadingTest {
    @Test
    fun mainAppFacadeHasValidJvmBytecode() {
        // Menu-only UI tests do not load the app shell. Verify it without starting services
        // or opening a window, so malformed compiler output cannot slip into a distribution.
        val appShell = Class.forName("com.nuvio.app.MainAppContentKt", false, javaClass.classLoader)
        assertTrue(appShell.declaredMethods.any { it.name == "MainAppContent" })
        val details = Class.forName("com.nuvio.app.features.details.MetaDetailsScreenKt", false, javaClass.classLoader)
        assertTrue(details.declaredMethods.any { it.name == "MetaDetailsScreen" })
    }
}
