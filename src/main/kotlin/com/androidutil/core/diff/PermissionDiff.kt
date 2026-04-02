package com.androidutil.core.diff

import com.androidutil.core.manifest.ManifestParser
import java.nio.file.Path

data class PermissionDiffResult(
    val oldFile: String,
    val newFile: String,
    val added: List<String>,
    val removed: List<String>,
    val unchanged: List<String>,
    val oldTotal: Int,
    val newTotal: Int
)

class PermissionDiff {

    private val manifestParser = ManifestParser()

    fun compare(oldFile: Path, newFile: Path): PermissionDiffResult {
        val oldPerms = extractPermissions(oldFile).toSet()
        val newPerms = extractPermissions(newFile).toSet()

        val added = (newPerms - oldPerms).sorted()
        val removed = (oldPerms - newPerms).sorted()
        val unchanged = (oldPerms.intersect(newPerms)).sorted()

        return PermissionDiffResult(
            oldFile = oldFile.toString(),
            newFile = newFile.toString(),
            added = added,
            removed = removed,
            unchanged = unchanged,
            oldTotal = oldPerms.size,
            newTotal = newPerms.size
        )
    }

    private fun extractPermissions(file: Path): List<String> {
        val ext = file.toString().substringAfterLast('.').lowercase()
        val manifest = when (ext) {
            "apk" -> manifestParser.parseFromApk(file)
            "aab" -> manifestParser.parseFromAab(file)
            else -> throw IllegalArgumentException("Unsupported file type: $ext")
        }
        return manifest.permissions
    }
}
