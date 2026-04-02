package com.androidutil.core.nativelib

import com.androidutil.TestApkBuilder
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class NativeLibInspectorTest : DescribeSpec({

    val inspector = NativeLibInspector()

    describe("inspect") {
        it("should find all native libraries in APK") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addNativeLib("arm64-v8a", "libnative.so", 16384L)
                    .addNativeLib("arm64-v8a", "libutils.so", 16384L)
                    .addNativeLib("armeabi-v7a", "libnative.so", 4096L)
                    .addDex()
                    .build(apk)

                val report = inspector.inspect(apk)

                report.totalLibs shouldBe 3
                report.libraries shouldHaveSize 3
                report.abiSummary["arm64-v8a"] shouldBe 2
                report.abiSummary["armeabi-v7a"] shouldBe 1
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should detect page alignment correctly") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addNativeLib("arm64-v8a", "libaligned.so", 16384L)
                    .addNativeLib("arm64-v8a", "libunaligned.so", 4096L)
                    .build(apk)

                val report = inspector.inspect(apk)

                report.allPageAligned.shouldBeFalse()
                val aligned = report.libraries.first { it.path.contains("libaligned") }
                val unaligned = report.libraries.first { it.path.contains("libunaligned") }
                aligned.pageAligned.shouldBeTrue()
                unaligned.pageAligned.shouldBeFalse()
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should detect all-aligned APK") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addNativeLib("arm64-v8a", "lib1.so", 16384L)
                    .addNativeLib("arm64-v8a", "lib2.so", 65536L) // larger alignment is also OK
                    .build(apk)

                val report = inspector.inspect(apk)
                report.allPageAligned.shouldBeTrue()
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should handle APK with no native libs") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addDex()
                    .addManifest()
                    .build(apk)

                val report = inspector.inspect(apk)
                report.totalLibs shouldBe 0
                report.libraries shouldHaveSize 0
                report.allPageAligned.shouldBeTrue()
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should extract ABI from path correctly") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addNativeLib("arm64-v8a", "libtest.so")
                    .addNativeLib("x86_64", "libtest.so")
                    .addNativeLib("armeabi-v7a", "libtest.so")
                    .addNativeLib("x86", "libtest.so")
                    .build(apk)

                val report = inspector.inspect(apk)
                val abis = report.libraries.map { it.abi }.toSet()
                abis shouldBe setOf("arm64-v8a", "x86_64", "armeabi-v7a", "x86")
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should report ELF class (32 vs 64 bit)") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addNativeLib("arm64-v8a", "lib64.so", 16384L)
                    .addNativeLib32("armeabi-v7a", "lib32.so", 16384L)
                    .build(apk)

                val report = inspector.inspect(apk)
                val lib64 = report.libraries.first { it.path.contains("lib64") }
                val lib32 = report.libraries.first { it.path.contains("lib32") }

                lib64.elfClass shouldBe 64
                lib32.elfClass shouldBe 32
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should report minimum alignment") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addNativeLib("arm64-v8a", "libtest.so", 4096L)
                    .build(apk)

                val report = inspector.inspect(apk)
                report.libraries.first().minAlignment shouldBe 4096L
            } finally {
                Files.deleteIfExists(apk)
            }
        }
    }
})
