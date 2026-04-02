package com.androidutil.util

import com.androidutil.TestApkBuilder
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import java.nio.file.Files

class ZipUtilsTest : DescribeSpec({

    describe("listEntries") {
        it("should list all entries in a test APK") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addDex()
                    .addNativeLib("arm64-v8a", "libnative.so")
                    .addManifest()
                    .addResource("res/drawable-hdpi/icon.png", 500)
                    .build(apk)

                val entries = ZipUtils.listEntries(apk)
                entries.map { it.name } shouldContain "classes.dex"
                entries.map { it.name } shouldContain "lib/arm64-v8a/libnative.so"
                entries.map { it.name } shouldContain "AndroidManifest.xml"
                entries.map { it.name } shouldContain "res/drawable-hdpi/icon.png"
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should report correct sizes") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addRawEntry("test.txt", "Hello World!".toByteArray())
                    .build(apk)

                val entries = ZipUtils.listEntries(apk)
                val testEntry = entries.first { it.name == "test.txt" }
                testEntry.uncompressedSize shouldBe 12L
                testEntry.isDirectory shouldBe false
            } finally {
                Files.deleteIfExists(apk)
            }
        }
    }

    describe("readEntry") {
        it("should read entry contents") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                val content = "Test content for reading"
                TestApkBuilder()
                    .addRawEntry("myfile.txt", content.toByteArray())
                    .build(apk)

                val bytes = ZipUtils.readEntry(apk, "myfile.txt")
                bytes.shouldNotBeNull()
                String(bytes) shouldBe content
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should return null for non-existent entry") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addDex()
                    .build(apk)

                ZipUtils.readEntry(apk, "nonexistent.txt").shouldBeNull()
            } finally {
                Files.deleteIfExists(apk)
            }
        }
    }

    describe("withMatchingEntries") {
        it("should find entries matching pattern") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addNativeLib("arm64-v8a", "libnative.so")
                    .addNativeLib("arm64-v8a", "libutils.so")
                    .addDex()
                    .build(apk)

                val soNames = ZipUtils.withMatchingEntries(apk, Regex(".*\\.so")) { name, _, _ -> name }
                soNames shouldHaveSize 2
                soNames.forEach { it shouldEndWith ".so" }
            } finally {
                Files.deleteIfExists(apk)
            }
        }
    }
})
