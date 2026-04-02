package com.androidutil.core.nativelib

import com.androidutil.core.elf.ElfParser
import com.androidutil.util.ZipUtils
import java.nio.file.Path

data class NativeLibDetail(
    val path: String,
    val abi: String,
    val sizeBytes: Long,
    val uncompressedBytes: Long,
    val isStripped: Boolean,
    val elfClass: Int,  // 32 or 64
    val pageAligned: Boolean,
    val minAlignment: Long
)

data class NativeLibReport(
    val filePath: String,
    val totalLibs: Int,
    val totalSizeBytes: Long,
    val abiSummary: Map<String, Int>,  // abi -> count
    val libraries: List<NativeLibDetail>,
    val allPageAligned: Boolean
)

class NativeLibInspector {

    fun inspect(filePath: Path): NativeLibReport {
        val entries = ZipUtils.listEntries(filePath)
        val soEntries = entries.filter { it.name.endsWith(".so") && !it.isDirectory }

        val libraries = soEntries.map { entry ->
            val abi = extractAbi(entry.name)
            val elfBytes = ZipUtils.readEntry(filePath, entry.name)

            var elfClass = 0
            var pageAligned = false
            var minAlignment = 0L
            var isStripped = true

            if (elfBytes != null) {
                try {
                    val result = ElfParser().checkAlignment(elfBytes.inputStream(), entry.name)
                    elfClass = result.elfClass
                    pageAligned = result.isCompatible
                    minAlignment = result.ptLoadSegments.minOfOrNull { seg -> seg.pAlign } ?: 0L
                } catch (_: Exception) {}

                // Check if .symtab section exists (indicates not stripped)
                isStripped = !hasSymtab(elfBytes)
            }

            NativeLibDetail(
                path = entry.name,
                abi = abi,
                sizeBytes = entry.compressedSize,
                uncompressedBytes = entry.uncompressedSize,
                isStripped = isStripped,
                elfClass = elfClass,
                pageAligned = pageAligned,
                minAlignment = minAlignment
            )
        }

        val abiSummary = libraries.groupBy { it.abi }.mapValues { it.value.size }

        // Google Play only requires 16KB alignment for 64-bit libraries
        val libs64bit = libraries.filter { it.elfClass == 64 }
        val allAligned = libs64bit.isEmpty() || libs64bit.all { it.pageAligned }

        return NativeLibReport(
            filePath = filePath.toString(),
            totalLibs = libraries.size,
            totalSizeBytes = libraries.sumOf { it.sizeBytes },
            abiSummary = abiSummary,
            libraries = libraries.sortedBy { it.path },
            allPageAligned = allAligned
        )
    }

    private fun extractAbi(path: String): String {
        // lib/arm64-v8a/libfoo.so or base/lib/arm64-v8a/libfoo.so → arm64-v8a
        val parts = path.split("/")
        val libIdx = parts.lastIndexOf("lib")
        return if (libIdx >= 0 && libIdx + 1 < parts.size) parts[libIdx + 1] else "unknown"
    }

    private fun hasSymtab(elfBytes: ByteArray): Boolean {
        // Simple heuristic: look for .symtab string in section header string table
        val marker = ".symtab".toByteArray()
        for (i in 0..elfBytes.size - marker.size) {
            var found = true
            for (j in marker.indices) {
                if (elfBytes[i + j] != marker[j]) {
                    found = false
                    break
                }
            }
            if (found) return true
        }
        return false
    }
}
