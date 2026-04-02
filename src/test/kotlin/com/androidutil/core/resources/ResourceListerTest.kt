package com.androidutil.core.resources

import com.androidutil.TestApkBuilder
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldContain
import java.nio.file.Files

class ResourceListerTest : DescribeSpec({

    val lister = ResourceLister()

    describe("list") {
        it("should categorize resources correctly") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addDex("classes.dex")
                    .addNativeLib("arm64-v8a", "libnative.so")
                    .addResource("res/drawable-hdpi/icon.png", 5000)
                    .addResource("res/layout/main.xml", 300)
                    .addResource("res/values/strings.xml", 200)
                    .addResource("res/raw/data.bin", 1000)
                    .addAsset("fonts/roboto.ttf", 20000)
                    .addManifest()
                    .addResourceTable()
                    .addMetaInf()
                    .build(apk)

                val report = lister.list(apk)

                val categoryNames = report.categories.map { it.name }
                categoryNames shouldContain "DEX"
                categoryNames shouldContain "Native Libraries"
                categoryNames shouldContain "Drawables/Images"
                categoryNames shouldContain "Layouts"
                categoryNames shouldContain "Assets"
                categoryNames shouldContain "Manifest"
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should count total resources") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addDex()
                    .addResource("res/drawable/a.png", 100)
                    .addResource("res/drawable/b.png", 200)
                    .addResource("res/layout/c.xml", 50)
                    .build(apk)

                val report = lister.list(apk)
                report.totalResources shouldBe 4 // 1 dex + 2 drawables + 1 layout
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should report largest files (top 15)") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder.standardApk(apk)

                val report = lister.list(apk)
                report.largestFiles shouldHaveAtLeastSize 1
                // Should be sorted by size descending
                for (i in 0 until report.largestFiles.size - 1) {
                    report.largestFiles[i].compressedBytes shouldBeGreaterThan
                        report.largestFiles[i + 1].compressedBytes - 1 // >= check
                }
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should calculate total bytes") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addDex()
                    .addManifest()
                    .build(apk)

                val report = lister.list(apk)
                report.totalBytes shouldBeGreaterThan 0
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should categorize assets separately from resources") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addAsset("data.json", 500)
                    .addAsset("config.yaml", 200)
                    .addResource("res/values/strings.xml", 100)
                    .build(apk)

                val report = lister.list(apk)
                val assetCategory = report.categories.first { it.name == "Assets" }
                assetCategory.fileCount shouldBe 2
            } finally {
                Files.deleteIfExists(apk)
            }
        }
    }
})
