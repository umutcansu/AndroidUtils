package com.androidutil.util

import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * Cross-platform file chooser using Java Swing's JFileChooser.
 * Works on macOS, Linux, and Windows without extra dependencies.
 * Falls back gracefully if no display is available (headless mode / SSH).
 */
object FileChooser {

    /**
     * Opens a native file picker dialog.
     * Returns the selected file path, or null if cancelled.
     */
    fun selectFile(
        title: String = "Select File",
        extensions: List<String> = emptyList(),
        startDir: Path? = null
    ): Path? {
        // Check if we're in a graphical environment
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return null
        }

        return try {
            // Use system look and feel for native appearance
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

            val chooser = JFileChooser().apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = extensions.isEmpty()

                if (extensions.isNotEmpty()) {
                    val description = extensions.joinToString(", ") { ".$it" }
                    val filter = FileNameExtensionFilter("Android files ($description)", *extensions.toTypedArray())
                    addChoosableFileFilter(filter)
                    fileFilter = filter
                }

                // Start directory priority: provided > Downloads > home
                currentDirectory = when {
                    startDir != null && startDir.exists() -> startDir.toFile()
                    else -> defaultStartDir().toFile()
                }
            }

            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.toPath()
            } else {
                null
            }
        } catch (e: Exception) {
            // Fallback: headless environment, no display, etc.
            null
        }
    }

    /**
     * Determines the best starting directory for the file picker.
     * Checks common locations where Android files might be.
     */
    private fun defaultStartDir(): Path {
        val home = System.getProperty("user.home")

        // Check Downloads first (most common for received files)
        val downloads = Path(home, "Downloads")
        if (downloads.exists()) return downloads

        // Desktop
        val desktop = Path(home, "Desktop")
        if (desktop.exists()) return desktop

        return Path(home)
    }
}
