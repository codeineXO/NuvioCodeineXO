package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerUiModeTest {

    @Test
    fun fromStorageKey_returnsCorrectMode() {
        assertEquals(PlayerUiMode.OFFICIAL, PlayerUiMode.fromStorageKey("official"))
        assertEquals(PlayerUiMode.CODEINE_XO, PlayerUiMode.fromStorageKey("codeine_xo"))
    }

    @Test
    fun fromStorageKey_isCaseInsensitive() {
        assertEquals(PlayerUiMode.OFFICIAL, PlayerUiMode.fromStorageKey("OFFICIAL"))
        assertEquals(PlayerUiMode.CODEINE_XO, PlayerUiMode.fromStorageKey("Codeine_Xo"))
    }

    @Test
    fun fromStorageKey_defaultsToCodeineXoForUnknownOrNull() {
        assertEquals(PlayerUiMode.CODEINE_XO, PlayerUiMode.fromStorageKey(null))
        assertEquals(PlayerUiMode.CODEINE_XO, PlayerUiMode.fromStorageKey(""))
        assertEquals(PlayerUiMode.CODEINE_XO, PlayerUiMode.fromStorageKey("unknown_mode"))
    }
}
