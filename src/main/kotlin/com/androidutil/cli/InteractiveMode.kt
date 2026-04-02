package com.androidutil.cli

import com.androidutil.core.adb.AdbService
import com.androidutil.core.analyzer.AabAnalyzer
import com.androidutil.core.analyzer.ApkAnalyzer
import com.androidutil.core.deeplink.DeeplinkAnalyzer
import com.androidutil.core.diff.ManifestDiff
import com.androidutil.core.download.PlayStoreDownloader
import com.androidutil.core.diff.PermissionDiff
import com.androidutil.core.diff.SizeDiff
import com.androidutil.core.nativelib.NativeLibInspector
import com.androidutil.core.playcompat.PlayCompatChecker
import com.androidutil.core.resources.ResourceLister
import com.androidutil.core.signing.KeystoreInspector
import com.androidutil.core.signing.SignatureVerifier
import com.androidutil.core.stacktrace.StackTraceDecoder
import com.androidutil.i18n.Messages
import com.androidutil.output.HtmlReportGenerator
import com.androidutil.output.JsonRenderer
import com.androidutil.output.OutputRenderer
import com.androidutil.output.TerminalRenderer
import com.androidutil.util.FileChooser
import com.androidutil.util.FileSize
import com.androidutil.util.RecentFiles
import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.terminal.StringPrompt
import com.github.ajalt.mordant.terminal.Terminal
import java.nio.file.Path
import kotlin.io.path.*

class InteractiveMode(private val config: AppConfig) {

    private val terminal = config.terminal
    private val msg = config.messages
    private val backKey = msg["common.back"]
    private val renderer: OutputRenderer = if (config.json) {
        JsonRenderer(terminal)
    } else {
        TerminalRenderer(terminal, msg)
    }

    private var currentFile: Path? = null

    fun start() {
        terminal.println()
        terminal.println(bold(cyan("  ╔══════════════════════════════════════╗")))
        terminal.println(bold(cyan("  ║       AndroidUtil v1.0.0             ║")))
        terminal.println(bold(cyan("  ║  Android Developer Utility Tool      ║")))
        terminal.println(bold(cyan("  ╚══════════════════════════════════════╝")))
        terminal.println()

        while (true) {
            if (currentFile == null) {
                terminal.println(bold(msg["interactive.selectFile"]))
                terminal.println()
                val file = selectFile(listOf("aab", "apk"))
                if (file == null) {
                    val toolAction = selectToolsOrQuit() ?: break
                    if (toolAction == "quit") break
                    handleIndependentTool(toolAction)
                } else {
                    currentFile = file
                    RecentFiles.add(file)
                }
            } else {
                val action = selectOperation() ?: break
                when (action) {
                    "change" -> { currentFile = null }
                    "quit" -> break
                    else -> handleAction(action)
                }
            }
            terminal.println()
        }

        terminal.println(green(msg["common.goodbye"]))
    }

    // ─────────────────────────────────────────────────────────
    //  OPERATION MENU
    // ─────────────────────────────────────────────────────────

    private fun selectOperation(): String? {
        val file = currentFile ?: return null
        val ext = file.extension.lowercase()
        val size = try { FileSize.format(file.fileSize()) } catch (_: Exception) { "?" }

        terminal.println(bold(green(msg.get("interactive.fileLoaded", file.name, size))))
        terminal.println(gray("  ${file.parent}"))
        terminal.println()

        val entries = mutableListOf<String>()

        // File-specific operations
        entries.add(msg["menu.analyze"])
        entries.add(msg["menu.deeplink"])
        entries.add(msg["menu.verify"])
        entries.add(msg["menu.nativelib"])
        entries.add(msg["menu.resources"])
        entries.add(msg["menu.playcheck"])

        if (ext == "aab") {
            entries.add(msg["menu.convert"])
        }

        entries.add(msg["menu.comparison"])
        entries.add(msg["menu.mdiff"])
        entries.add(msg["menu.sizediff"])

        entries.add(msg["menu.otherTools"])
        entries.add(msg["menu.download"])
        entries.add(msg["menu.decode"])
        entries.add(msg["menu.keystore"])
        entries.add(msg["menu.adb"])
        entries.add(msg["menu.report"])

        entries.add(msg["menu.separator"])
        entries.add(msg["menu.change"])
        entries.add(msg["menu.quit"])

        val selected = terminal.interactiveSelectList {
            entries.forEach { addEntry(it) }
        } ?: return null

        val action = selected.substringBefore(" ").trim()
        if (action.startsWith("─")) return ""
        return action
    }

    private fun selectToolsOrQuit(): String? {
        terminal.println()
        terminal.println(bold(msg["interactive.noFileSelected"]))
        terminal.println()

        val selected = terminal.interactiveSelectList {
            addEntry(msg["menu.tools.download"])
            addEntry(msg["menu.tools.decode"])
            addEntry(msg["menu.tools.keystore"])
            addEntry(msg["menu.tools.adb"])
            addEntry(msg["menu.tools.file"])
            addEntry(msg["menu.tools.quit"])
        } ?: return null

        return selected.substringBefore(" ").trim()
    }

    // ─────────────────────────────────────────────────────────
    //  ACTION DISPATCH
    // ─────────────────────────────────────────────────────────

    private fun handleAction(action: String) {
        when (action) {
            "analyze" -> handleAnalyze()
            "deeplink" -> handleDeeplink()
            "convert" -> handleConvert()
            "verify" -> handleVerify()
            "nativelib" -> handleNativeLib()
            "resources" -> handleResources()
            "playcheck" -> handlePlayCheck()
            "mdiff" -> handleManifestDiff()
            "sizediff" -> handleSizeDiff()
            "download" -> handleIndependentTool("download")
            "decode" -> handleIndependentTool("decode")
            "keystore" -> handleIndependentTool("keystore")
            "adb" -> handleIndependentTool("adb")
            "report" -> handleReport()
            "file" -> { currentFile = null }
            "" -> {}
        }
    }

    private fun handleIndependentTool(tool: String) {
        when (tool) {
            "download" -> handleDownload()
            "decode" -> handleDecode()
            "keystore" -> handleKeystore()
            "adb" -> handleAdb()
            "file" -> { currentFile = null }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  HANDLERS
    // ─────────────────────────────────────────────────────────

    private fun handleAnalyze() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println(msg.get("interactive.analyzing", file.name))
        terminal.println()
        try {
            when (file.extension.lowercase()) {
                "aab" -> renderer.renderAabAnalysis(AabAnalyzer().analyze(file))
                "apk" -> renderer.renderApkAnalysis(ApkAnalyzer().analyze(file))
            }
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
            if (config.verbose) e.printStackTrace()
        }
    }

    private fun handleConvert() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println(bold(msg["interactive.conversionMode"]))
        terminal.println()

        val mode = terminal.interactiveSelectList {
            addEntry(msg["interactive.conversionUniversal"])
            addEntry(msg["interactive.conversionDefault"])
            addEntry(backKey)
        }?.substringBefore(" ")?.trim() ?: return
        if (mode == "←") return

        val universal = mode == "universal"
        val defaultOutput = file.resolveSibling(file.name.replace(".aab", if (universal) ".apk" else ".apks"))

        terminal.println()
        terminal.println(msg.get("interactive.outputFile", green(defaultOutput.absolutePathString())))
        terminal.println(gray(msg["interactive.outputPathHint"]))
        val customPath = StringPrompt(msg["interactive.outputPathPrompt"], terminal).ask()
        val outputPath = if (customPath.isNullOrBlank()) defaultOutput else Path(customPath.trim())

        terminal.println()
        try {
            val conversionConfig = com.androidutil.core.converter.ConversionConfig(
                aabPath = file, outputPath = outputPath,
                keystorePath = null, keystorePassword = null,
                keyAlias = null, keyPassword = null,
                deviceSpecPath = null, universal = universal
            )
            com.androidutil.core.converter.AabConverter(terminal, msg).convert(conversionConfig)
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
            if (config.verbose) e.printStackTrace()
        }
    }

    private fun handleVerify() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println("Verifying ${file.name}...")
        terminal.println()
        try {
            renderer.renderSignatureVerification(SignatureVerifier().verify(file))
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
        }
    }

    private fun handleDeeplink() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println("Extracting deeplinks from ${file.name}...")
        terminal.println()
        try {
            val result = DeeplinkAnalyzer().analyze(file)
            renderer.renderDeeplinkAnalysis(result)
            if (result.deeplinks.isNotEmpty()) {
                terminal.println(bold(msg["interactive.adbTestPrompt"]))
                terminal.println()
                val options = result.deeplinks.map { it.fullUrl } + listOf(backKey)
                val selected = terminal.interactiveSelectList {
                    options.forEach { addEntry(it) }
                } ?: return
                if (selected != backKey) {
                    try { AdbService(terminal, msg).testDeeplink(selected) }
                    catch (e: Exception) { terminal.println(red(msg.get("interactive.adbError", e.message ?: ""))) }
                }
            }
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
        }
    }

    private fun handleNativeLib() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println("Inspecting native libraries in ${file.name}...")
        terminal.println()
        try {
            val result = NativeLibInspector().inspect(file)
            renderer.renderNativeLibReport(result)
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
        }
    }

    private fun handleResources() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println("Listing resources in ${file.name}...")
        terminal.println()
        try {
            val result = ResourceLister().list(file)
            renderer.renderResourceReport(result)
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
        }
    }

    private fun handlePlayCheck() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println("Checking Google Play compatibility for ${file.name}...")
        terminal.println()
        try {
            val result = PlayCompatChecker().check(file, msg)
            renderer.renderPlayCompat(result)
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
        }
    }

    private fun handleManifestDiff() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println(green(msg.get("interactive.oldFile", file.name)))
        terminal.println(bold(msg["interactive.selectNewFile"]))
        val newFile = selectFile(listOf("aab", "apk")) ?: return
        terminal.println()
        terminal.println("Comparing manifests...")
        terminal.println()
        try {
            val result = ManifestDiff().compare(file, newFile)
            renderer.renderManifestDiff(result)
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
        }
    }

    private fun handleSizeDiff() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println(green(msg.get("interactive.oldFile", file.name)))
        terminal.println(bold(msg["interactive.selectNewFile"]))
        val newFile = selectFile(listOf("aab", "apk")) ?: return
        terminal.println()
        terminal.println("Comparing sizes...")
        terminal.println()
        try {
            val result = SizeDiff().compare(file, newFile)
            renderer.renderSizeDiff(result)
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
        }
    }

    private fun handleDownload() {
        terminal.println()
        val downloader = PlayStoreDownloader(terminal, msg)

        val source = terminal.interactiveSelectList {
            addEntry(msg["download.apkpure"])
            addEntry(msg["download.url"])
            addEntry(backKey)
        }?.substringBefore(" ")?.trim() ?: return

        if (source == "←") return

        when (source) {
            "apkpure" -> {
                if (!downloader.isApkeepAvailable()) {
                    terminal.println(red(msg["downloader.apkeepNotFound"]))
                    terminal.println()
                    terminal.println(bold(msg["download.installHint"]))
                    terminal.println(cyan("  cargo install apkeep"))
                    terminal.println(cyan("  brew install apkeep"))
                    terminal.println(cyan("  https://github.com/EFForg/apkeep/releases"))
                    return
                }
                terminal.println()
                val pkg = StringPrompt(msg["download.packagePrompt"], terminal).ask()
                if (pkg.isNullOrBlank()) return

                val outputDir = Path(System.getProperty("user.dir"))
                val file = downloader.downloadFromApkPure(pkg, outputDir)

                if (file != null && (file.name.endsWith(".apk") || file.name.endsWith(".xapk"))) {
                    terminal.println()
                    terminal.println(bold(msg["interactive.loadDownloadedPrompt"]))
                    val choice = terminal.interactiveSelectList {
                        addEntry(msg["interactive.loadDownloadedYes"])
                        addEntry("Hayir")
                    } ?: return
                    if (choice == msg["interactive.loadDownloadedYes"]) {
                        currentFile = file
                        RecentFiles.add(file)
                        terminal.println(green(msg.get("interactive.fileLoadedStatus", file.name)))
                    }
                }
            }
            "url" -> {
                terminal.println()
                val url = StringPrompt("APK URL", terminal).ask()
                if (url.isNullOrBlank()) return

                val outputDir = Path(System.getProperty("user.dir"))
                val file = downloader.downloadFromUrl(url, outputDir)

                if (file != null && file.name.endsWith(".apk")) {
                    terminal.println()
                    terminal.println(bold(msg["interactive.loadDownloadedPrompt"]))
                    val choice = terminal.interactiveSelectList {
                        addEntry(msg["interactive.loadDownloadedYes"])
                        addEntry("Hayir")
                    } ?: return
                    if (choice == msg["interactive.loadDownloadedYes"]) {
                        currentFile = file
                        RecentFiles.add(file)
                        terminal.println(green(msg.get("interactive.fileLoadedStatus", file.name)))
                    }
                }
            }
        }
    }

    private fun handleReport() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println("Generating HTML report for ${file.name}...")
        terminal.println()

        val defaultOutput = file.resolveSibling(file.nameWithoutExtension + "_report.html")
        terminal.println(msg.get("interactive.reportFile", green(defaultOutput.absolutePathString())))
        terminal.println(gray(msg["interactive.reportPathHint"]))
        val customPath = StringPrompt(msg["interactive.reportPathPrompt"], terminal).ask()
        val outputPath = if (customPath.isNullOrBlank()) defaultOutput else Path(customPath.trim())

        try {
            val ext = file.extension.lowercase()
            val apkResult = if (ext == "apk") ApkAnalyzer().analyze(file) else null
            val aabResult = if (ext == "aab") AabAnalyzer().analyze(file) else null
            val nativeLibReport = try { NativeLibInspector().inspect(file) } catch (_: Exception) { null }
            val playCompat = try { PlayCompatChecker().check(file, msg) } catch (_: Exception) { null }
            val resourceReport = try { ResourceLister().list(file) } catch (_: Exception) { null }

            HtmlReportGenerator(msg).generateReport(
                filePath = file.absolutePathString(),
                apkResult = apkResult,
                aabResult = aabResult,
                nativeLibReport = nativeLibReport,
                playCompat = playCompat,
                resourceReport = resourceReport,
                outputPath = outputPath
            )
            terminal.println(green(msg.get("interactive.reportCreated", outputPath.absolutePathString())))
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
            if (config.verbose) e.printStackTrace()
        }
    }

    private fun handleDecode() {
        terminal.println(bold(msg["interactive.stackTraceFile"]))
        val stackFile = selectFile(listOf("txt", "log", "stacktrace")) ?: return
        terminal.println(bold(msg["interactive.mappingFile"]))
        val mappingFile = selectFile(listOf("txt", "map")) ?: return
        terminal.println()
        terminal.println("Decoding stack trace...")
        terminal.println()
        try {
            val stackTrace = stackFile.readText()
            val result = StackTraceDecoder().decode(stackTrace, mappingFile)
            renderer.renderDecodedStackTrace(result)
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
        }
    }

    private fun handleKeystore() {
        val file = selectFile(listOf("jks", "keystore", "p12", "pfx")) ?: return
        terminal.println()
        val password = StringPrompt(msg["interactive.keystorePassword"], terminal).ask()
        terminal.println()
        terminal.println("Reading keystore ${file.name}...")
        terminal.println()
        try {
            val result = KeystoreInspector().inspect(file, password)
            renderer.renderKeystoreInfo(result)
            RecentFiles.add(file)
        } catch (e: Exception) {
            terminal.println(red(msg.get("common.error", e.message ?: "")))
        }
    }

    private fun handleAdb() {
        terminal.println()
        val action = terminal.interactiveSelectList {
            addEntry(msg["adb.menu.pull"])
            addEntry(msg["adb.menu.packages"])
            addEntry(msg["adb.menu.install"])
            addEntry(msg["adb.menu.launch"])
            addEntry(msg["adb.menu.start"])
            addEntry(msg["adb.menu.logcat"])
            addEntry(msg["adb.menu.deeplink"])
            addEntry(msg["adb.menu.screenshot"])
            addEntry(msg["adb.menu.clear"])
            addEntry(msg["adb.menu.uninstall"])
            addEntry(msg["adb.menu.mirror"])
            addEntry(msg["adb.menu.devices"])
            addEntry(backKey)
        }?.substringBefore(" ")?.trim() ?: return

        if (action == "←") return

        val adb = try {
            AdbService(terminal, msg)
        } catch (e: Exception) {
            terminal.println(red(msg.get("adb.notFound", e.message ?: "")))
            return
        }

        when (action) {
            "pull" -> {
                terminal.println()
                terminal.println(bold(msg["adb.pullPrompt"]))
                val choiceAction = terminal.interactiveSelectList {
                    addEntry(msg["adb.pullSearch"])
                    addEntry(msg["adb.pullManual"])
                    addEntry(backKey)
                }?.substringBefore(" ")?.trim() ?: return

                val pkg = when (choiceAction) {
                    "search" -> {
                        terminal.println()
                        val filter = StringPrompt(msg["adb.filterPrompt"], terminal).ask() ?: ""
                        val packages = adb.listPackages(filter.ifBlank { null })
                        if (packages.isEmpty()) {
                            terminal.println(yellow(msg["adb.noAppsFound"]))
                            return
                        }
                        terminal.println()
                        val selected = terminal.interactiveSelectList {
                            packages.forEach { addEntry(it) }
                            addEntry(backKey)
                        } ?: return
                        if (selected == backKey) return
                        selected
                    }
                    "manual" -> {
                        terminal.println()
                        StringPrompt("Package name", terminal).ask()?.takeIf { it.isNotBlank() } ?: return
                    }
                    else -> return
                }

                val outputDir = Path(System.getProperty("user.dir"))
                val pulledFile = adb.pullApk(pkg, outputDir)

                // Offer to load the pulled APK as current file
                if (pulledFile != null && pulledFile.name.endsWith(".apk")) {
                    terminal.println()
                    terminal.println(bold(msg["interactive.pullLoadPrompt"]))
                    val loadChoice = terminal.interactiveSelectList {
                        addEntry(msg["interactive.pullLoadYes"])
                        addEntry(msg["interactive.pullLoadNo"])
                    } ?: return
                    if (loadChoice == msg["interactive.pullLoadYes"]) {
                        currentFile = pulledFile
                        RecentFiles.add(pulledFile)
                        terminal.println(green(msg.get("interactive.fileLoadedStatus", pulledFile.name)))
                    }
                }
            }
            "packages" -> {
                terminal.println()
                val filter = StringPrompt(msg["adb.filterPrompt"], terminal).ask() ?: ""
                val packages = adb.listPackages(filter.ifBlank { null })
                if (packages.isEmpty()) {
                    terminal.println(yellow(msg["adb.noAppsFound"]))
                } else {
                    terminal.println(bold(msg.get("adb.installedApps", packages.size)))
                    packages.forEach { terminal.println("  $it") }
                }
            }
            "install" -> {
                val file = resolveApkFile() ?: return
                adb.installApk(file)
            }
            "launch" -> {
                val file = resolveApkFile() ?: return
                terminal.println()
                val pkg = StringPrompt("Package name", terminal).ask()
                if (!pkg.isNullOrBlank()) adb.installAndLaunch(file, pkg)
            }
            "start" -> {
                terminal.println()
                val pkg = StringPrompt("Package name", terminal).ask()
                if (!pkg.isNullOrBlank()) adb.launchApp(pkg)
            }
            "logcat" -> {
                terminal.println()
                val pkg = StringPrompt("Package name", terminal).ask()
                if (!pkg.isNullOrBlank()) adb.logcat(pkg)
            }
            "deeplink" -> {
                terminal.println()
                val url = StringPrompt("Deeplink URL", terminal).ask()
                if (!url.isNullOrBlank()) adb.testDeeplink(url)
            }
            "screenshot" -> {
                val output = Path(System.getProperty("user.dir"), "screenshot_${System.currentTimeMillis()}.png")
                adb.screenshot(output)
            }
            "clear" -> {
                terminal.println()
                val pkg = StringPrompt("Package name", terminal).ask()
                if (!pkg.isNullOrBlank()) adb.clearAppData(pkg)
            }
            "uninstall" -> {
                terminal.println()
                val pkg = StringPrompt("Package name", terminal).ask()
                if (!pkg.isNullOrBlank()) adb.uninstall(pkg)
            }
            "mirror" -> {
                adb.startScrcpy()
            }
            "devices" -> {
                val devices = adb.listDevices()
                if (devices.isEmpty()) {
                    terminal.println(yellow(msg["adb.noDevices"]))
                } else {
                    terminal.println(bold(msg["adb.connectedDevices"]))
                    devices.forEach { dev ->
                        terminal.println("  ${dev.model.ifEmpty { dev.serial }} - ${dev.status} (${dev.serial})")
                    }
                }
            }
        }
    }

    private fun resolveApkFile(): Path? {
        if (currentFile?.extension?.lowercase() == "apk") {
            terminal.println(bold(msg["interactive.whichApk"]))
            val selectOtherLabel = msg["interactive.selectOther"]
            val choice = terminal.interactiveSelectList {
                addEntry(msg.get("interactive.useLoaded", currentFile!!.name))
                addEntry(selectOtherLabel)
                addEntry(backKey)
            } ?: return null
            return when {
                choice == backKey -> null
                choice == selectOtherLabel -> selectFile(listOf("apk"))
                else -> currentFile
            }
        }
        return selectFile(listOf("apk"))
    }

    // ─────────────────────────────────────────────────────────
    //  FILE SELECTION
    // ─────────────────────────────────────────────────────────

    private fun selectFile(extensions: List<String>): Path? {
        terminal.println()

        val menuLabels = mutableListOf<String>()
        val menuPaths = mutableListOf<Path?>()

        val recents = RecentFiles.load().filter { it.extension.lowercase() in extensions }
        for (file in recents) {
            val size = try { FileSize.format(file.fileSize()) } catch (_: Exception) { "?" }
            val dir = file.parent?.toString()?.let { gray(" - $it") } ?: ""
            menuLabels.add("${file.name} ($size)$dir")
            menuPaths.add(file)
        }

        menuLabels.add(msg["interactive.filePicker"])
        menuPaths.add(null)
        menuLabels.add(msg["interactive.manualPath"])
        menuPaths.add(null)
        menuLabels.add(backKey)
        menuPaths.add(null)

        terminal.println(bold(msg["interactive.selectFileTitle"]))
        terminal.println()

        val filePickerIdx = menuLabels.size - 3
        val manualPathIdx = menuLabels.size - 2
        val backIdx = menuLabels.size - 1

        val selected = terminal.interactiveSelectList {
            menuLabels.forEach { addEntry(it) }
        } ?: return null

        val selectedIdx = menuLabels.indexOf(selected)

        return when (selectedIdx) {
            backIdx -> null
            filePickerIdx -> {
                terminal.println(msg["interactive.openingPicker"])
                val file = FileChooser.selectFile(
                    title = "Select ${extensions.joinToString("/").uppercase()} File",
                    extensions = extensions
                )
                if (file == null) {
                    terminal.println(yellow(msg["interactive.noFileManualHint"]))
                    promptForPath()
                } else {
                    terminal.println(green(msg.get("interactive.selected", file.name)))
                    file
                }
            }
            manualPathIdx -> promptForPath()
            else -> {
                val path = menuPaths[selectedIdx]
                if (path != null) terminal.println(green(msg.get("interactive.selected", path.name)))
                path
            }
        }
    }

    private fun promptForPath(): Path? {
        terminal.println()
        terminal.println(msg["interactive.pastePathHint"])
        terminal.println(gray("  ${msg["interactive.pastePathCancel"]}"))
        terminal.println()

        val input = StringPrompt(msg["interactive.pathPrompt"], terminal).ask()
        if (input.isNullOrBlank()) return null

        val cleaned = input.trim()
            .removeSurrounding("'")
            .removeSurrounding("\"")
            .replace("\\ ", " ")

        val path = Path(cleaned)

        if (!path.exists()) {
            terminal.println(red(msg.get("interactive.fileNotFound", path.toString())))
            return null
        }
        if (!path.isRegularFile()) {
            terminal.println(red(msg.get("interactive.notAFile", path.toString())))
            return null
        }

        terminal.println(green(msg.get("interactive.selected", path.name)))
        return path
    }
}
