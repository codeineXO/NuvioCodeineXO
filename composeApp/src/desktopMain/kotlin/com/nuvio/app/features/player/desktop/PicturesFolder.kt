package com.nuvio.app.features.player.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.io.File

/** The user's Pictures folder, following a Windows redirect (e.g. to OneDrive). */
internal object PicturesFolder {
    private const val CSIDL_MYPICTURES = 0x0027
    private const val MAX_PATH = 260

    private interface Shell32 : StdCallLibrary {
        fun SHGetFolderPathW(hwnd: Pointer?, csidl: Int, token: Pointer?, flags: Int, path: CharArray): Int
    }

    val directory: File by lazy {
        windowsPictures() ?: File(System.getProperty("user.home"), "Pictures")
    }

    private fun windowsPictures(): File? {
        if (DesktopHostOs.current != DesktopHostOs.WINDOWS) return null
        return runCatching {
            val buffer = CharArray(MAX_PATH)
            val shell32 = Native.load("shell32", Shell32::class.java)
            if (shell32.SHGetFolderPathW(null, CSIDL_MYPICTURES, null, 0, buffer) != 0) return@runCatching null
            Native.toString(buffer).takeIf(String::isNotBlank)?.let(::File)
        }.getOrNull()
    }
}
