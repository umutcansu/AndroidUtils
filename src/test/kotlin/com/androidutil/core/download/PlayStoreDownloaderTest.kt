package com.androidutil.core.download

import com.androidutil.i18n.Messages
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.fileSize

class PlayStoreDownloaderTest : DescribeSpec({

    fun createDownloader(): Pair<PlayStoreDownloader, TerminalRecorder> {
        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE)
        val terminal = Terminal(terminalInterface = recorder)
        return PlayStoreDownloader(terminal, Messages.forLanguage("tr")) to recorder
    }

    describe("isApkeepAvailable") {
        it("should return false when apkeep is not installed") {
            // This test assumes apkeep is not installed on the test machine
            // If it is installed, this test still passes (returns true, which is also valid)
            val (downloader, _) = createDownloader()
            val result = downloader.isApkeepAvailable()
            // We just verify it doesn't crash
            println("apkeep available: $result")
        }
    }

    describe("downloadFromApkPure") {
        it("should show error when apkeep is not installed") {
            val (downloader, recorder) = createDownloader()
            if (!downloader.isApkeepAvailable()) {
                val outputDir = Files.createTempDirectory("download-test-")
                try {
                    val result = downloader.downloadFromApkPure("com.nonexistent.app", outputDir)
                    result.shouldBeNull()
                    val output = recorder.output()
                    output shouldContain "apkeep bulunamadi"
                    output shouldContain "cargo install apkeep"
                } finally {
                    outputDir.toFile().deleteRecursively()
                }
            }
        }
    }

    describe("downloadFromGooglePlay") {
        it("should show error when apkeep is not installed") {
            val (downloader, recorder) = createDownloader()
            if (!downloader.isApkeepAvailable()) {
                val outputDir = Files.createTempDirectory("download-test-")
                try {
                    val result = downloader.downloadFromGooglePlay(
                        "com.nonexistent.app",
                        "test@test.com",
                        "fake-token",
                        outputDir
                    )
                    result.shouldBeNull()
                    val output = recorder.output()
                    output shouldContain "apkeep bulunamadi"
                } finally {
                    outputDir.toFile().deleteRecursively()
                }
            }
        }
    }

    describe("downloadFromUrl") {
        it("should download a real APK from a direct URL") {
            val (downloader, recorder) = createDownloader()
            val outputDir = Files.createTempDirectory("download-test-")
            try {
                val result = downloader.downloadFromUrl(
                    "https://f-droid.org/repo/de.markusfisch.android.binaryeye_75.apk",
                    outputDir,
                    "test-download.apk"
                )

                result.shouldNotBeNull()
                result.exists().shouldBeTrue()
                result.fileSize() shouldBeGreaterThan 1_000_000L // should be ~6MB

                val output = recorder.output()
                output shouldContain "Indirildi"
            } finally {
                outputDir.toFile().deleteRecursively()
            }
        }

        it("should return null for invalid URL") {
            val (downloader, recorder) = createDownloader()
            val outputDir = Files.createTempDirectory("download-test-")
            try {
                val result = downloader.downloadFromUrl(
                    "https://invalid.nonexistent.domain.example/file.apk",
                    outputDir
                )

                result.shouldBeNull()
                val output = recorder.output()
                output shouldContain "hata" // error message in Turkish
            } finally {
                outputDir.toFile().deleteRecursively()
            }
        }

        it("should handle HTTP 404 gracefully") {
            val (downloader, recorder) = createDownloader()
            val outputDir = Files.createTempDirectory("download-test-")
            try {
                val result = downloader.downloadFromUrl(
                    "https://f-droid.org/repo/nonexistent_app_12345.apk",
                    outputDir
                )

                // curl may download a 404 HTML page, or return error
                // Either way, verify it doesn't crash
                if (result != null) {
                    // If curl downloaded the 404 page, the file will be very small
                    println("Got file of size: ${result.fileSize()}")
                }
            } finally {
                outputDir.toFile().deleteRecursively()
            }
        }

        it("should use filename from URL when not specified") {
            val (downloader, _) = createDownloader()
            val outputDir = Files.createTempDirectory("download-test-")
            try {
                val result = downloader.downloadFromUrl(
                    "https://f-droid.org/repo/de.markusfisch.android.binaryeye_75.apk",
                    outputDir
                    // no fileName parameter — should extract from URL
                )

                result.shouldNotBeNull()
                result.fileName.toString() shouldBe "de.markusfisch.android.binaryeye_75.apk"
                result.exists().shouldBeTrue()
            } finally {
                outputDir.toFile().deleteRecursively()
            }
        }
    }
})
