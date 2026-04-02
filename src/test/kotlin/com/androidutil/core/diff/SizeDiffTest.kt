package com.androidutil.core.diff

import com.androidutil.TestApkBuilder
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class SizeDiffTest : DescribeSpec({

    val sizeDiff = SizeDiff()

    describe("compare") {
        it("should detect size differences between two APKs") {
            val oldApk = Files.createTempFile("old-", ".apk")
            val newApk = Files.createTempFile("new-", ".apk")
            try {
                TestApkBuilder.standardApk(oldApk)
                TestApkBuilder.standardApkV2(newApk)

                val result = sizeDiff.compare(oldApk, newApk)

                result.oldFile shouldBe oldApk.fileName.toString()
                result.newFile shouldBe newApk.fileName.toString()
                result.totalDiffBytes shouldBeGreaterThan 0 // V2 is bigger
            } finally {
                Files.deleteIfExists(oldApk)
                Files.deleteIfExists(newApk)
            }
        }

        it("should break down differences by category") {
            val oldApk = Files.createTempFile("old-", ".apk")
            val newApk = Files.createTempFile("new-", ".apk")
            try {
                TestApkBuilder.standardApk(oldApk)
                TestApkBuilder.standardApkV2(newApk)

                val result = sizeDiff.compare(oldApk, newApk)

                val categories = result.categoryDiffs.map { it.category }
                categories shouldBe listOf("dex", "res", "lib", "assets", "other")

                // V2 has a new dex file, so dex category should increase
                val dexDiff = result.categoryDiffs.first { it.category == "dex" }
                dexDiff.diffBytes shouldBeGreaterThan 0

                // V2 has a new asset (ml/model.tflite 50KB), so assets should increase
                val assetDiff = result.categoryDiffs.first { it.category == "assets" }
                assetDiff.diffBytes shouldBeGreaterThan 0
            } finally {
                Files.deleteIfExists(oldApk)
                Files.deleteIfExists(newApk)
            }
        }

        it("should report top increases") {
            val oldApk = Files.createTempFile("old-", ".apk")
            val newApk = Files.createTempFile("new-", ".apk")
            try {
                TestApkBuilder.standardApk(oldApk)
                TestApkBuilder.standardApkV2(newApk)

                val result = sizeDiff.compare(oldApk, newApk)

                result.topIncreases shouldHaveAtLeastSize 1
                result.topIncreases.forEach { it.diffBytes shouldBeGreaterThan 0 }
            } finally {
                Files.deleteIfExists(oldApk)
                Files.deleteIfExists(newApk)
            }
        }

        it("should report top decreases when entries are removed") {
            val oldApk = Files.createTempFile("old-", ".apk")
            val newApk = Files.createTempFile("new-", ".apk")
            try {
                // Old has entries that new doesn't
                TestApkBuilder()
                    .addDex()
                    .addResource("res/raw/big_data.bin", 10000)
                    .addResource("res/raw/old_stuff.bin", 5000)
                    .build(oldApk)

                TestApkBuilder()
                    .addDex()
                    .build(newApk)

                val result = sizeDiff.compare(oldApk, newApk)
                result.topDecreases shouldHaveAtLeastSize 2
                result.topDecreases.forEach { it.diffBytes shouldBeLessThan 0 }
            } finally {
                Files.deleteIfExists(oldApk)
                Files.deleteIfExists(newApk)
            }
        }

        it("should handle identical files") {
            val apk1 = Files.createTempFile("same1-", ".apk")
            val apk2 = Files.createTempFile("same2-", ".apk")
            try {
                TestApkBuilder()
                    .addDex()
                    .addManifest()
                    .build(apk1)

                TestApkBuilder()
                    .addDex()
                    .addManifest()
                    .build(apk2)

                val result = sizeDiff.compare(apk1, apk2)
                result.topIncreases shouldHaveSize 0
                result.topDecreases shouldHaveSize 0
            } finally {
                Files.deleteIfExists(apk1)
                Files.deleteIfExists(apk2)
            }
        }
    }
})
