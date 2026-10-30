package com.nuvio.app.features.player

import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File

internal actual object SubtitleImportPlatform {
    private val supportedExtensions = setOf("srt", "vtt", "ass", "ssa", "sub")

    actual fun pickSubtitleFile(): ImportedSubtitleFile? {
        val parent = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
            ?: Frame.getFrames().firstOrNull { it.isVisible }
        val dialog = FileDialog(parent, "Import Subtitles", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val ext = name.substringAfterLast('.', "").lowercase()
            ext in supportedExtensions
        }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        val dir = dialog.directory ?: ""
        val target = File(dir, file)
        if (!target.exists() || !target.isFile) return null
        val ext = target.extension.lowercase()
        if (ext !in supportedExtensions) return null
        return ImportedSubtitleFile(
            path = target.absolutePath,
            name = target.name,
        )
    }

    fun handleDroppedFiles(files: List<File>): Boolean {
        val validFile = files.firstOrNull { f ->
            f.extension.lowercase() in supportedExtensions
        } ?: return false
        val listener = SubtitleImportBridge.onImport ?: return false
        listener.invoke(ImportedSubtitleFile(path = validFile.absolutePath, name = validFile.name))
        return true
    }

    fun createDropTarget(): DropTarget {
        return DropTarget(null, object : DropTargetAdapter() {
            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    val transferable = dtde.transferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        val list = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                        val files = list?.filterIsInstance<File>() ?: emptyList()
                        if (handleDroppedFiles(files)) {
                            dtde.dropComplete(true)
                            return
                        }
                    }
                    dtde.dropComplete(false)
                } catch (_: Exception) {
                    dtde.dropComplete(false)
                }
            }
        })
    }
}
