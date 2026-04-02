package com.androidutil.core.download

import com.androidutil.i18n.Messages
import com.androidutil.util.ProcessRunner
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Downloads APK files from various sources.
 * Supports:
 *   1. apkeep (if installed) — downloads from APKPure without auth
 *   2. Direct URL download — user provides direct APK link
 */
class PlayStoreDownloader(private val terminal: Terminal, private val msg: Messages) {

    enum class Source { APKEEP_APKPURE, APKEEP_GPLAY, DIRECT_URL }

    /**
     * Check if apkeep is available on the system.
     */
    fun isApkeepAvailable(): Boolean {
        return try {
            val result = ProcessRunner.run(listOf("apkeep", "--version"), timeoutSeconds = 5)
            result.exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Download APK using apkeep from APKPure (no auth needed).
     */
    fun downloadFromApkPure(packageName: String, outputDir: Path): Path? {
        if (!isApkeepAvailable()) {
            terminal.println(red(msg["downloader.apkeepNotFound"]))
            terminal.println(yellow(msg["downloader.installHint"]))
            terminal.println(yellow("  ${msg["downloader.installHintBrew"]}"))
            terminal.println(yellow("  ${msg["downloader.installHintGithub"]}"))
            return null
        }

        terminal.println(msg.get("downloader.downloadingApkPure", packageName))
        terminal.println(gray(msg["downloader.pleaseWait"]))

        val cmd = listOf(
            "apkeep",
            "-a", packageName,
            "-d", "apkpure",
            outputDir.absolutePathString()
        )

        val result = ProcessRunner.run(cmd, timeoutSeconds = 300)

        if (result.exitCode != 0) {
            terminal.println(red(msg.get("downloader.downloadError", result.stderr.ifEmpty { result.stdout })))
            return null
        }

        // apkeep saves as packageName.apk or packageName.xapk in output dir
        val possibleFiles = listOf(
            outputDir.resolve("$packageName.apk"),
            outputDir.resolve("$packageName.xapk")
        )

        val downloadedFile = possibleFiles.firstOrNull { it.exists() }

        if (downloadedFile != null) {
            val size = com.androidutil.util.FileSize.format(downloadedFile.fileSize())
            terminal.println(green(msg.get("downloader.downloaded", downloadedFile.absolutePathString(), size)))
            return downloadedFile
        }

        // Try to find any recently created file matching the package
        val found = outputDir.listDirectoryEntries("$packageName*")
            .filter { it.isRegularFile() }
            .maxByOrNull { it.getLastModifiedTime() }

        if (found != null) {
            val size = com.androidutil.util.FileSize.format(found.fileSize())
            terminal.println(green(msg.get("downloader.downloaded", found.absolutePathString(), size)))
            return found
        }

        terminal.println(red(msg["downloader.notFound"]))
        return null
    }

    /**
     * Download APK using apkeep from Google Play (requires auth token).
     */
    fun downloadFromGooglePlay(packageName: String, email: String, aasToken: String, outputDir: Path): Path? {
        if (!isApkeepAvailable()) {
            terminal.println(red(msg["downloader.apkeepNotFound"]))
            return null
        }

        terminal.println(msg.get("downloader.downloadingGPlay", packageName))

        val cmd = listOf(
            "apkeep",
            "-a", packageName,
            "-d", "google-play",
            "-e", email,
            "-t", aasToken,
            outputDir.absolutePathString()
        )

        val result = ProcessRunner.run(cmd, timeoutSeconds = 300)

        if (result.exitCode != 0) {
            terminal.println(red(msg.get("downloader.downloadError", result.stderr.ifEmpty { result.stdout })))
            return null
        }

        val found = outputDir.listDirectoryEntries("$packageName*")
            .filter { it.isRegularFile() }
            .maxByOrNull { it.getLastModifiedTime() }

        if (found != null) {
            val size = com.androidutil.util.FileSize.format(found.fileSize())
            terminal.println(green(msg.get("downloader.downloaded", found.absolutePathString(), size)))
            return found
        }

        terminal.println(red(msg["downloader.downloadFailed"]))
        return null
    }

    /**
     * Download APK from a direct URL.
     */
    fun downloadFromUrl(url: String, outputDir: Path, fileName: String? = null): Path? {
        terminal.println(msg.get("downloader.downloadingUrl", url))

        val outputName = fileName ?: URI(url).path.substringAfterLast('/').ifBlank { "downloaded.apk" }
        val outputPath = outputDir.resolve(outputName)

        try {
            // Use curl/wget for reliability
            val curlResult = ProcessRunner.run(
                listOf("curl", "-L", "-o", outputPath.absolutePathString(), "-#", url),
                timeoutSeconds = 600
            )

            if (curlResult.exitCode == 0 && outputPath.exists() && outputPath.fileSize() > 0) {
                val size = com.androidutil.util.FileSize.format(outputPath.fileSize())
                terminal.println(green(msg.get("downloader.downloaded", outputPath.absolutePathString(), size)))
                return outputPath
            }

            // Fallback: Java HttpURLConnection
            terminal.println(yellow(msg["downloader.curlFailed"]))
            val connection = URI(url).toURL().openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 300_000

            if (connection.responseCode == 200) {
                connection.inputStream.use { input ->
                    outputPath.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val size = com.androidutil.util.FileSize.format(outputPath.fileSize())
                terminal.println(green(msg.get("downloader.downloaded", outputPath.absolutePathString(), size)))
                return outputPath
            } else {
                terminal.println(red(msg.get("downloader.httpError", connection.responseCode)))
                return null
            }
        } catch (e: Exception) {
            terminal.println(red(msg.get("downloader.downloadError", e.message ?: "")))
            return null
        }
    }
}
