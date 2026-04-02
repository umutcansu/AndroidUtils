package com.androidutil.core.elf

data class PtLoadInfo(
    val segmentIndex: Int,
    val pAlign: Long,
    val pOffset: Long,
    val pVaddr: Long,
    val pFlags: Int,
    val compatible: Boolean
)

data class AlignmentCheckResult(
    val libraryPath: String,
    val abi: String,
    val elfClass: Int, // 32 or 64
    val ptLoadSegments: List<PtLoadInfo>,
    val isCompatible: Boolean
) {
    companion object {
        const val PAGE_16KB = 16384L
    }
}
