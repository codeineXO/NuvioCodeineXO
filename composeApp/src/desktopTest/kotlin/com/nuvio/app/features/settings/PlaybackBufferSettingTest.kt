package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.player.PlaybackBufferPreset
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackBufferSettingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sharedSettingsRowsStayInSyncAndSelectionClosesPopup() {
        var preset by mutableStateOf(PlaybackBufferPreset.BALANCED)
        compose.setContent {
            NuvioTheme {
                Column {
                    repeat(2) { PlaybackBufferSetting(false, preset) { preset = it } }
                }
            }
        }
        compose.onAllNodesWithText("Balanced").assertCountEquals(2)
        compose.onAllNodesWithText("Playback buffer")[0].performClick()
        compose.onNodeWithText("Choose how much video to keep in memory. Larger buffers help with network slowdowns but use more RAM.").assertExists()
        compose.onNodeWithText("SVP uses its own forward-cache limit while enabled.").assertExists()
        compose.onNodeWithText("Medium").performClick()
        compose.onAllNodesWithText("Medium").assertCountEquals(2)
        compose.onNodeWithText("SVP uses its own forward-cache limit while enabled.").assertDoesNotExist()
        compose.runOnIdle { assertEquals(PlaybackBufferPreset.MEDIUM, preset) }
    }

    @Test
    fun dismissingPopupDoesNotChangeSelection() {
        var selections = 0
        compose.setContent {
            NuvioTheme { PlaybackBufferSetting(false, PlaybackBufferPreset.LARGE) { selections++ } }
        }
        compose.onNodeWithText("Playback buffer").performClick()
        compose.onNode(hasText("Large") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("SVP uses its own forward-cache limit while enabled.").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, selections) }
    }
}
