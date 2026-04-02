package com.androidutil.cli

import com.androidutil.core.adb.AdbService
import com.androidutil.i18n.Messages
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path

class AdbCommand : CliktCommand(name = "adb") {
    override fun help(context: Context) = "ADB shortcuts"
    override fun run() = Unit
}

class AdbInstallCommand : CliktCommand(name = "install") {
    override fun help(context: Context) = "Install APK to connected device"

    private val file by argument(help = "Path to .apk file")
        .path(mustExist = true, canBeDir = false)

    private val device by option("-s", "--device", help = "Target device serial")

    private val config by requireObject<AppConfig>()

    override fun run() {
        AdbService(config.terminal, config.messages).installApk(file, device)
    }
}

class AdbDeeplinkCommand : CliktCommand(name = "deeplink") {
    override fun help(context: Context) = "Test deeplink on connected device"

    private val url by argument(help = "Deeplink URL to test")

    private val device by option("-s", "--device", help = "Target device serial")

    private val config by requireObject<AppConfig>()

    override fun run() {
        AdbService(config.terminal, config.messages).testDeeplink(url, device)
    }
}

class AdbScreenshotCommand : CliktCommand(name = "screenshot") {
    override fun help(context: Context) = "Take screenshot from connected device"

    private val output by argument(help = "Output file path")
        .path()

    private val device by option("-s", "--device", help = "Target device serial")

    private val config by requireObject<AppConfig>()

    override fun run() {
        AdbService(config.terminal, config.messages).screenshot(output, device)
    }
}

class AdbClearCommand : CliktCommand(name = "clear") {
    override fun help(context: Context) = "Clear app data on device"

    private val packageName by argument("package", help = "Package name (e.g. com.example.app)")

    private val device by option("-s", "--device", help = "Target device serial")
    private val force by option("-f", "--force", help = "Skip confirmation").flag()

    private val config by requireObject<AppConfig>()

    override fun run() {
        if (!force) {
            print(config.messages.get("adb.cmd.clearConfirm", packageName) + " ")
            val answer = readlnOrNull()?.trim()?.lowercase()
            if (answer != "e" && answer != "evet" && answer != "y" && answer != "yes") {
                config.terminal.println(config.messages["common.cancelled"])
                return
            }
        }
        AdbService(config.terminal, config.messages).clearAppData(packageName, device)
    }
}

class AdbMirrorCommand : CliktCommand(name = "mirror") {
    override fun help(context: Context) = "Mirror device screen using scrcpy"

    private val device by option("-s", "--device", help = "Target device serial")
    private val record by option("-r", "--record", help = "Record screen to file (e.g. video.mp4)").path()
    private val maxSize by option("-m", "--max-size", help = "Limit video dimension (e.g. 1024)").int()
    private val bitRate by option("-b", "--bit-rate", help = "Video bit rate (e.g. 8M)")
    private val noAudio by option("--no-audio", help = "Disable audio forwarding").flag()
    private val stayAwake by option("--stay-awake", help = "Keep device awake while mirroring").flag()
    private val turnScreenOff by option("--turn-screen-off", help = "Turn off device screen while mirroring").flag()
    private val noVideo by option("--no-video", help = "Disable video (audio only)").flag()
    private val windowTitle by option("--window-title", help = "Custom window title")

    private val config by requireObject<AppConfig>()

    override fun run() {
        AdbService(config.terminal, config.messages).startScrcpy(
            device = device,
            record = record,
            maxSize = maxSize,
            bitRate = bitRate,
            noAudio = noAudio,
            windowTitle = windowTitle,
            stayAwake = stayAwake,
            turnScreenOff = turnScreenOff,
            noVideo = noVideo
        )
    }
}

/** Top-level alias so users can run `androidutil mirror` instead of `androidutil adb mirror` */
class MirrorCommand : CliktCommand(name = "mirror") {
    override fun help(context: Context) = "Mirror device screen using scrcpy (shortcut for 'adb mirror')"

    private val device by option("-s", "--device", help = "Target device serial")
    private val record by option("-r", "--record", help = "Record screen to file (e.g. video.mp4)").path()
    private val maxSize by option("-m", "--max-size", help = "Limit video dimension (e.g. 1024)").int()
    private val bitRate by option("-b", "--bit-rate", help = "Video bit rate (e.g. 8M)")
    private val noAudio by option("--no-audio", help = "Disable audio forwarding").flag()
    private val stayAwake by option("--stay-awake", help = "Keep device awake while mirroring").flag()
    private val turnScreenOff by option("--turn-screen-off", help = "Turn off device screen while mirroring").flag()
    private val noVideo by option("--no-video", help = "Disable video (audio only)").flag()
    private val windowTitle by option("--window-title", help = "Custom window title")

    private val config by requireObject<AppConfig>()

    override fun run() {
        AdbService(config.terminal, config.messages).startScrcpy(
            device = device,
            record = record,
            maxSize = maxSize,
            bitRate = bitRate,
            noAudio = noAudio,
            windowTitle = windowTitle,
            stayAwake = stayAwake,
            turnScreenOff = turnScreenOff,
            noVideo = noVideo
        )
    }
}

class AdbUninstallCommand : CliktCommand(name = "uninstall") {
    override fun help(context: Context) = "Uninstall app from device"

    private val packageName by argument("package", help = "Package name (e.g. com.example.app)")

    private val device by option("-s", "--device", help = "Target device serial")
    private val force by option("-f", "--force", help = "Skip confirmation").flag()

    private val config by requireObject<AppConfig>()

    override fun run() {
        if (!force) {
            print(config.messages.get("adb.cmd.uninstallConfirm", packageName) + " ")
            val answer = readlnOrNull()?.trim()?.lowercase()
            if (answer != "e" && answer != "evet" && answer != "y" && answer != "yes") {
                config.terminal.println(config.messages["common.cancelled"])
                return
            }
        }
        AdbService(config.terminal, config.messages).uninstall(packageName, device)
    }
}
