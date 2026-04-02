package com.androidutil.core.analyzer

import com.androidutil.core.elf.AlignmentCheckResult
import com.androidutil.core.manifest.ManifestInfo
import com.androidutil.core.signing.CertificateInfo
import com.androidutil.core.signing.SignatureVerificationResult
import com.androidutil.core.dex.DexFileInfo

data class NativeLibInfo(
    val path: String,
    val abi: String,
    val sizeBytes: Long
)

data class SizeBreakdown(
    val totalBytes: Long,
    val dexBytes: Long,
    val resourceBytes: Long,
    val nativeLibBytes: Long,
    val assetBytes: Long,
    val otherBytes: Long
)

data class AabAnalysisResult(
    val filePath: String,
    val fileSizeBytes: Long,
    val manifestInfo: ManifestInfo,
    val certificates: List<CertificateInfo>,
    val nativeLibraries: List<NativeLibInfo>,
    val alignmentResults: List<AlignmentCheckResult>,
    val estimatedSize: Long
)

data class ApkAnalysisResult(
    val filePath: String,
    val fileSizeBytes: Long,
    val manifestInfo: ManifestInfo,
    val sizeBreakdown: SizeBreakdown,
    val dexFiles: List<DexFileInfo>,
    val nativeLibraries: List<NativeLibInfo>,
    val alignmentResults: List<AlignmentCheckResult>,
    val signatures: SignatureVerificationResult?
)
