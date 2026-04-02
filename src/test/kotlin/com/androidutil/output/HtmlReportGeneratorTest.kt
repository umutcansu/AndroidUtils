package com.androidutil.output

import com.androidutil.core.analyzer.ApkAnalysisResult
import com.androidutil.core.analyzer.SizeBreakdown
import com.androidutil.core.manifest.ManifestInfo
import com.androidutil.core.nativelib.NativeLibDetail
import com.androidutil.core.nativelib.NativeLibReport
import com.androidutil.core.playcompat.CheckStatus
import com.androidutil.core.playcompat.CompatCheck
import com.androidutil.core.playcompat.PlayCompatResult
import com.androidutil.core.resources.ResourceCategory
import com.androidutil.core.resources.ResourceEntry
import com.androidutil.core.resources.ResourceReport
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import kotlin.io.path.fileSize
import kotlin.io.path.readText

class HtmlReportGeneratorTest : DescribeSpec({

    val generator = HtmlReportGenerator()

    describe("generateReport") {
        it("should create valid HTML file with all sections") {
            val outputPath = Files.createTempFile("report-", ".html")
            try {
                val manifest = ManifestInfo(
                    "com.test.app", 42, "2.1.0", 24, 34, 34,
                    listOf("android.permission.INTERNET", "android.permission.CAMERA"),
                    10, 3, 2, 1
                )

                generator.generateReport(
                    filePath = "test.apk",
                    apkResult = ApkAnalysisResult(
                        filePath = "test.apk",
                        fileSizeBytes = 5000000,
                        manifestInfo = manifest,
                        sizeBreakdown = SizeBreakdown(5000000, 2000000, 1500000, 1000000, 300000, 200000),
                        dexFiles = emptyList(),
                        nativeLibraries = emptyList(),
                        alignmentResults = emptyList(),
                        signatures = null
                    ),
                    nativeLibReport = NativeLibReport(
                        filePath = "test.apk",
                        totalLibs = 1,
                        totalSizeBytes = 300000,
                        abiSummary = mapOf("arm64-v8a" to 1),
                        libraries = listOf(
                            NativeLibDetail("lib/arm64-v8a/libnative.so", "arm64-v8a", 300000, 500000, true, 64, true, 16384)
                        ),
                        allPageAligned = true
                    ),
                    playCompat = PlayCompatResult(
                        filePath = "test.apk",
                        checks = listOf(
                            CompatCheck("Target SDK", CheckStatus.PASS, "targetSdk=34"),
                            CompatCheck("16KB Alignment", CheckStatus.PASS, "All aligned")
                        ),
                        passCount = 2, failCount = 0, warnCount = 0, isReady = true
                    ),
                    resourceReport = ResourceReport(
                        filePath = "test.apk",
                        totalResources = 20,
                        totalBytes = 5000000,
                        categories = listOf(
                            ResourceCategory("DEX", 1, 2000000, listOf(ResourceEntry("classes.dex", 2000000, 3000000)))
                        ),
                        largestFiles = listOf(ResourceEntry("classes.dex", 2000000, 3000000))
                    ),
                    outputPath = outputPath
                )

                val html = outputPath.readText()
                outputPath.fileSize() shouldBeGreaterThan 0

                // Check HTML structure
                html shouldContain "<!DOCTYPE html>"
                html shouldContain "AndroidUtil Analiz Raporu"
                html shouldContain "test.apk"

                // Manifest section
                html shouldContain "com.test.app"
                html shouldContain "2.1.0"
                html shouldContain "Target SDK"

                // Size breakdown
                html shouldContain "Boyut Dagilimi"

                // Play compatibility
                html shouldContain "Google Play Uyumlulugu"
                html shouldContain "HAZIR"

                // Native libraries
                html shouldContain "Native Kutuphaneler"
                html shouldContain "libnative.so"

                // Resources
                html shouldContain "Kaynaklar"

                // Permissions
                html shouldContain "INTERNET"
                html shouldContain "CAMERA"

                // Footer
                html shouldContain "AndroidUtil v1.0.0"
            } finally {
                Files.deleteIfExists(outputPath)
            }
        }

        it("should handle minimal report with only file path") {
            val outputPath = Files.createTempFile("report-", ".html")
            try {
                generator.generateReport(
                    filePath = "minimal.apk",
                    outputPath = outputPath
                )

                val html = outputPath.readText()
                html shouldContain "<!DOCTYPE html>"
                html shouldContain "minimal.apk"
            } finally {
                Files.deleteIfExists(outputPath)
            }
        }
    }
})
