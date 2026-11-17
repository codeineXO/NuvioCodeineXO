package com.nuvio.app.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

expect fun platformExitApp()

/** Pending request to show the app-exit confirmation; consumed by the app shell. */
object AppExitConfirmationRequests {
    var pending by mutableStateOf(false)
}

/** Asks the app shell to show the exit confirmation instead of exiting immediately. */
fun requestAppExitConfirmation() {
    AppExitConfirmationRequests.pending = true
}
