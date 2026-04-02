package com.androidutil.core.download

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
class PlayStoreDownloader(private val terminal: Terminal) {

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
            terminal.println(red("apkeep bulunamadi."))
            terminal.println(yellow("Kurmak icin: cargo install apkeep"))
            terminal.println(yellow("  veya: brew install apkeep"))
            terminal.println(yellow("  veya: https://github.com/EFForg/apkeep/releases"))
            return null
        }

        terminal.println("APKPure'dan indiriliyor: $packageName")
        terminal.println(gray("Bu islem biraz zaman alabilir..."))

        val cmd = listOf(
            "apkeep",
            "-a", packageName,
            "-d", "apkpure",
            outputDir.absolutePathString()
        )

        val result = ProcessRunner.run(cmd, timeoutSeconds = 300)

        if (result.exitCode != 0) {
            terminal.println(red("Indirme hatasi: ${result.stderr.ifEmpty { result.stdout }}"))
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
            terminal.println(green("Indirildi: ${downloadedFile.absolutePathString()} ($size)"))
            return downloadedFile
        }

        // Try to find any recently created file matching the package
        val found = outputDir.listDirectoryEntries("$packageName*")
            .filter { it.isRegularFile() }
            .maxByOrNull { it.getLastModifiedTime() }

        if (found != null) {
            val size = com.androidutil.util.FileSize.format(found.fileSize())
            terminal.println(green("Indirildi: ${found.absolutePathString()} ($size)"))
            return found
        }

        terminal.println(red("Dosya indirilemedi veya bulunamadi."))
        return null
    }

    /**
     * Download APK using apkeep from Google Play (requires auth token).
     */
    fun downloadFromGooglePlay(packageName: String, email: String, aasToken: String, outputDir: Path): Path? {
        if (!isApkeepAvailable()) {
            terminal.println(red("apkeep bulunamadi."))
            return null
        }

        terminal.println("Google Play'den indiriliyor: $packageName")

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
            terminal.println(red("Indirme hatasi: ${result.stderr.ifEmpty { result.stdout }}"))
            return null
        }

        val found = outputDir.listDirectoryEntries("$packageName*")
            .filter { it.isRegularFile() }
            .maxByOrNull { it.getLastModifiedTime() }

        if (found != null) {
            val size = com.androidutil.util.FileSize.format(found.fileSize())
            terminal.println(green("Indirildi: ${found.absolutePathString()} ($size)"))
            return found
        }

        terminal.println(red("Dosya indirilemedi."))
        return null
    }

    /**
     * Download APK from a direct URL.
     */
    fun downloadFromUrl(url: String, outputDir: Path, fileName: String? = null): Path? {
        terminal.println("Indiriliyor: $url")

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
                terminal.println(green("Indirildi: ${outputPath.absolutePathString()} ($size)"))
                return outputPath
            }

            // Fallback: Java HttpURLConnection
            terminal.println(yellow("curl basarisiz, Java ile deneniyor..."))
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
                terminal.println(green("Indirildi: ${outputPath.absolutePathString()} ($size)"))
                return outputPath
            } else {
                terminal.println(red("HTTP hatasi: ${connection.responseCode}"))
                return null
            }
        } catch (e: Exception) {
            terminal.println(red("Indirme hatasi: ${e.message}"))
            return null
        }
    }
}
