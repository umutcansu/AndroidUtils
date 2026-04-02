package com.androidutil.cli

import com.androidutil.TestApkBuilder
import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Integration tests that run the fat JAR directly.
 * Requires: ./gradlew shadowJar before running.
 * Tests are skipped if the JAR doesn't exist.
 */
@Tags("integration")
class CliIntegrationTest : DescribeSpec({

    val projectDir = java.io.File(System.getProperty("user.dir"))
    val jarFile = projectDir.resolve("build/libs/androidutil-1.0.0.jar")

    val jarExists = jarFile.exists()

    fun runJar(vararg args: String): Pair<Int, String> {
        val cmd = listOf("java", "-jar", jarFile.absolutePath) + args.toList()
        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(30, TimeUnit.SECONDS)
        return process.exitValue() to output
    }

    beforeSpec {
        if (!jarExists) {
            println("⚠️  JAR not found at ${jarFile.absolutePath}. Run './gradlew shadowJar' first. Skipping CLI tests.")
        }
    }

    describe("--help").config(enabled = jarExists) {
        it("should show all commands") {
            val (exitCode, output) = runJar("--help")

            exitCode shouldBeExactly 0
            output shouldContain "analyze"
            output shouldContain "convert"
            output shouldContain "deeplink"
            output shouldContain "diff"
            output shouldContain "decode"
            output shouldContain "nativelib"
            output shouldContain "playcheck"
            output shouldContain "resources"
            output shouldContain "sizediff"
            output shouldContain "mdiff"
            output shouldContain "sign"
            output shouldContain "keystore"
            output shouldContain "adb"
        }
    }

    describe("nativelib command").config(enabled = jarExists) {
        it("should analyze native libs in test APK") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addNativeLib("arm64-v8a", "libnative.so", 16384L)
                    .addNativeLib("arm64-v8a", "libutils.so", 4096L)
                    .addDex()
                    .build(apk)

                val (exitCode, output) = runJar("nativelib", apk.toAbsolutePath().toString())
                exitCode shouldBeExactly 0
                output shouldContain "arm64-v8a"
                output shouldContain "libnative.so"
                output shouldContain "libutils.so"
            } finally {
                Files.deleteIfExists(apk)
            }
        }

        it("should output JSON when --json flag is used") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addNativeLib("arm64-v8a", "libnative.so", 16384L)
                    .build(apk)

                val (exitCode, output) = runJar("--json", "nativelib", apk.toAbsolutePath().toString())
                exitCode shouldBeExactly 0
                output shouldContain "\"totalLibs\""
                output shouldContain "\"libraries\""
            } finally {
                Files.deleteIfExists(apk)
            }
        }
    }

    describe("resources command").config(enabled = jarExists) {
        it("should list resources in test APK") {
            val apk = Files.createTempFile("test-", ".apk")
            try {
                TestApkBuilder()
                    .addDex()
                    .addResource("res/drawable-hdpi/icon.png", 5000)
                    .addResource("res/layout/main.xml", 300)
                    .addAsset("data.json", 500)
                    .addManifest()
                    .build(apk)

                val (exitCode, output) = runJar("resources", apk.toAbsolutePath().toString())
                exitCode shouldBeExactly 0
                output shouldContain "DEX"
            } finally {
                Files.deleteIfExists(apk)
            }
        }
    }

    describe("sizediff command").config(enabled = jarExists) {
        it("should compare two APK files") {
            val oldApk = Files.createTempFile("old-", ".apk")
            val newApk = Files.createTempFile("new-", ".apk")
            try {
                TestApkBuilder.standardApk(oldApk)
                TestApkBuilder.standardApkV2(newApk)

                val (exitCode, output) = runJar(
                    "sizediff",
                    oldApk.toAbsolutePath().toString(),
                    newApk.toAbsolutePath().toString()
                )
                exitCode shouldBeExactly 0
            } finally {
                Files.deleteIfExists(oldApk)
                Files.deleteIfExists(newApk)
            }
        }
    }

    describe("subcommand help").config(enabled = jarExists) {
        it("should show nativelib help") {
            val (exitCode, output) = runJar("nativelib", "--help")
            exitCode shouldBeExactly 0
            output shouldContain "native"
        }

        it("should show sizediff help") {
            val (exitCode, output) = runJar("sizediff", "--help")
            exitCode shouldBeExactly 0
            output shouldContain "Compare"
        }

        it("should show resources help") {
            val (exitCode, output) = runJar("resources", "--help")
            exitCode shouldBeExactly 0
            output shouldContain "resources"
        }

        it("should show sign help") {
            val (exitCode, output) = runJar("sign", "--help")
            exitCode shouldBeExactly 0
            output shouldContain "apk"
            output shouldContain "verify"
        }

        it("should show adb help") {
            val (exitCode, output) = runJar("adb", "--help")
            exitCode shouldBeExactly 0
        }
    }
})
