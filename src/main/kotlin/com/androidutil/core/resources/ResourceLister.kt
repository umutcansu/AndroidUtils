package com.androidutil.core.resources

import com.androidutil.util.ZipUtils
import java.nio.file.Path

data class ResourceCategory(
    val name: String,
    val fileCount: Int,
    val totalBytes: Long,
    val entries: List<ResourceEntry>
)

data class ResourceEntry(
    val path: String,
    val compressedBytes: Long,
    val uncompressedBytes: Long
)

data class ResourceReport(
    val filePath: String,
    val totalResources: Int,
    val totalBytes: Long,
    val categories: List<ResourceCategory>,
    val largestFiles: List<ResourceEntry>
)

class ResourceLister {

    fun list(filePath: Path): ResourceReport {
        val entries = ZipUtils.listEntries(filePath).filter { !it.isDirectory }

        val categorized = entries.groupBy { categorize(it.name) }

        val categories = categorized.map { (name, items) ->
            ResourceCategory(
                name = name,
                fileCount = items.size,
                totalBytes = items.sumOf { it.compressedSize },
                entries = items.map {
                    ResourceEntry(it.name, it.compressedSize, it.uncompressedSize)
                }.sortedByDescending { it.compressedBytes }
            )
        }.sortedByDescending { it.totalBytes }

        val allResourceEntries = entries.map {
            ResourceEntry(it.name, it.compressedSize, it.uncompressedSize)
        }

        return ResourceReport(
            filePath = filePath.toString(),
            totalResources = entries.size,
            totalBytes = entries.sumOf { it.compressedSize },
            categories = categories,
            largestFiles = allResourceEntries.sortedByDescending { it.compressedBytes }.take(15)
        )
    }

    private fun categorize(path: String): String {
        // Normalize AAB paths: "base/res/..." → "res/...", "base/lib/..." → "lib/..."
        val normalized = path.removePrefix("base/")
        return when {
            normalized.endsWith(".dex") -> "DEX"
            normalized.startsWith("lib/") -> "Native Libraries"
            normalized.startsWith("res/drawable") || normalized.startsWith("res/mipmap") -> "Drawables/Images"
            normalized.startsWith("res/layout") -> "Layouts"
            normalized.startsWith("res/values") -> "Values (strings, colors, etc.)"
            normalized.startsWith("res/raw") -> "Raw Resources"
            normalized.startsWith("res/xml") -> "XML Resources"
            normalized.startsWith("res/font") -> "Fonts"
            normalized.startsWith("res/anim") || normalized.startsWith("res/animator") -> "Animations"
            normalized.startsWith("res/navigation") -> "Navigation"
            normalized.startsWith("res/") -> "Other Resources"
            normalized.startsWith("assets/") -> "Assets"
            normalized == "resources.arsc" || normalized == "resources.pb" -> "Resource Table"
            path.startsWith("META-INF/") || path.contains("/META-INF/") -> "META-INF (signatures, etc.)"
            normalized == "AndroidManifest.xml" || path.endsWith("AndroidManifest.xml") -> "Manifest"
            path.startsWith("BUNDLE-METADATA/") -> "Bundle Metadata"
            path.startsWith("kotlin/") || path.startsWith("okhttp3/") || path.startsWith("com/") -> "Code Metadata"
            else -> "Other"
        }
    }
}
