package com.androidutil.util

import java.nio.file.Path
import kotlin.io.path.*

/**
 * Manages recently used files for quick access.
 * Stores in ~/.androidutil/recents.txt
 */
object RecentFiles {

    private const val MAX_RECENTS = 10
    private val configDir = Path(System.getProperty("user.home"), ".androidutil")
    private val recentsFile = configDir.resolve("recents.txt")

    fun load(): List<Path> {
        if (!recentsFile.exists()) return emptyList()

        return try {
            recentsFile.readLines()
                .filter { it.isNotBlank() }
                .map { Path(it) }
                .filter { it.exists() }  // Only show files that still exist
                .take(MAX_RECENTS)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(path: Path) {
        try {
            if (!configDir.exists()) {
                configDir.createDirectories()
            }

            val existing = if (recentsFile.exists()) {
                recentsFile.readLines().filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            // Add to top, remove duplicates, remove stale files, limit size
            val updated = (listOf(path.absolutePathString()) + existing)
                .distinct()
                .filter { Path(it).exists() }
                .take(MAX_RECENTS)

            recentsFile.writeText(updated.joinToString("\n"))
        } catch (e: Exception) {
            // Silently fail - recents are a nice-to-have
        }
    }

    fun clear() {
        try {
            if (recentsFile.exists()) {
                recentsFile.deleteExisting()
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
