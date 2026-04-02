package com.androidutil.core.scrcpy

import com.androidutil.i18n.Messages
import com.androidutil.util.ProcessRunner
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.net.URI
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import kotlin.io.path.*

/**
 * Manages scrcpy binary: locates system install, downloads from GitHub if needed,
 * caches in ~/.androidutil/scrcpy/.
 */
class ScrcpyManager(private val terminal: Terminal, private val msg: Messages) {

    companion object {
        private const val GITHUB_API_LATEST = "https://api.github.com/repos/Genymobile/scrcpy/releases/latest"
        private val CACHE_DIR = Path(System.getProperty("user.home"), ".androidutil", "scrcpy")
    }

    /**
     * Find scrcpy binary — checks system PATH first, then local cache.
     */
    fun findScrcpy(): Path? {
        // 1. Check system PATH
        val systemPath = findInPath()
        if (systemPath != null) return systemPath

        // 2. Check local cache
        val cachedBinary = findInCache()
        if (cachedBinary != null) return cachedBinary

        return null
    }

    /**
     * Ensure scrcpy is available — download if not found.
     */
    fun ensureScrcpy(): Path {
        findScrcpy()?.let { return it }

        terminal.println(yellow(msg["scrcpy.notFound"]))
        return downloadScrcpy()
    }

    /**
     * Download latest scrcpy from GitHub releases.
     */
    fun downloadScrcpy(): Path {
        val platform = detectPlatform()
        terminal.println(msg.get("scrcpy.platform", platform))

        // Get latest release info from GitHub API
        val releaseInfo = fetchLatestRelease()
        val version = releaseInfo.version
        val assetUrl = releaseInfo.assets[platform]
            ?: throw IllegalStateException(msg.get("scrcpy.platformNotSupported", platform))

        terminal.println(msg.get("scrcpy.downloading", version))

        // Prepare directories
        val downloadDir = CACHE_DIR.resolve("download")
        downloadDir.createDirectories()

        val fileName = assetUrl.substringAfterLast('/')
        val downloadPath = downloadDir.resolve(fileName)

        // Download with curl
        val curlResult = ProcessRunner.run(
            listOf("curl", "-L", "-o", downloadPath.absolutePathString(), "--progress-bar", assetUrl),
            timeoutSeconds = 300
        )

        if (curlResult.exitCode != 0 || !downloadPath.exists()) {
            throw IllegalStateException(msg.get("scrcpy.downloadFailed", curlResult.stderr))
        }

        val size = downloadPath.fileSize()
        terminal.println(green(msg.get("scrcpy.downloaded", size / 1024 / 1024)))

        // Extract
        terminal.println(msg["scrcpy.extracting"])
        val extractDir = CACHE_DIR.resolve(version)
        extractDir.createDirectories()

        if (fileName.endsWith(".tar.gz")) {
            extractTarGz(downloadPath, extractDir)
        } else if (fileName.endsWith(".zip")) {
            extractZip(downloadPath, extractDir)
        }

        // Find scrcpy binary in extracted dir
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val binaryName = if (isWindows) "scrcpy.exe" else "scrcpy"

        val scrcpyBinary = findBinaryRecursive(extractDir, binaryName)
            ?: throw IllegalStateException(msg["scrcpy.binaryNotFound"])

        // Make executable on Unix
        if (!isWindows) {
            ProcessRunner.run(listOf("chmod", "+x", scrcpyBinary.absolutePathString()))
        }

        // Write version marker
        CACHE_DIR.resolve(".version").writeText(version)

        // Cleanup download
        downloadPath.deleteIfExists()

        terminal.println(green(msg.get("scrcpy.ready", version)))
        return scrcpyBinary
    }

    private fun findInPath(): Path? {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val cmd = if (isWindows) listOf("where", "scrcpy") else listOf("which", "scrcpy")
        return try {
            val result = ProcessRunner.run(cmd, timeoutSeconds = 5)
            if (result.exitCode == 0 && result.stdout.isNotBlank()) {
                val path = Path(result.stdout.trim().lines().first())
                if (path.exists()) path else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun findInCache(): Path? {
        if (!CACHE_DIR.exists()) return null

        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val binaryName = if (isWindows) "scrcpy.exe" else "scrcpy"

        return findBinaryRecursive(CACHE_DIR, binaryName)
    }

    private fun findBinaryRecursive(dir: Path, name: String): Path? {
        if (!dir.exists()) return null
        return dir.toFile().walk()
            .filter { it.name == name && it.isFile }
            .firstOrNull()
            ?.toPath()
    }

    internal fun detectPlatform(): String {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()

        return when {
            osName.contains("mac") || osName.contains("darwin") -> {
                if (osArch == "aarch64" || osArch == "arm64") "macos-aarch64"
                else "macos-x86_64"
            }
            osName.contains("linux") -> "linux-x86_64"
            osName.contains("win") -> {
                if (osArch.contains("64")) "win64"
                else "win32"
            }
            else -> throw IllegalStateException(msg.get("scrcpy.unsupportedPlatform", "$osName $osArch"))
        }
    }

    internal data class ReleaseInfo(
        val version: String,
        val assets: Map<String, String> // platform -> download URL
    )

    internal fun fetchLatestRelease(): ReleaseInfo {
        // Use curl to fetch GitHub API (avoids needing JSON library)
        val result = ProcessRunner.run(
            listOf("curl", "-sL", "-H", "Accept: application/vnd.github.v3+json", GITHUB_API_LATEST),
            timeoutSeconds = 30
        )

        if (result.exitCode != 0 || result.stdout.isBlank()) {
            throw IllegalStateException(msg.get("scrcpy.apiFailed", result.stderr))
        }

        val json = result.stdout

        // Parse tag_name
        val versionRegex = """"tag_name"\s*:\s*"([^"]+)"""".toRegex()
        val version = versionRegex.find(json)?.groupValues?.get(1)
            ?: throw IllegalStateException(msg["scrcpy.versionNotFound"])

        // Parse asset URLs
        val assetRegex = """"browser_download_url"\s*:\s*"([^"]+scrcpy-[^"]+)"""".toRegex()
        val urls = assetRegex.findAll(json).map { it.groupValues[1] }.toList()

        val assets = mutableMapOf<String, String>()
        for (url in urls) {
            when {
                url.contains("macos-aarch64") && url.endsWith(".tar.gz") -> assets["macos-aarch64"] = url
                url.contains("macos-x86_64") && url.endsWith(".tar.gz") -> assets["macos-x86_64"] = url
                url.contains("linux-x86_64") && url.endsWith(".tar.gz") -> assets["linux-x86_64"] = url
                url.contains("win64") && url.endsWith(".zip") -> assets["win64"] = url
                url.contains("win32") && url.endsWith(".zip") -> assets["win32"] = url
            }
        }

        if (assets.isEmpty()) {
            throw IllegalStateException(msg["scrcpy.noAssets"])
        }

        return ReleaseInfo(version, assets)
    }

    private fun extractTarGz(archive: Path, targetDir: Path) {
        val result = ProcessRunner.run(
            listOf("tar", "-xzf", archive.absolutePathString(), "-C", targetDir.absolutePathString()),
            timeoutSeconds = 60
        )
        if (result.exitCode != 0) {
            throw IllegalStateException(msg.get("scrcpy.extractFailed", result.stderr))
        }
    }

    private fun extractZip(archive: Path, targetDir: Path) {
        ZipFile(archive.toFile()).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = targetDir.resolve(entry.name)
                if (entry.isDirectory) {
                    outFile.createDirectories()
                } else {
                    outFile.parent.createDirectories()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}
