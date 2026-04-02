package com.androidutil

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

/**
 * End-to-end tests that run the real JAR with real APK files from F-Droid.
 * Tests every user-facing command of the application.
 *
 * Test APKs: Binary Eye v74 and v75 (open source barcode scanner)
 * Downloaded from F-Droid: de.markusfisch.android.binaryeye
 */
@Tags("e2e")
class E2eTest : DescribeSpec({

    val projectDir = File(System.getProperty("user.dir"))
    val jarFile = projectDir.resolve("build/libs/androidutil-1.0.0.jar")
    val testApk = projectDir.resolve("src/test/resources/test-files/test-app.apk")
    val testApkOld = projectDir.resolve("src/test/resources/test-files/test-app-old.apk")

    val canRun = jarFile.exists() && testApk.exists() && testApkOld.exists()

    fun run(vararg args: String, timeoutSec: Long = 30): Triple<Int, String, String> {
        val cmd = listOf("java", "-jar", jarFile.absolutePath) + args.toList()
        val process = ProcessBuilder(cmd)
            .also { it.environment()["COLUMNS"] = "200" }
            .start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        process.waitFor(timeoutSec, TimeUnit.SECONDS)
        return Triple(process.exitValue(), stdout, stderr)
    }

    beforeSpec {
        if (!canRun) {
            println("⚠️  E2E tests require JAR and test APKs. Run './gradlew shadowJar' and ensure test APKs exist.")
        }
    }

    // ========== 1. ROOT HELP ==========
    describe("root --help").config(enabled = canRun) {
        it("should list all 13 commands") {
            val (exit, out, _) = run("--help")
            exit shouldBeExactly 0
            listOf("analyze", "convert", "deeplink", "diff", "decode",
                "nativelib", "playcheck", "resources", "sizediff", "mdiff",
                "sign", "keystore", "adb").forEach {
                out shouldContain it
            }
        }

        it("should show app description") {
            val (_, out, _) = run("--help")
            out shouldContain "Android developer utility"
        }
    }

    // ========== 2. NATIVELIB ==========
    describe("nativelib — native lib analizi").config(enabled = canRun) {
        it("should find 24 native libraries across 4 ABIs") {
            val (exit, out, _) = run("nativelib", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "24 kutuphane"
            out shouldContain "arm64-v8a"
            out shouldContain "armeabi-v7a"
            out shouldContain "x86"
            out shouldContain "x86_64"
        }

        it("should detect 16KB alignment status") {
            val (exit, out, _) = run("nativelib", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "16KB"
            out shouldContain "OK"
        }

        it("should show library details: stripped status, ELF class") {
            val (exit, out, _) = run("nativelib", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "Evet" // stripped=Evet
            out shouldContain "64-bi" // 64-bit (truncated in table)
            out shouldContain "32-bi" // 32-bit
        }

        it("should produce valid JSON output") {
            val (exit, out, _) = run("--json", "nativelib", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "\"totalLibs\": 24"
            out shouldContain "\"arm64-v8a\""
            out shouldContain "\"stripped\": true"
            out shouldContain "\"pageAligned\":"
        }
    }

    // ========== 3. RESOURCES ==========
    describe("resources — kaynak listesi").config(enabled = canRun) {
        it("should list all resource categories") {
            val (exit, out, _) = run("resources", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "Native Libraries"
            out shouldContain "DEX"
            out shouldContain "Drawables/Images"
            out shouldContain "Layouts"
        }

        it("should report total file count and size") {
            val (exit, out, _) = run("resources", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "858 dosya"
            out shouldContain "5.9 MB"
        }

        it("should show largest files section") {
            val (exit, out, _) = run("resources", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "En Buyuk Dosyalar"
        }

        it("should produce valid JSON output") {
            val (exit, out, _) = run("--json", "resources", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "\"totalResources\":"
            out shouldContain "\"categories\":"
            out shouldContain "\"largestFiles\":"
        }
    }

    // ========== 4. PLAYCHECK ==========
    describe("playcheck — Google Play uyumluluk kontrolu").config(enabled = canRun) {
        it("should check all compatibility items") {
            val (exit, out, _) = run("playcheck", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "Target S"
            out shouldContain "16KB"
            out shouldContain "Hassas"
            out shouldContain "Min SDK"
            out shouldContain "64-bit"
        }

        it("should detect incompatibilities") {
            val (exit, out, _) = run("playcheck", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "HAZIR DEGIL"
            out shouldContain "HATA"
        }

        it("should detect 64-bit support as OK") {
            val (exit, out, _) = run("playcheck", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "OK"
            out shouldContain "arm64-v8a"
        }

        it("should produce valid JSON output") {
            val (exit, out, _) = run("--json", "playcheck", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "\"isReady\": false"
            out shouldContain "\"checks\":"
            out shouldContain "\"status\":"
        }
    }

    // ========== 5. SIZEDIFF ==========
    describe("sizediff — boyut karsilastirma").config(enabled = canRun) {
        it("should compare two versions and show size diff") {
            val (exit, out, _) = run("sizediff", testApkOld.absolutePath, testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "Boyut Farki"
            out shouldContain "Eski:"
            out shouldContain "Yeni:"
            out shouldContain "Fark:"
        }

        it("should show category breakdown") {
            val (exit, out, _) = run("sizediff", testApkOld.absolutePath, testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "Kategori"
            out shouldContain "DEX"
            out shouldContain "RES"
            out shouldContain "LIB"
        }

        it("should report top increases and decreases") {
            val (exit, out, _) = run("sizediff", testApkOld.absolutePath, testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "En Buyuk Artislar"
            out shouldContain "En Buyuk Azalislar"
            out shouldContain "classes.dex"
        }

        it("should produce valid JSON output") {
            val (exit, out, _) = run("--json", "sizediff", testApkOld.absolutePath, testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "\"oldFile\":"
            out shouldContain "\"newFile\":"
            out shouldContain "\"totalDiffBytes\":"
            out shouldContain "\"categoryDiffs\":"
        }
    }

    // ========== 6. DIFF (permission diff) ==========
    describe("diff — permission karsilastirma").config(enabled = canRun) {
        it("should compare permissions between two APKs") {
            val (exit, out, _) = run("diff", testApkOld.absolutePath, testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "Izin Farki"
        }

        it("should produce valid JSON output") {
            val (exit, out, _) = run("--json", "diff", testApkOld.absolutePath, testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "\"oldFile\":"
            out shouldContain "\"newFile\":"
        }
    }

    // ========== 7. DEEPLINK ==========
    describe("deeplink — deeplink analizi").config(enabled = canRun) {
        it("should report deeplink status even if none found") {
            val (exit, out, _) = run("deeplink", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "Deeplink"
        }

        it("should produce valid JSON output") {
            val (exit, out, _) = run("--json", "deeplink", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "\"totalCount\":"
        }
    }

    // ========== 8. SIGN VERIFY ==========
    describe("sign verify — imza dogrulama").config(enabled = canRun) {
        it("should verify APK signature") {
            val (exit, out, _) = run("sign", "verify", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "Imza Dogrulamasi"
            out shouldContain "GECERLI"
        }

        it("should show signature scheme details") {
            val (exit, out, _) = run("sign", "verify", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "v1"
            out shouldContain "v2"
            out shouldContain "v3"
        }

        it("should produce valid JSON output") {
            val (exit, out, _) = run("--json", "sign", "verify", testApk.absolutePath)
            exit shouldBeExactly 0
            out shouldContain "\"valid\":"
        }
    }

    // ========== 9. DECODE ==========
    describe("decode — stack trace decode").config(enabled = canRun) {
        it("should decode obfuscated stack trace with mapping file") {
            val mappingFile = Files.createTempFile("mapping-", ".txt")
            val stackFile = Files.createTempFile("stack-", ".txt")
            try {
                mappingFile.writeText("""
                    com.example.app.MainActivity -> a.a.a:
                        void onCreate(android.os.Bundle) -> a
                        void onResume() -> b
                    com.example.app.UserService -> a.a.b:
                        java.lang.String getUser(int) -> a
                    com.example.app.MyException -> a.a.c:
                """.trimIndent())

                stackFile.writeText("""
                    a.a.c: Something went wrong
                        at a.a.a.a(SourceFile:42)
                        at a.a.b.a(SourceFile:15)
                    Caused by: java.lang.NullPointerException: null
                        at a.a.a.b(SourceFile:88)
                """.trimIndent())

                val (exit, out, _) = run(
                    "decode", stackFile.toAbsolutePath().toString(),
                    "-m", mappingFile.toAbsolutePath().toString()
                )
                exit shouldBeExactly 0
                out shouldContain "MainActivity"
                out shouldContain "onCreate"
                out shouldContain "UserService"
                out shouldContain "getUser"
                out shouldContain "onResume"
            } finally {
                Files.deleteIfExists(mappingFile)
                Files.deleteIfExists(stackFile)
            }
        }

        it("should produce valid JSON output for decode") {
            val mappingFile = Files.createTempFile("mapping-", ".txt")
            val stackFile = Files.createTempFile("stack-", ".txt")
            try {
                mappingFile.writeText("com.example.Foo -> a.b:\n    void bar() -> a\n")
                stackFile.writeText("    at a.b.a(SourceFile:1)\n")

                val (exit, out, _) = run(
                    "--json", "decode", stackFile.toAbsolutePath().toString(),
                    "-m", mappingFile.toAbsolutePath().toString()
                )
                exit shouldBeExactly 0
                out shouldContain "\"mappingsApplied\":"
                out shouldContain "\"decodedLines\":"
            } finally {
                Files.deleteIfExists(mappingFile)
                Files.deleteIfExists(stackFile)
            }
        }
    }

    // ========== 10. ANALYZE ==========
    describe("analyze — APK tam analiz").config(enabled = canRun) {
        it("should run full analysis on APK") {
            val (exit, out, _) = run("analyze", testApk.absolutePath)
            out.shouldNotBeBlank()
        }
    }

    // ========== 11. SUBCOMMAND HELP ==========
    describe("subcommand --help parametreleri").config(enabled = canRun) {
        it("nativelib --help should show usage") {
            val (exit, out, _) = run("nativelib", "--help")
            exit shouldBeExactly 0
            out shouldContain "Usage:"
            out shouldContain "<file>"
        }

        it("playcheck --help should show usage") {
            val (exit, out, _) = run("playcheck", "--help")
            exit shouldBeExactly 0
            out shouldContain "Usage:"
            out shouldContain "Play"
        }

        it("resources --help should show usage") {
            val (exit, out, _) = run("resources", "--help")
            exit shouldBeExactly 0
            out shouldContain "Usage:"
        }

        it("sizediff --help should show both arguments") {
            val (exit, out, _) = run("sizediff", "--help")
            exit shouldBeExactly 0
            out shouldContain "<old>"
            out shouldContain "<new>"
        }

        it("mdiff --help should show both arguments") {
            val (exit, out, _) = run("mdiff", "--help")
            exit shouldBeExactly 0
            out shouldContain "<old>"
            out shouldContain "<new>"
        }

        it("decode --help should show mapping option") {
            val (exit, out, _) = run("decode", "--help")
            exit shouldBeExactly 0
            out shouldContain "mapping"
        }

        it("convert --help should show keystore options") {
            val (exit, out, _) = run("convert", "--help")
            exit shouldBeExactly 0
            out shouldContain "--keystore"
            out shouldContain "--output"
            out shouldContain "--universal"
        }

        it("sign --help should show subcommands") {
            val (exit, out, _) = run("sign", "--help")
            exit shouldBeExactly 0
            out shouldContain "apk"
            out shouldContain "verify"
        }

        it("keystore info --help should show password option") {
            val (exit, out, _) = run("keystore", "info", "--help")
            exit shouldBeExactly 0
            out shouldContain "--password"
        }

        it("adb --help should show subcommands") {
            val (exit, out, _) = run("adb", "--help")
            exit shouldBeExactly 0
            out shouldContain "install"
        }
    }

    // ========== 12. ERROR HANDLING ==========
    describe("hata durumlari").config(enabled = canRun) {
        it("should fail gracefully with non-existent file") {
            val (exit, _, _) = run("nativelib", "/tmp/nonexistent-file.apk")
            exit shouldBe 1
        }

        it("should fail gracefully with missing required argument") {
            val (exit, _, _) = run("nativelib")
            exit shouldBe 1
        }

        it("should fail gracefully with unknown command") {
            val (exit, out, err) = run("unknowncmd")
            exit shouldBe 1
        }
    }

    // ========== 13. VERBOSE MODE ==========
    describe("verbose mode").config(enabled = canRun) {
        it("should accept -v flag without error") {
            val (exit, out, _) = run("-v", "nativelib", testApk.absolutePath)
            exit shouldBeExactly 0
            out.shouldNotBeBlank()
        }
    }

    // ========== 14. VERSION ==========
    describe("version").config(enabled = canRun) {
        it("should show version with --version flag") {
            val (exit, out, _) = run("--version")
            exit shouldBeExactly 0
            out shouldContain "1.0.0"
        }
    }
})
