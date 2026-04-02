package com.androidutil

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream

/**
 * Builds realistic test APK (ZIP) files with proper structure for integration testing.
 * Creates files with real binary DEX headers, ELF native libs, resources, etc.
 */
class TestApkBuilder {

    private val entries = mutableListOf<Pair<String, ByteArray>>()

    fun addDex(name: String = "classes.dex", methodCount: Int = 100, classCount: Int = 50, stringCount: Int = 200): TestApkBuilder {
        entries.add(name to createDexFile(methodCount, classCount, stringCount))
        return this
    }

    fun addNativeLib(abi: String = "arm64-v8a", libName: String = "libnative.so", alignment: Long = 16384L): TestApkBuilder {
        val path = "lib/$abi/$libName"
        entries.add(path to createElf64(listOf(alignment)))
        return this
    }

    fun addNativeLib32(abi: String = "armeabi-v7a", libName: String = "libnative.so", alignment: Long = 4096L): TestApkBuilder {
        val path = "lib/$abi/$libName"
        entries.add(path to createElf32(listOf(alignment)))
        return this
    }

    fun addResource(path: String, size: Int = 100): TestApkBuilder {
        entries.add(path to ByteArray(size) { (it % 256).toByte() })
        return this
    }

    fun addManifest(): TestApkBuilder {
        // Fake binary XML — not parseable by aapt2 but valid as ZIP entry
        entries.add("AndroidManifest.xml" to ByteArray(256) { 0 })
        return this
    }

    fun addResourceTable(): TestApkBuilder {
        entries.add("resources.arsc" to ByteArray(512) { 0 })
        return this
    }

    fun addAsset(name: String, size: Int = 100): TestApkBuilder {
        entries.add("assets/$name" to ByteArray(size) { (it % 256).toByte() })
        return this
    }

    fun addMetaInf(): TestApkBuilder {
        entries.add("META-INF/MANIFEST.MF" to "Manifest-Version: 1.0\n".toByteArray())
        entries.add("META-INF/CERT.SF" to ByteArray(200))
        entries.add("META-INF/CERT.RSA" to ByteArray(300))
        return this
    }

    fun addRawEntry(path: String, data: ByteArray): TestApkBuilder {
        entries.add(path to data)
        return this
    }

    fun build(outputPath: Path): Path {
        ZipOutputStream(outputPath.outputStream()).use { zos ->
            for ((name, data) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return outputPath
    }

    companion object {

        /**
         * Creates a minimal but structurally valid DEX file header.
         */
        fun createDexFile(methodCount: Int = 100, classCount: Int = 50, stringCount: Int = 200): ByteArray {
            val buf = ByteBuffer.allocate(112).order(ByteOrder.LITTLE_ENDIAN)
            // Magic: "dex\n035\0"
            buf.put(byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00))
            // Checksum (fake)
            buf.putInt(0)
            // Signature (20 bytes, fake)
            buf.put(ByteArray(20))
            // File size
            buf.putInt(112)
            // Header size
            buf.putInt(112)
            // Endian tag
            buf.putInt(0x12345678)
            // Link size & off
            buf.putInt(0)
            buf.putInt(0)
            // Map offset
            buf.putInt(0)
            // String IDs size (offset 56)
            buf.putInt(stringCount)
            // String IDs offset
            buf.putInt(0)
            // Type IDs size
            buf.putInt(0)
            // Type IDs offset
            buf.putInt(0)
            // Proto IDs size
            buf.putInt(0)
            // Proto IDs offset
            buf.putInt(0)
            // Field IDs size
            buf.putInt(0)
            // Field IDs offset
            buf.putInt(0)
            // Method IDs size (offset 88)
            buf.putInt(methodCount)
            // Method IDs offset
            buf.putInt(0)
            // Class defs size (offset 96)
            buf.putInt(classCount)
            // Class defs offset
            buf.putInt(0)
            // Data size
            buf.putInt(0)
            // Data offset
            buf.putInt(0)

            return buf.array()
        }

        /**
         * Creates a minimal ELF64 binary with specified PT_LOAD alignments.
         */
        fun createElf64(ptLoadAlignments: List<Long>): ByteArray {
            val phnum = ptLoadAlignments.size
            val phoff = 64L
            val phentsize = 56
            val totalSize = 64 + (phentsize * phnum)

            val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

            // ELF header
            buf.put(0x7F.toByte())
            buf.put('E'.code.toByte())
            buf.put('L'.code.toByte())
            buf.put('F'.code.toByte())
            buf.put(2) // ELFCLASS64
            buf.put(1) // ELFDATA2LSB
            buf.put(1) // EI_VERSION
            buf.put(0) // EI_OSABI
            buf.putLong(0) // padding
            buf.putShort(2) // e_type = ET_EXEC
            buf.putShort(0xB7.toShort()) // EM_AARCH64
            buf.putInt(1) // e_version
            buf.putLong(0) // e_entry
            buf.putLong(phoff) // e_phoff
            buf.putLong(0) // e_shoff
            buf.putInt(0) // e_flags
            buf.putShort(64) // e_ehsize
            buf.putShort(phentsize.toShort()) // e_phentsize
            buf.putShort(phnum.toShort()) // e_phnum
            buf.putShort(0) // e_shentsize
            buf.putShort(0) // e_shnum
            buf.putShort(0) // e_shstrndx

            for ((index, alignment) in ptLoadAlignments.withIndex()) {
                val offset = 64 + (index * phentsize)
                buf.position(offset)
                buf.putInt(1) // PT_LOAD
                buf.putInt(5) // p_flags
                buf.putLong(0) // p_offset
                buf.putLong(0) // p_vaddr
                buf.putLong(0) // p_paddr
                buf.putLong(0) // p_filesz
                buf.putLong(0) // p_memsz
                buf.putLong(alignment) // p_align
            }

            return buf.array()
        }

        /**
         * Creates a minimal ELF32 binary with specified PT_LOAD alignments.
         */
        fun createElf32(ptLoadAlignments: List<Long>): ByteArray {
            val phnum = ptLoadAlignments.size
            val phoff = 52
            val phentsize = 32
            val totalSize = phoff + (phentsize * phnum)

            val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

            buf.put(0x7F.toByte())
            buf.put('E'.code.toByte())
            buf.put('L'.code.toByte())
            buf.put('F'.code.toByte())
            buf.put(1) // ELFCLASS32
            buf.put(1) // ELFDATA2LSB
            buf.put(1)
            buf.put(0)
            buf.putLong(0) // padding
            buf.putShort(2) // e_type
            buf.putShort(40.toShort()) // EM_ARM
            buf.putInt(1)
            buf.putInt(0) // e_entry
            buf.putInt(phoff) // e_phoff
            buf.putInt(0) // e_shoff
            buf.putInt(0) // e_flags
            buf.putShort(52) // e_ehsize
            buf.putShort(phentsize.toShort())
            buf.putShort(phnum.toShort())
            buf.putShort(0)
            buf.putShort(0)
            buf.putShort(0)

            for ((index, alignment) in ptLoadAlignments.withIndex()) {
                val offset = phoff + (index * phentsize)
                buf.position(offset)
                buf.putInt(1) // PT_LOAD
                buf.putInt(0)
                buf.putInt(0)
                buf.putInt(0)
                buf.putInt(0)
                buf.putInt(0)
                buf.putInt(5) // p_flags
                buf.putInt(alignment.toInt())
            }

            return buf.array()
        }

        /**
         * Creates a standard test APK with common structure.
         */
        fun standardApk(outputPath: Path): Path {
            return TestApkBuilder()
                .addDex("classes.dex", methodCount = 1500, classCount = 200, stringCount = 3000)
                .addDex("classes2.dex", methodCount = 500, classCount = 80, stringCount = 1000)
                .addNativeLib("arm64-v8a", "libnative.so", 16384L)
                .addNativeLib("arm64-v8a", "libutils.so", 16384L)
                .addNativeLib("armeabi-v7a", "libnative.so", 4096L) // intentionally 4KB
                .addManifest()
                .addResourceTable()
                .addResource("res/drawable-hdpi/icon.png", 5000)
                .addResource("res/drawable-xhdpi/icon.png", 8000)
                .addResource("res/layout/activity_main.xml", 500)
                .addResource("res/layout/fragment_list.xml", 300)
                .addResource("res/values/strings.xml", 1000)
                .addResource("res/values/colors.xml", 200)
                .addResource("res/raw/data.bin", 2000)
                .addAsset("fonts/roboto.ttf", 40000)
                .addAsset("config.json", 500)
                .addMetaInf()
                .build(outputPath)
        }

        /**
         * Creates a second version APK with size differences for diff testing.
         */
        fun standardApkV2(outputPath: Path): Path {
            return TestApkBuilder()
                .addDex("classes.dex", methodCount = 2000, classCount = 250, stringCount = 4000)
                .addDex("classes2.dex", methodCount = 800, classCount = 120, stringCount = 1500)
                .addDex("classes3.dex", methodCount = 200, classCount = 30, stringCount = 500) // new dex
                .addNativeLib("arm64-v8a", "libnative.so", 16384L)
                .addNativeLib("arm64-v8a", "libutils.so", 16384L)
                .addNativeLib("arm64-v8a", "libnew.so", 16384L) // new lib
                .addManifest()
                .addResourceTable()
                .addResource("res/drawable-hdpi/icon.png", 6000) // bigger
                .addResource("res/drawable-xhdpi/icon.png", 10000) // bigger
                .addResource("res/layout/activity_main.xml", 500)
                .addResource("res/layout/fragment_list.xml", 300)
                .addResource("res/layout/activity_settings.xml", 400) // new
                .addResource("res/values/strings.xml", 1200) // bigger
                .addResource("res/values/colors.xml", 200)
                .addAsset("fonts/roboto.ttf", 40000)
                .addAsset("config.json", 600) // bigger
                .addAsset("ml/model.tflite", 50000) // new asset
                .addMetaInf()
                .build(outputPath)
        }
    }
}
