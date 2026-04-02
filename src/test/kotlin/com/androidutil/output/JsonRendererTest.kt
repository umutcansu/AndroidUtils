package com.androidutil.output

import com.androidutil.core.diff.*
import com.androidutil.core.manifest.ManifestInfo
import com.androidutil.core.nativelib.NativeLibDetail
import com.androidutil.core.nativelib.NativeLibReport
import com.androidutil.core.playcompat.CheckStatus
import com.androidutil.core.playcompat.CompatCheck
import com.androidutil.core.playcompat.PlayCompatResult
import com.androidutil.core.resources.ResourceCategory
import com.androidutil.core.resources.ResourceEntry
import com.androidutil.core.resources.ResourceReport
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain

class JsonRendererTest : DescribeSpec({

    fun captureOutput(block: (Terminal) -> Unit): String {
        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE)
        val terminal = Terminal(terminalInterface = recorder)
        block(terminal)
        return recorder.output()
    }

    describe("renderSizeDiff") {
        it("should produce valid JSON with diff data") {
            val output = captureOutput { terminal ->
                val renderer = JsonRenderer(terminal)
                renderer.renderSizeDiff(SizeDiffResult(
                    oldFile = "old.apk",
                    newFile = "new.apk",
                    oldTotalBytes = 1000000,
                    newTotalBytes = 1500000,
                    totalDiffBytes = 500000,
                    categoryDiffs = listOf(
                        CategoryDiff("dex", 400000, 600000, 200000),
                        CategoryDiff("lib", 300000, 500000, 200000)
                    ),
                    topIncreases = listOf(EntryDiff("classes.dex", 400000, 600000, 200000)),
                    topDecreases = listOf(EntryDiff("res/old.png", 50000, 0, -50000))
                ))
            }

            output shouldContain "\"oldFile\": \"old.apk\""
            output shouldContain "\"newFile\": \"new.apk\""
            output shouldContain "\"totalDiffBytes\": 500000"
            output shouldContain "\"category\": \"dex\""
            output shouldContain "\"topIncreases\""
            output shouldContain "\"topDecreases\""
        }
    }

    describe("renderNativeLibReport") {
        it("should produce valid JSON with native lib data") {
            val output = captureOutput { terminal ->
                val renderer = JsonRenderer(terminal)
                renderer.renderNativeLibReport(NativeLibReport(
                    filePath = "test.apk",
                    totalLibs = 2,
                    totalSizeBytes = 500000,
                    abiSummary = mapOf("arm64-v8a" to 2),
                    libraries = listOf(
                        NativeLibDetail("lib/arm64-v8a/libnative.so", "arm64-v8a", 300000, 500000, true, 64, true, 16384),
                        NativeLibDetail("lib/arm64-v8a/libutils.so", "arm64-v8a", 200000, 400000, true, 64, false, 4096)
                    ),
                    allPageAligned = false
                ))
            }

            output shouldContain "\"totalLibs\": 2"
            output shouldContain "\"allPageAligned\": false"
            output shouldContain "libnative.so"
            output shouldContain "\"stripped\": true"
        }
    }

    describe("renderPlayCompat") {
        it("should produce valid JSON with play compatibility data") {
            val output = captureOutput { terminal ->
                val renderer = JsonRenderer(terminal)
                renderer.renderPlayCompat(PlayCompatResult(
                    filePath = "test.apk",
                    checks = listOf(
                        CompatCheck("Target SDK", CheckStatus.PASS, "targetSdk=34"),
                        CompatCheck("16KB Alignment", CheckStatus.FAIL, "2/5 incompatible")
                    ),
                    passCount = 1,
                    failCount = 1,
                    warnCount = 0,
                    isReady = false
                ))
            }

            output shouldContain "\"isReady\": false"
            output shouldContain "\"pass\": 1"
            output shouldContain "\"fail\": 1"
            output shouldContain "Target SDK"
        }
    }

    describe("renderResourceReport") {
        it("should produce valid JSON with resource data") {
            val output = captureOutput { terminal ->
                val renderer = JsonRenderer(terminal)
                renderer.renderResourceReport(ResourceReport(
                    filePath = "test.apk",
                    totalResources = 50,
                    totalBytes = 1000000,
                    categories = listOf(
                        ResourceCategory("DEX", 2, 400000, listOf(
                            ResourceEntry("classes.dex", 300000, 500000)
                        ))
                    ),
                    largestFiles = listOf(
                        ResourceEntry("classes.dex", 300000, 500000)
                    )
                ))
            }

            output shouldContain "\"totalResources\": 50"
            output shouldContain "\"totalBytes\": 1000000"
            output shouldContain "DEX"
            output shouldContain "classes.dex"
        }
    }

    describe("renderManifestDiff") {
        it("should produce valid JSON with manifest diff data") {
            val oldManifest = ManifestInfo("com.test.app", 10, "1.0", 21, 33, 33, listOf("android.permission.INTERNET"), 5, 2, 1, 0)
            val newManifest = ManifestInfo("com.test.app", 11, "1.1", 21, 34, 34, listOf("android.permission.INTERNET", "android.permission.CAMERA"), 6, 2, 1, 0)

            val output = captureOutput { terminal ->
                val renderer = JsonRenderer(terminal)
                renderer.renderManifestDiff(ManifestDiffResult(
                    oldFile = "old.apk",
                    newFile = "new.apk",
                    oldManifest = oldManifest,
                    newManifest = newManifest,
                    changes = listOf(
                        ManifestChange("Version Code", "10", "11"),
                        ManifestChange("Target SDK", "33", "34")
                    ),
                    permissionChanges = PermissionChanges(
                        added = listOf("android.permission.CAMERA"),
                        removed = emptyList(),
                        unchanged = listOf("android.permission.INTERNET")
                    )
                ))
            }

            output shouldContain "Version Code"
            output shouldContain "\"oldValue\": \"10\""
            output shouldContain "\"newValue\": \"11\""
            output shouldContain "permissionsAdded"
            output shouldContain "CAMERA"
        }
    }
})
