package com.androidutil.core.elf

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin ELF parser for checking 16KB page alignment of native libraries.
 * Reads ELF headers directly from byte streams without external dependencies.
 */
class ElfParser {

    companion object {
        private val ELF_MAGIC = byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
        private const val PT_LOAD = 1
        private const val ELFCLASS32 = 1
        private const val ELFCLASS64 = 2
        private const val ELFDATA2LSB = 1 // Little-endian
        private const val ELFDATA2MSB = 2 // Big-endian
    }

    fun checkAlignment(inputStream: InputStream, libraryPath: String): AlignmentCheckResult {
        val bytes = inputStream.readAllBytes()
        if (bytes.size < 64) {
            return errorResult(libraryPath, "File too small to be a valid ELF")
        }

        val buf = ByteBuffer.wrap(bytes)

        // Validate ELF magic
        val magic = ByteArray(4)
        buf.get(magic)
        if (!magic.contentEquals(ELF_MAGIC)) {
            return errorResult(libraryPath, "Not an ELF file")
        }

        val elfClass = buf.get(4).toInt() and 0xFF
        val dataEncoding = buf.get(5).toInt() and 0xFF

        buf.order(
            when (dataEncoding) {
                ELFDATA2LSB -> ByteOrder.LITTLE_ENDIAN
                ELFDATA2MSB -> ByteOrder.BIG_ENDIAN
                else -> ByteOrder.LITTLE_ENDIAN
            }
        )

        val abi = extractAbi(libraryPath)
        val elfBits = if (elfClass == ELFCLASS64) 64 else 32

        val ptLoadSegments = when (elfClass) {
            ELFCLASS64 -> parseElf64(buf)
            ELFCLASS32 -> parseElf32(buf)
            else -> emptyList()
        }

        return AlignmentCheckResult(
            libraryPath = libraryPath,
            abi = abi,
            elfClass = elfBits,
            ptLoadSegments = ptLoadSegments,
            isCompatible = ptLoadSegments.all { it.compatible }
        )
    }

    private fun parseElf64(buf: ByteBuffer): List<PtLoadInfo> {
        if (buf.capacity() < 64) return emptyList()

        val phoff = buf.getLong(32)         // e_phoff
        val phentsize = buf.getShort(54).toInt() and 0xFFFF  // e_phentsize
        val phnum = buf.getShort(56).toInt() and 0xFFFF      // e_phnum

        val segments = mutableListOf<PtLoadInfo>()

        for (i in 0 until phnum) {
            val offset = (phoff + (i.toLong() * phentsize)).toInt()
            if (offset + phentsize > buf.capacity()) break

            val pType = buf.getInt(offset)
            if (pType != PT_LOAD) continue

            val pFlags = buf.getInt(offset + 4)
            val pOffset = buf.getLong(offset + 8)
            val pVaddr = buf.getLong(offset + 16)
            val pAlign = buf.getLong(offset + 48)

            segments.add(
                PtLoadInfo(
                    segmentIndex = i,
                    pAlign = pAlign,
                    pOffset = pOffset,
                    pVaddr = pVaddr,
                    pFlags = pFlags,
                    compatible = pAlign >= AlignmentCheckResult.PAGE_16KB
                )
            )
        }

        return segments
    }

    private fun parseElf32(buf: ByteBuffer): List<PtLoadInfo> {
        if (buf.capacity() < 52) return emptyList()

        val phoff = buf.getInt(28).toLong() and 0xFFFFFFFFL   // e_phoff
        val phentsize = buf.getShort(42).toInt() and 0xFFFF    // e_phentsize
        val phnum = buf.getShort(44).toInt() and 0xFFFF        // e_phnum

        val segments = mutableListOf<PtLoadInfo>()

        for (i in 0 until phnum) {
            val offset = (phoff + (i.toLong() * phentsize)).toInt()
            if (offset + phentsize > buf.capacity()) break

            val pType = buf.getInt(offset)
            if (pType != PT_LOAD) continue

            val pOffset = buf.getInt(offset + 4).toLong() and 0xFFFFFFFFL
            val pVaddr = buf.getInt(offset + 8).toLong() and 0xFFFFFFFFL
            val pFlags = buf.getInt(offset + 24)
            val pAlign = buf.getInt(offset + 28).toLong() and 0xFFFFFFFFL

            segments.add(
                PtLoadInfo(
                    segmentIndex = i,
                    pAlign = pAlign,
                    pOffset = pOffset,
                    pVaddr = pVaddr,
                    pFlags = pFlags,
                    compatible = pAlign >= AlignmentCheckResult.PAGE_16KB
                )
            )
        }

        return segments
    }

    private fun extractAbi(path: String): String {
        // Extract ABI from path like "lib/arm64-v8a/libnative.so" or "base/lib/arm64-v8a/libnative.so"
        val parts = path.replace('\\', '/').split('/')
        val libIndex = parts.lastIndexOf("lib")
        return if (libIndex >= 0 && libIndex + 1 < parts.size) {
            parts[libIndex + 1]
        } else {
            "unknown"
        }
    }

    private fun errorResult(libraryPath: String, reason: String): AlignmentCheckResult {
        return AlignmentCheckResult(
            libraryPath = libraryPath,
            abi = extractAbi(libraryPath),
            elfClass = 0,
            ptLoadSegments = emptyList(),
            isCompatible = false
        )
    }
}
