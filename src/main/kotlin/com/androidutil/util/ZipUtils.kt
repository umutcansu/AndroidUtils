package com.androidutil.util

import java.io.InputStream
import java.nio.file.Path
import java.util.zip.ZipFile

object ZipUtils {

    data class ZipEntryInfo(
        val name: String,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val isDirectory: Boolean
    )

    fun listEntries(path: Path): List<ZipEntryInfo> {
        return ZipFile(path.toFile()).use { zip ->
            zip.entries().asSequence().map { entry ->
                ZipEntryInfo(
                    name = entry.name,
                    compressedSize = entry.compressedSize,
                    uncompressedSize = entry.size,
                    isDirectory = entry.isDirectory
                )
            }.toList()
        }
    }

    fun <T> withEntry(path: Path, entryName: String, action: (InputStream) -> T): T? {
        return ZipFile(path.toFile()).use { zip ->
            val entry = zip.getEntry(entryName) ?: return null
            zip.getInputStream(entry).use { action(it) }
        }
    }

    fun <T> withMatchingEntries(
        path: Path,
        pattern: Regex,
        action: (String, InputStream, Long) -> T
    ): List<T> {
        return ZipFile(path.toFile()).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && pattern.matches(it.name) }
                .map { entry ->
                    zip.getInputStream(entry).use { inputStream ->
                        action(entry.name, inputStream, entry.size)
                    }
                }
                .toList()
        }
    }

    fun readEntry(path: Path, entryName: String): ByteArray? {
        return withEntry(path, entryName) { it.readAllBytes() }
    }
}
