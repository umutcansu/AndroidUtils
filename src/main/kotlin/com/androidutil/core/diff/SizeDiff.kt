package com.androidutil.core.diff

import com.androidutil.util.ZipUtils
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.name

data class SizeDiffResult(
    val oldFile: String,
    val newFile: String,
    val oldTotalBytes: Long,
    val newTotalBytes: Long,
    val totalDiffBytes: Long,
    val categoryDiffs: List<CategoryDiff>,
    val topIncreases: List<EntryDiff>,
    val topDecreases: List<EntryDiff>
)

data class CategoryDiff(
    val category: String,
    val oldBytes: Long,
    val newBytes: Long,
    val diffBytes: Long
)

data class EntryDiff(
    val path: String,
    val oldBytes: Long,
    val newBytes: Long,
    val diffBytes: Long
)

class SizeDiff {

    fun compare(oldFile: Path, newFile: Path): SizeDiffResult {
        val oldEntries = ZipUtils.listEntries(oldFile).associateBy { it.name }
        val newEntries = ZipUtils.listEntries(newFile).associateBy { it.name }

        val allNames = (oldEntries.keys + newEntries.keys)

        // Per-entry diffs
        val entryDiffs = allNames.map { name ->
            val oldSize = oldEntries[name]?.compressedSize ?: 0L
            val newSize = newEntries[name]?.compressedSize ?: 0L
            EntryDiff(name, oldSize, newSize, newSize - oldSize)
        }.filter { it.diffBytes != 0L }

        // Category breakdown
        val categories = listOf("dex", "res", "lib", "assets", "other")
        val categoryDiffs = categories.map { cat ->
            val catFilter = categoryFilter(cat)
            val oldBytes = oldEntries.values.filter { catFilter(it.name) }.sumOf { it.compressedSize }
            val newBytes = newEntries.values.filter { catFilter(it.name) }.sumOf { it.compressedSize }
            CategoryDiff(cat, oldBytes, newBytes, newBytes - oldBytes)
        }

        val topIncreases = entryDiffs.filter { it.diffBytes > 0 }.sortedByDescending { it.diffBytes }.take(10)
        val topDecreases = entryDiffs.filter { it.diffBytes < 0 }.sortedBy { it.diffBytes }.take(10)

        return SizeDiffResult(
            oldFile = oldFile.name,
            newFile = newFile.name,
            oldTotalBytes = oldFile.fileSize(),
            newTotalBytes = newFile.fileSize(),
            totalDiffBytes = newFile.fileSize() - oldFile.fileSize(),
            categoryDiffs = categoryDiffs,
            topIncreases = topIncreases,
            topDecreases = topDecreases
        )
    }

    private fun categoryFilter(category: String): (String) -> Boolean {
        // Support both APK paths (lib/, res/) and AAB paths (base/lib/, base/res/)
        return when (category) {
            "dex" -> { name -> name.endsWith(".dex") }
            "res" -> { name ->
                name.startsWith("res/") || name.startsWith("base/res/") ||
                    name == "resources.arsc" || name == "base/resources.pb"
            }
            "lib" -> { name -> name.startsWith("lib/") || name.startsWith("base/lib/") }
            "assets" -> { name -> name.startsWith("assets/") || name.startsWith("base/assets/") }
            else -> { name ->
                val n = name.removePrefix("base/")
                !n.endsWith(".dex") && !n.startsWith("res/") && n != "resources.arsc" &&
                    n != "resources.pb" && !n.startsWith("lib/") && !n.startsWith("assets/")
            }
        }
    }
}
