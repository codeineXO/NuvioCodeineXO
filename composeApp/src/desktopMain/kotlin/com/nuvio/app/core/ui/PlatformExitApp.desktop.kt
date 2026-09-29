package com.nuvio.app.core.ui

actual fun platformExitApp() {
    DesktopAppShutdown.requestExit { }
}
