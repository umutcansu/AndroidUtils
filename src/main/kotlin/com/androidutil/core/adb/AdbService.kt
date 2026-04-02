package com.androidutil.core.adb

import com.androidutil.sdk.AndroidSdkLocator
import com.androidutil.util.ProcessRunner
import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.name

data class DeviceInfo(
    val serial: String,
    val model: String,
    val status: String
)

class AdbService(private val terminal: Terminal) {

    private fun findAdb(): Path? {
        val sdkHome = AndroidSdkLocator.resolveAndroidHome() ?: return null
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val adbName = if (isWindows) "adb.exe" else "adb"
        val adb = sdkHome.resolve("platform-tools").resolve(adbName)
        return adb.takeIf { it.exists() }
    }

    private fun adb(): String {
        return findAdb()?.absolutePathString()
            ?: throw IllegalStateException("adb not found. Set ANDROID_HOME or install Android SDK Platform Tools.")
    }

    // ── Device Management ──

    fun listDevices(): List<DeviceInfo> {
        val result = ProcessRunner.run(listOf(adb(), "devices", "-l"))
        if (result.exitCode != 0) return emptyList()

        return result.stdout.lines()
            .drop(1) // skip "List of devices attached"
            .filter { it.isNotBlank() && !it.startsWith("*") }
            .map { line ->
                val parts = line.trim().split("\\s+".toRegex())
                val serial = parts.getOrElse(0) { "unknown" }
                val status = parts.getOrElse(1) { "unknown" }
                val model = Regex("model:(\\S+)").find(line)?.groupValues?.get(1) ?: ""
                DeviceInfo(serial, model, status)
            }
    }

    fun selectDevice(): String? {
        val devices = listDevices()
        if (devices.isEmpty()) {
            terminal.println(red("Bagli cihaz bulunamadi. ADB ile bagli bir cihaz veya emulator gerekli."))
            return null
        }
        if (devices.size == 1) {
            val dev = devices.first()
            terminal.println("Cihaz: ${dev.model.ifEmpty { dev.serial }} (${dev.status})")
            return dev.serial
        }

        // Multi-device interactive selection
        terminal.println("Birden fazla cihaz bagli — birini sec:")
        terminal.println()
        val selected = terminal.interactiveSelectList {
            devices.forEach { dev ->
                val label = "${dev.model.ifEmpty { dev.serial }} - ${dev.status} (${dev.serial})"
                addEntry(label)
            }
        } ?: return null

        val idx = devices.indexOfFirst { dev ->
            selected.contains(dev.serial)
        }
        return if (idx >= 0) devices[idx].serial else devices.first().serial
    }

    // ── Install APK ──

    fun installApk(apkPath: Path, device: String? = null) {
        val targetDevice = device ?: selectDevice() ?: return
        terminal.println("Installing ${apkPath.name}...")
        val cmd = mutableListOf(adb(), "-s", targetDevice, "install", "-r", "-d", apkPath.absolutePathString())

        val result = ProcessRunner.run(cmd, timeoutSeconds = 120)
        if (result.exitCode == 0 && result.stdout.contains("Success")) {
            terminal.println(green("Kurulum basarili!"))
        } else {
            terminal.println(red("Kurulum basarisiz: ${result.stderr.ifEmpty { result.stdout }}"))
        }
    }

    // ── Install + Launch ──

    fun installAndLaunch(apkPath: Path, packageName: String, device: String? = null) {
        val targetDevice = device ?: selectDevice() ?: return

        // Install
        terminal.println("Installing ${apkPath.name}...")
        val installCmd = mutableListOf(adb(), "-s", targetDevice, "install", "-r", "-d", apkPath.absolutePathString())
        val installResult = ProcessRunner.run(installCmd, timeoutSeconds = 120)

        if (installResult.exitCode != 0 || !installResult.stdout.contains("Success")) {
            terminal.println(red("Kurulum basarisiz: ${installResult.stderr.ifEmpty { installResult.stdout }}"))
            return
        }
        terminal.println(green("Kurulum basarili!"))

        // Find launcher activity
        val launcherActivity = findLauncherActivity(packageName, targetDevice)
        if (launcherActivity != null) {
            terminal.println("Uygulama baslatiliyor: $launcherActivity")
            val launchCmd = mutableListOf(
                adb(), "-s", targetDevice, "shell", "am", "start",
                "-n", "$packageName/$launcherActivity"
            )
            val launchResult = ProcessRunner.run(launchCmd)
            if (launchResult.exitCode == 0) {
                terminal.println(green("Uygulama baslatildi!"))
            } else {
                terminal.println(yellow("Baslatilamadi: ${launchResult.stderr.ifEmpty { launchResult.stdout }}"))
            }
        } else {
            // Fallback: monkey launch
            terminal.println("Launcher activity bulunamadi, monkey ile baslatiliyor...")
            val monkeyCmd = mutableListOf(
                adb(), "-s", targetDevice, "shell", "monkey",
                "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1"
            )
            ProcessRunner.run(monkeyCmd)
            terminal.println(green("Uygulama baslatildi!"))
        }
    }

    fun launchApp(packageName: String, device: String? = null) {
        val targetDevice = device ?: selectDevice() ?: return
        val launcherActivity = findLauncherActivity(packageName, targetDevice)

        if (launcherActivity != null) {
            terminal.println("Baslatiliyor: $packageName/$launcherActivity")
            val cmd = mutableListOf(
                adb(), "-s", targetDevice, "shell", "am", "start",
                "-n", "$packageName/$launcherActivity"
            )
            val result = ProcessRunner.run(cmd)
            if (result.exitCode == 0) {
                terminal.println(green("Uygulama baslatildi!"))
            } else {
                terminal.println(red("Hata: ${result.stderr.ifEmpty { result.stdout }}"))
            }
        } else {
            // Fallback
            val cmd = mutableListOf(
                adb(), "-s", targetDevice, "shell", "monkey",
                "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1"
            )
            ProcessRunner.run(cmd)
            terminal.println(green("Uygulama baslatildi!"))
        }
    }

    private fun findLauncherActivity(packageName: String, device: String): String? {
        val cmd = mutableListOf(
            adb(), "-s", device, "shell", "cmd", "package", "resolve-activity",
            "--brief", "-c", "android.intent.category.LAUNCHER", packageName
        )
        val result = ProcessRunner.run(cmd, timeoutSeconds = 5)
        if (result.exitCode == 0) {
            // Output format: priority=0 preferredOrder=0 ...\n com.pkg/.Activity
            val lines = result.stdout.trim().lines()
            val activityLine = lines.lastOrNull { it.contains("/") }
            if (activityLine != null) {
                val activity = activityLine.trim().substringAfter("/")
                return if (activity.startsWith(".")) activity else ".$activity".let { activityLine.trim().substringAfter("/") }
            }
        }

        // Fallback: dumpsys package
        val dumpCmd = mutableListOf(adb(), "-s", device, "shell", "dumpsys", "package", packageName)
        val dumpResult = ProcessRunner.run(dumpCmd, timeoutSeconds = 10)
        if (dumpResult.exitCode == 0) {
            val launcherRegex = Regex("$packageName/([\\w.]+).*category.*LAUNCHER", RegexOption.IGNORE_CASE)
            val match = launcherRegex.find(dumpResult.stdout)
            return match?.groupValues?.get(1)
        }

        return null
    }

    // ── Test Deeplink ──

    fun testDeeplink(url: String, device: String? = null) {
        terminal.println("Deeplink test ediliyor: $url")
        val targetDevice = device ?: selectDevice() ?: return
        val cmd = mutableListOf(
            adb(), "-s", targetDevice, "shell", "am", "start",
            "-a", "android.intent.action.VIEW", "-d", url
        )

        val result = ProcessRunner.run(cmd)
        if (result.exitCode == 0) {
            terminal.println(green("Deeplink gonderildi. Cihazi kontrol et."))
            if (result.stdout.contains("Error")) {
                terminal.println(yellow("Uyari: ${result.stdout.trim()}"))
            }
        } else {
            terminal.println(red("Hata: ${result.stderr.ifEmpty { result.stdout }}"))
        }
    }

    // ── Logcat ──

    fun logcat(packageName: String, device: String? = null) {
        val targetDevice = device ?: selectDevice() ?: return
        terminal.println("Logcat baslatiliyor: $packageName")
        terminal.println(gray("Durdurmak icin Ctrl+C"))
        terminal.println()

        // Get PID for package filtering
        val pidCmd = mutableListOf(adb(), "-s", targetDevice, "shell", "pidof", packageName)
        val pidResult = ProcessRunner.run(pidCmd)
        val pid = pidResult.stdout.trim()

        val cmd = mutableListOf(adb(), "-s", targetDevice)
        if (pid.isNotEmpty() && pid.all { it.isDigit() }) {
            cmd.addAll(listOf("logcat", "--pid=$pid"))
        } else {
            // Fallback: filter by tag
            cmd.addAll(listOf("logcat", "-s", packageName))
        }

        // Run interactively (will block until Ctrl+C)
        val pb = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .inheritIO()
        val process = pb.start()
        process.waitFor()
    }

    // ── Screenshot ──

    fun screenshot(outputPath: Path, device: String? = null) {
        val targetDevice = device ?: selectDevice() ?: return
        terminal.println("Screenshot aliniyor...")
        val remotePath = "/sdcard/screenshot_androidutil.png"

        val captureCmd = mutableListOf(adb(), "-s", targetDevice, "shell", "screencap", "-p", remotePath)
        val captureResult = ProcessRunner.run(captureCmd)
        if (captureResult.exitCode != 0) {
            terminal.println(red("Screenshot alinamadi: ${captureResult.stderr}"))
            return
        }

        val pullCmd = mutableListOf(adb(), "-s", targetDevice, "pull", remotePath, outputPath.absolutePathString())
        val pullResult = ProcessRunner.run(pullCmd)
        if (pullResult.exitCode == 0) {
            terminal.println(green("Screenshot kaydedildi: $outputPath"))
        } else {
            terminal.println(red("Dosya cekilemedi: ${pullResult.stderr}"))
        }

        // Cleanup
        ProcessRunner.run(mutableListOf(adb(), "-s", targetDevice, "shell", "rm", remotePath))
    }

    // ── Clear App Data ──

    fun clearAppData(packageName: String, device: String? = null) {
        val targetDevice = device ?: selectDevice() ?: return
        terminal.println("Uygulama verisi temizleniyor: $packageName")
        val cmd = mutableListOf(adb(), "-s", targetDevice, "shell", "pm", "clear", packageName)

        val result = ProcessRunner.run(cmd)
        if (result.exitCode == 0 && result.stdout.contains("Success")) {
            terminal.println(green("Veriler temizlendi."))
        } else {
            terminal.println(red("Hata: ${result.stderr.ifEmpty { result.stdout }}"))
        }
    }

    // ── Uninstall ──

    fun uninstall(packageName: String, device: String? = null) {
        val targetDevice = device ?: selectDevice() ?: return
        terminal.println("Uygulama kaldiriliyor: $packageName")
        val cmd = mutableListOf(adb(), "-s", targetDevice, "uninstall", packageName)

        val result = ProcessRunner.run(cmd, timeoutSeconds = 30)
        if (result.exitCode == 0 && result.stdout.contains("Success")) {
            terminal.println(green("Uygulama kaldirildi."))
        } else {
            terminal.println(red("Hata: ${result.stderr.ifEmpty { result.stdout }}"))
        }
    }

    // ── Pull APK from Device ──

    fun pullApk(packageName: String, outputDir: Path, device: String? = null): Path? {
        val targetDevice = device ?: selectDevice() ?: return null

        // Find APK path on device
        terminal.println("APK yolu araniyor: $packageName")
        val pathCmd = mutableListOf(adb(), "-s", targetDevice, "shell", "pm", "path", packageName)
        val pathResult = ProcessRunner.run(pathCmd, timeoutSeconds = 10)

        if (pathResult.exitCode != 0 || pathResult.stdout.isBlank()) {
            terminal.println(red("Paket bulunamadi: $packageName"))
            return null
        }

        // Parse paths (could be split APKs)
        val apkPaths = pathResult.stdout.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }

        if (apkPaths.isEmpty()) {
            terminal.println(red("APK yolu bulunamadi."))
            return null
        }

        // Get version info
        val versionCmd = mutableListOf(
            adb(), "-s", targetDevice, "shell", "dumpsys", "package", packageName
        )
        val versionResult = ProcessRunner.run(versionCmd, timeoutSeconds = 10)
        val versionName = Regex("versionName=([^\\s]+)").find(versionResult.stdout)?.groupValues?.get(1) ?: "unknown"
        val versionCode = Regex("versionCode=(\\d+)").find(versionResult.stdout)?.groupValues?.get(1) ?: "0"

        terminal.println("Versiyon: $versionName ($versionCode)")
        terminal.println("APK sayisi: ${apkPaths.size}")

        if (apkPaths.size == 1) {
            // Single APK — simple pull
            val remotePath = apkPaths.first()
            val fileName = "${packageName}_v${versionName}.apk"
            val outputPath = outputDir.resolve(fileName)

            terminal.println("Indiriliyor: $remotePath")
            val pullCmd = mutableListOf(adb(), "-s", targetDevice, "pull", remotePath, outputPath.absolutePathString())
            val pullResult = ProcessRunner.run(pullCmd, timeoutSeconds = 120)

            return if (pullResult.exitCode == 0) {
                terminal.println(green("APK indirildi: ${outputPath.absolutePathString()}"))
                terminal.println("Boyut: ${com.androidutil.util.FileSize.format(outputPath.fileSize())}")
                outputPath
            } else {
                terminal.println(red("Indirme hatasi: ${pullResult.stderr}"))
                null
            }
        } else {
            // Split APKs — pull all parts
            terminal.println(yellow("Split APK tespit edildi (${apkPaths.size} parca)"))
            val apkDir = outputDir.resolve("${packageName}_v${versionName}_split")
            apkDir.createDirectories()

            var allSuccess = true
            for ((index, remotePath) in apkPaths.withIndex()) {
                val partName = remotePath.substringAfterLast('/')
                val partPath = apkDir.resolve(partName)
                terminal.println("  [${ index + 1}/${apkPaths.size}] $partName")

                val pullCmd = mutableListOf(adb(), "-s", targetDevice, "pull", remotePath, partPath.absolutePathString())
                val pullResult = ProcessRunner.run(pullCmd, timeoutSeconds = 120)
                if (pullResult.exitCode != 0) {
                    terminal.println(red("    Hata: ${pullResult.stderr}"))
                    allSuccess = false
                }
            }

            return if (allSuccess) {
                terminal.println(green("Tum APK'lar indirildi: ${apkDir.absolutePathString()}"))
                apkDir
            } else {
                terminal.println(yellow("Bazi parcalar indirilemedi."))
                apkDir
            }
        }
    }

    // ── List Installed Packages ──

    fun listPackages(filter: String? = null, device: String? = null): List<String> {
        val targetDevice = device ?: selectDevice() ?: return emptyList()
        val cmd = mutableListOf(adb(), "-s", targetDevice, "shell", "pm", "list", "packages", "-3") // -3 = third party only
        val result = ProcessRunner.run(cmd, timeoutSeconds = 10)
        if (result.exitCode != 0) return emptyList()

        return result.stdout.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { filter == null || it.contains(filter, ignoreCase = true) }
            .sorted()
    }

    // ── Screen Mirror (scrcpy) ──

    fun startScrcpy(
        device: String? = null,
        record: Path? = null,
        maxSize: Int? = null,
        bitRate: String? = null,
        noAudio: Boolean = false,
        windowTitle: String? = null,
        stayAwake: Boolean = false,
        turnScreenOff: Boolean = false,
        noVideo: Boolean = false
    ) {
        val scrcpyPath = com.androidutil.core.scrcpy.ScrcpyManager(terminal).ensureScrcpy()
        val targetDevice = device ?: selectDevice() ?: return

        val cmd = mutableListOf(scrcpyPath.absolutePathString(), "-s", targetDevice)

        record?.let { cmd.addAll(listOf("--record", it.absolutePathString())) }
        maxSize?.let { cmd.addAll(listOf("--max-size", it.toString())) }
        bitRate?.let { cmd.addAll(listOf("--video-bit-rate", it)) }
        if (noAudio) cmd.add("--no-audio")
        windowTitle?.let { cmd.addAll(listOf("--window-title", it)) }
        if (stayAwake) cmd.add("--stay-awake")
        if (turnScreenOff) cmd.add("--turn-screen-off")
        if (noVideo) cmd.add("--no-video")

        terminal.println("scrcpy baslatiliyor...")
        terminal.println(gray("Kapatmak icin scrcpy penceresini kapat veya Ctrl+C"))
        terminal.println()

        val pb = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .inheritIO()
        val process = pb.start()
        process.waitFor()
    }
}
