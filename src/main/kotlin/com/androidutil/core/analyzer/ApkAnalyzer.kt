package com.androidutil.core.analyzer

import com.androidutil.core.dex.DexAnalyzer
import com.androidutil.core.dex.DexFileInfo
import com.androidutil.core.elf.AlignmentCheckResult
import com.androidutil.core.elf.ElfParser
import com.androidutil.core.manifest.ManifestParser
import com.androidutil.core.signing.SignatureVerifier
import com.androidutil.util.ZipUtils
import java.nio.file.Path
import kotlin.io.path.fileSize

class ApkAnalyzer {

    private val elfParser = ElfParser()
    private val dexAnalyzer = DexAnalyzer()
    private val manifestParser = ManifestParser()
    private val signatureVerifier = SignatureVerifier()

    fun analyze(apkPath: Path): ApkAnalysisResult {
        val fileSizeBytes = apkPath.fileSize()
        val entries = ZipUtils.listEntries(apkPath)

        // Manifest info
        val manifestInfo = manifestParser.parseFromApk(apkPath)

        // Size breakdown
        val sizeBreakdown = calculateSizeBreakdown(entries, fileSizeBytes)

        // DEX analysis
        val dexFiles = analyzeDexFiles(apkPath, entries)

        // Native libraries
        val nativeLibs = entries.filter {
            it.name.startsWith("lib/") && it.name.endsWith(".so")
        }.map { entry ->
            NativeLibInfo(
                path = entry.name,
                abi = extractAbi(entry.name),
                sizeBytes = entry.uncompressedSize
            )
        }

        // 16KB alignment check
        val alignmentResults = checkNativeAlignment(apkPath)

        // Signature verification
        val signatures = try {
            signatureVerifier.verify(apkPath)
        } catch (e: Exception) {
            null
        }

        return ApkAnalysisResult(
            filePath = apkPath.toString(),
            fileSizeBytes = fileSizeBytes,
            manifestInfo = manifestInfo,
            sizeBreakdown = sizeBreakdown,
            dexFiles = dexFiles,
            nativeLibraries = nativeLibs,
            alignmentResults = alignmentResults,
            signatures = signatures
        )
    }

    private fun calculateSizeBreakdown(
        entries: List<ZipUtils.ZipEntryInfo>,
        totalSize: Long
    ): SizeBreakdown {
        var dexBytes = 0L
        var resourceBytes = 0L
        var nativeLibBytes = 0L
        var assetBytes = 0L

        for (entry in entries) {
            when {
                entry.name.endsWith(".dex") -> dexBytes += entry.compressedSize
                entry.name.startsWith("lib/") -> nativeLibBytes += entry.compressedSize
                entry.name.startsWith("assets/") -> assetBytes += entry.compressedSize
                entry.name.startsWith("res/") || entry.name == "resources.arsc" ->
                    resourceBytes += entry.compressedSize
            }
        }

        val otherBytes = totalSize - dexBytes - resourceBytes - nativeLibBytes - assetBytes

        return SizeBreakdown(
            totalBytes = totalSize,
            dexBytes = dexBytes,
            resourceBytes = resourceBytes,
            nativeLibBytes = nativeLibBytes,
            assetBytes = assetBytes,
            otherBytes = otherBytes.coerceAtLeast(0)
        )
    }

    private fun analyzeDexFiles(
        apkPath: Path,
        entries: List<ZipUtils.ZipEntryInfo>
    ): List<DexFileInfo> {
        val dexEntries = entries.filter { it.name.endsWith(".dex") }
        return ZipUtils.withMatchingEntries(apkPath, Regex(".*\\.dex")) { name, stream, size ->
            dexAnalyzer.analyze(stream, name, size)
        }
    }

    private fun checkNativeAlignment(apkPath: Path): List<AlignmentCheckResult> {
        return ZipUtils.withMatchingEntries(
            apkPath,
            Regex("lib/[^/]+/.*\\.so")
        ) { name, stream, _ ->
            elfParser.checkAlignment(stream, name)
        }
    }

    private fun extractAbi(path: String): String {
        val parts = path.split('/')
        return if (parts.size >= 2) parts[1] else "unknown"
    }
}
