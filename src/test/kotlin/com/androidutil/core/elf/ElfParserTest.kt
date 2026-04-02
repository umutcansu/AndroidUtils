package com.androidutil.core.elf

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ElfParserTest : DescribeSpec({

    val parser = ElfParser()

    fun createElf64(ptLoadAlignments: List<Long>): ByteArray {
        // Minimal ELF64 with specified PT_LOAD p_align values
        val phnum = ptLoadAlignments.size
        val phoff = 64L // Program headers start right after ELF header
        val phentsize = 56 // Size of each program header entry (ELF64)
        val totalSize = 64 + (phentsize * phnum)

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // ELF header (64 bytes for ELF64)
        buf.put(0x7F.toByte()) // EI_MAG0
        buf.put('E'.code.toByte()) // EI_MAG1
        buf.put('L'.code.toByte()) // EI_MAG2
        buf.put('F'.code.toByte()) // EI_MAG3
        buf.put(2) // EI_CLASS = ELFCLASS64
        buf.put(1) // EI_DATA = ELFDATA2LSB (little-endian)
        buf.put(1) // EI_VERSION
        buf.put(0) // EI_OSABI
        buf.putLong(0) // EI_ABIVERSION + padding (8 bytes from pos 8)
        buf.putShort(2) // e_type = ET_EXEC (pos 16)
        buf.putShort(0xB7.toShort()) // e_machine = EM_AARCH64 (pos 18)
        buf.putInt(1) // e_version (pos 20)
        buf.putLong(0) // e_entry (pos 24)
        buf.putLong(phoff) // e_phoff (pos 32)
        buf.putLong(0) // e_shoff (pos 40)
        buf.putInt(0) // e_flags (pos 48)
        buf.putShort(64) // e_ehsize (pos 52)
        buf.putShort(phentsize.toShort()) // e_phentsize (pos 54)
        buf.putShort(phnum.toShort()) // e_phnum (pos 56)
        buf.putShort(0) // e_shentsize (pos 58)
        buf.putShort(0) // e_shnum (pos 60)
        buf.putShort(0) // e_shstrndx (pos 62)

        // Program headers (each 56 bytes for ELF64)
        for ((index, alignment) in ptLoadAlignments.withIndex()) {
            val offset = 64 + (index * phentsize)
            buf.position(offset)
            buf.putInt(1) // p_type = PT_LOAD
            buf.putInt(5) // p_flags = PF_R | PF_X
            buf.putLong(0) // p_offset
            buf.putLong(0) // p_vaddr
            buf.putLong(0) // p_paddr
            buf.putLong(0) // p_filesz
            buf.putLong(0) // p_memsz
            buf.putLong(alignment) // p_align
        }

        return buf.array()
    }

    fun createElf32(ptLoadAlignments: List<Long>): ByteArray {
        val phnum = ptLoadAlignments.size
        val phoff = 52 // ELF32 header size
        val phentsize = 32 // ELF32 program header size
        val totalSize = phoff + (phentsize * phnum)

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // ELF32 header
        buf.put(0x7F.toByte())
        buf.put('E'.code.toByte())
        buf.put('L'.code.toByte())
        buf.put('F'.code.toByte())
        buf.put(1) // EI_CLASS = ELFCLASS32
        buf.put(1) // EI_DATA = ELFDATA2LSB
        buf.put(1) // EI_VERSION
        buf.put(0) // EI_OSABI
        buf.putLong(0) // padding
        buf.putShort(2) // e_type (pos 16)
        buf.putShort(40.toShort()) // e_machine = EM_ARM (pos 18)
        buf.putInt(1) // e_version (pos 20)
        buf.putInt(0) // e_entry (pos 24)
        buf.putInt(phoff) // e_phoff (pos 28)
        buf.putInt(0) // e_shoff (pos 32)
        buf.putInt(0) // e_flags (pos 36)
        buf.putShort(52) // e_ehsize (pos 40)
        buf.putShort(phentsize.toShort()) // e_phentsize (pos 42)
        buf.putShort(phnum.toShort()) // e_phnum (pos 44)
        buf.putShort(0) // e_shentsize (pos 46)
        buf.putShort(0) // e_shnum (pos 48)
        buf.putShort(0) // e_shstrndx (pos 50)

        // Program headers (each 32 bytes for ELF32)
        for ((index, alignment) in ptLoadAlignments.withIndex()) {
            val offset = phoff + (index * phentsize)
            buf.position(offset)
            buf.putInt(1) // p_type = PT_LOAD
            buf.putInt(0) // p_offset
            buf.putInt(0) // p_vaddr
            buf.putInt(0) // p_paddr
            buf.putInt(0) // p_filesz
            buf.putInt(0) // p_memsz
            buf.putInt(5) // p_flags = PF_R | PF_X
            buf.putInt(alignment.toInt()) // p_align
        }

        return buf.array()
    }

    describe("ELF64 parsing") {
        it("should detect 16KB aligned library as compatible") {
            val elf = createElf64(listOf(16384L, 16384L))
            val result = parser.checkAlignment(
                ByteArrayInputStream(elf),
                "lib/arm64-v8a/libtest.so"
            )

            result.isCompatible.shouldBeTrue()
            result.abi shouldBe "arm64-v8a"
            result.elfClass shouldBe 64
            result.ptLoadSegments shouldHaveSize 2
            result.ptLoadSegments.forEach { it.compatible.shouldBeTrue() }
        }

        it("should detect 4KB aligned library as incompatible") {
            val elf = createElf64(listOf(4096L, 4096L))
            val result = parser.checkAlignment(
                ByteArrayInputStream(elf),
                "lib/arm64-v8a/libtest.so"
            )

            result.isCompatible.shouldBeFalse()
            result.ptLoadSegments.forEach { it.pAlign shouldBe 4096L }
            result.ptLoadSegments.forEach { it.compatible.shouldBeFalse() }
        }

        it("should detect mixed alignment as incompatible") {
            val elf = createElf64(listOf(16384L, 4096L))
            val result = parser.checkAlignment(
                ByteArrayInputStream(elf),
                "lib/arm64-v8a/libtest.so"
            )

            result.isCompatible.shouldBeFalse()
            result.ptLoadSegments[0].compatible.shouldBeTrue()
            result.ptLoadSegments[1].compatible.shouldBeFalse()
        }

        it("should handle larger alignments as compatible") {
            val elf = createElf64(listOf(65536L, 65536L))
            val result = parser.checkAlignment(
                ByteArrayInputStream(elf),
                "lib/arm64-v8a/libtest.so"
            )

            result.isCompatible.shouldBeTrue()
        }
    }

    describe("ELF32 parsing") {
        it("should parse 32-bit ELF correctly") {
            val elf = createElf32(listOf(4096L))
            val result = parser.checkAlignment(
                ByteArrayInputStream(elf),
                "lib/armeabi-v7a/libtest.so"
            )

            result.elfClass shouldBe 32
            result.abi shouldBe "armeabi-v7a"
            result.isCompatible.shouldBeFalse()
            result.ptLoadSegments shouldHaveSize 1
        }

        it("should detect 16KB aligned 32-bit ELF as compatible") {
            val elf = createElf32(listOf(16384L))
            val result = parser.checkAlignment(
                ByteArrayInputStream(elf),
                "lib/armeabi-v7a/libtest.so"
            )

            result.isCompatible.shouldBeTrue()
        }
    }

    describe("ABI extraction") {
        it("should extract ABI from standard path") {
            val elf = createElf64(listOf(16384L))
            val result = parser.checkAlignment(
                ByteArrayInputStream(elf),
                "lib/x86_64/libtest.so"
            )
            result.abi shouldBe "x86_64"
        }

        it("should extract ABI from AAB path") {
            val elf = createElf64(listOf(16384L))
            val result = parser.checkAlignment(
                ByteArrayInputStream(elf),
                "base/lib/arm64-v8a/libtest.so"
            )
            result.abi shouldBe "arm64-v8a"
        }

        it("should return unknown for non-standard path") {
            val elf = createElf64(listOf(16384L))
            val result = parser.checkAlignment(
                ByteArrayInputStream(elf),
                "native/libtest.so"
            )
            result.abi shouldBe "unknown"
        }
    }

    describe("error handling") {
        it("should handle too-small file") {
            val result = parser.checkAlignment(
                ByteArrayInputStream(ByteArray(10)),
                "lib/arm64-v8a/libtest.so"
            )
            result.isCompatible.shouldBeFalse()
            result.ptLoadSegments shouldHaveSize 0
        }

        it("should handle non-ELF file") {
            val notElf = ByteArray(100) { 0 }
            val result = parser.checkAlignment(
                ByteArrayInputStream(notElf),
                "lib/arm64-v8a/libtest.so"
            )
            result.isCompatible.shouldBeFalse()
        }
    }
})
