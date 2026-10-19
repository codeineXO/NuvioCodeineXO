package com.nuvio.app

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class RootTabHostMemoryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun evictedTabRestoresStateAndProfileSwitchClearsIt() {
        var selected by mutableStateOf(AppScreenTab.Home)
        var profile by mutableStateOf(1)
        val live = mutableSetOf<AppScreenTab>()
        val values = mutableMapOf<AppScreenTab, String>()
        val setters = mutableMapOf<AppScreenTab, (String) -> Unit>()
        compose.setContent {
            RootTabHost(selectedTab = selected, profileId = profile) { tab ->
                var value by rememberSaveable { mutableStateOf("initial") }
                DisposableEffect(tab) {
                    live += tab
                    onDispose { live -= tab; setters.remove(tab) }
                }
                values[tab] = value
                setters[tab] = { value = it }
                BasicText(value)
            }
        }
        compose.runOnIdle { setters.getValue(AppScreenTab.Home)("saved query / selection") }
        compose.runOnIdle { selected = AppScreenTab.Search }
        compose.runOnIdle { selected = AppScreenTab.Library }
        compose.runOnIdle {
            assertEquals(setOf(AppScreenTab.Search, AppScreenTab.Library), live)
            selected = AppScreenTab.Home
        }
        compose.runOnIdle {
            assertEquals(setOf(AppScreenTab.Library, AppScreenTab.Home), live)
            assertEquals("saved query / selection", values[AppScreenTab.Home])
            profile = 2
        }
        compose.runOnIdle {
            assertEquals(setOf(AppScreenTab.Home), live)
            assertEquals("initial", values[AppScreenTab.Home])
        }
    }

    @Test
    fun revisitingTabUpdatesEvictionOrder() {
        val initial = retainedRootTabs(emptyList(), AppScreenTab.Home, 2)
        val searched = retainedRootTabs(initial, AppScreenTab.Search, 2)
        val revisited = retainedRootTabs(searched, AppScreenTab.Home, 2)
        assertEquals(listOf(AppScreenTab.Home, AppScreenTab.Library), retainedRootTabs(revisited, AppScreenTab.Library, 2))
    }
}
