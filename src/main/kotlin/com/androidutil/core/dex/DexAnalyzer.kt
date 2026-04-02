package com.androidutil.core.dex

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DexFileInfo(
    val name: String,
    val sizeBytes: Long,
    val methodCount: Int,
    val classCount: Int,
    val stringCount: Int
)

/**
 * Analyzes DEX files for method counts and basic statistics.
 * Parses the DEX file header directly.
 */
class DexAnalyzer {

    companion object {
        private val DEX_MAGIC = byteArrayOf(0x64, 0x65, 0x78, 0x0A) // "dex\n"
    }

    fun analyze(inputStream: InputStream, name: String, sizeBytes: Long): DexFileInfo {
        val bytes = inputStream.readAllBytes()
        if (bytes.size < 112) {
            return DexFileInfo(name, sizeBytes, 0, 0, 0)
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Validate magic (first 4 bytes should be "dex\n")
        val magic = ByteArray(4)
        buf.get(magic)
        if (!magic.contentEquals(DEX_MAGIC)) {
            return DexFileInfo(name, sizeBytes, 0, 0, 0)
        }

        // DEX header offsets (after 8-byte magic+version and 4-byte checksum and 20-byte signature)
        // string_ids_size at offset 56
        // class_defs_size at offset 96
        // method_ids_size at offset 88

        val stringCount = buf.getInt(56)
        val methodCount = buf.getInt(88)
        val classCount = buf.getInt(96)

        return DexFileInfo(
            name = name,
            sizeBytes = sizeBytes,
            methodCount = methodCount,
            classCount = classCount,
            stringCount = stringCount
        )
    }
}
