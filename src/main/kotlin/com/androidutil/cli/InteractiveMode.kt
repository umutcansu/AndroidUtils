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

private const val BACK_KEY = "← Geri don"

class InteractiveMode(private val config: AppConfig) {

    private val terminal = config.terminal
    private val renderer: OutputRenderer = if (config.json) {
        JsonRenderer(terminal)
    } else {
        TerminalRenderer(terminal)
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
                terminal.println(bold("Bir AAB veya APK dosyasi sec:"))
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

        terminal.println(green("Gorusuruz!"))
    }

    // ─────────────────────────────────────────────────────────
    //  OPERATION MENU
    // ─────────────────────────────────────────────────────────

    private fun selectOperation(): String? {
        val file = currentFile ?: return null
        val ext = file.extension.lowercase()
        val size = try { FileSize.format(file.fileSize()) } catch (_: Exception) { "?" }

        terminal.println(bold(green("Dosya: ${file.name} ($size)")))
        terminal.println(gray("  ${file.parent}"))
        terminal.println()

        val entries = mutableListOf<String>()

        // File-specific operations
        entries.add("analyze   - AAB/APK analiz (16KB, manifest, sertifika, boyut)")
        entries.add("deeplink  - Deeplink listele ve test et")
        entries.add("verify    - Imza dogrulama (sertifika bilgileri)")
        entries.add("nativelib - Native library (.so) inspector")
        entries.add("resources - Resource ve asset listesi")
        entries.add("playcheck - Google Play uyumluluk kontrolu")

        if (ext == "aab") {
            entries.add("convert   - AAB → APK donustur")
        }

        entries.add("─── Karsilastirma ──────────────────────────────────────")
        entries.add("mdiff     - Manifest karsilastirma (tum alanlar)")
        entries.add("sizediff  - Boyut karsilastirma (detayli)")

        entries.add("─── Diger Araclar ──────────────────────────────────────")
        entries.add("download  - Play Store'dan APK indir (apkeep)")
        entries.add("decode    - Obfuscated stack trace coz (mapping.txt)")
        entries.add("keystore  - Keystore bilgileri")
        entries.add("adb       - ADB (install, launch, logcat, screenshot...)")
        entries.add("report    - HTML rapor olustur")

        entries.add("────────────────────────────────────────────────────────")
        entries.add("change    - Dosya degistir")
        entries.add("quit      - Cikis")

        val selected = terminal.interactiveSelectList {
            entries.forEach { addEntry(it) }
        } ?: return null

        val action = selected.substringBefore(" ").trim()
        if (action.startsWith("─")) return ""
        return action
    }

    private fun selectToolsOrQuit(): String? {
        terminal.println()
        terminal.println(bold("Dosya secilmedi. Baska bir arac kullanabilirsin:"))
        terminal.println()

        val selected = terminal.interactiveSelectList {
            addEntry("download - Play Store'dan APK indir (apkeep)")
            addEntry("decode   - Obfuscated stack trace coz (mapping.txt)")
            addEntry("keystore - Keystore bilgileri")
            addEntry("adb      - ADB kisayollari")
            addEntry("file     - Dosya sec (tekrar dene)")
            addEntry("quit     - Cikis")
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
        terminal.println("${file.name} analiz ediliyor...")
        terminal.println()
        try {
            when (file.extension.lowercase()) {
                "aab" -> renderer.renderAabAnalysis(AabAnalyzer().analyze(file))
                "apk" -> renderer.renderApkAnalysis(ApkAnalyzer().analyze(file))
            }
        } catch (e: Exception) {
            terminal.println(red("Hata: ${e.message}"))
            if (config.verbose) e.printStackTrace()
        }
    }

    private fun handleConvert() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println(bold("Donusum modu:"))
        terminal.println()

        val mode = terminal.interactiveSelectList {
            addEntry("universal - Tek APK (tum cihazlar icin)")
            addEntry("default   - Cihaza ozel APK seti (.apks)")
            addEntry(BACK_KEY)
        }?.substringBefore(" ")?.trim() ?: return
        if (mode == "←") return

        val universal = mode == "universal"
        val defaultOutput = file.resolveSibling(file.name.replace(".aab", if (universal) ".apk" else ".apks"))

        terminal.println()
        terminal.println("Cikti dosyasi: ${green(defaultOutput.absolutePathString())}")
        terminal.println(gray("Farkli bir yol girmek icin yaz, ayni yol icin bos birak:"))
        val customPath = StringPrompt("Cikti yolu", terminal).ask()
        val outputPath = if (customPath.isNullOrBlank()) defaultOutput else Path(customPath.trim())

        terminal.println()
        try {
            val conversionConfig = com.androidutil.core.converter.ConversionConfig(
                aabPath = file, outputPath = outputPath,
                keystorePath = null, keystorePassword = null,
                keyAlias = null, keyPassword = null,
                deviceSpecPath = null, universal = universal
            )
            com.androidutil.core.converter.AabConverter(terminal).convert(conversionConfig)
        } catch (e: Exception) {
            terminal.println(red("Hata: ${e.message}"))
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
            terminal.println(red("Hata: ${e.message}"))
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
                terminal.println(bold("Bir deeplink'i ADB ile test etmek ister misin?"))
                terminal.println()
                val options = result.deeplinks.map { it.fullUrl } + listOf(BACK_KEY)
                val selected = terminal.interactiveSelectList {
                    options.forEach { addEntry(it) }
                } ?: return
                if (selected != BACK_KEY) {
                    try { AdbService(terminal).testDeeplink(selected) }
                    catch (e: Exception) { terminal.println(red("ADB hatasi: ${e.message}")) }
                }
            }
        } catch (e: Exception) {
            terminal.println(red("Hata: ${e.message}"))
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
            terminal.println(red("Hata: ${e.message}"))
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
            terminal.println(red("Hata: ${e.message}"))
        }
    }

    private fun handlePlayCheck() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println("Checking Google Play compatibility for ${file.name}...")
        terminal.println()
        try {
            val result = PlayCompatChecker().check(file)
            renderer.renderPlayCompat(result)
        } catch (e: Exception) {
            terminal.println(red("Hata: ${e.message}"))
        }
    }

    private fun handleManifestDiff() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println(green("Eski dosya: ${file.name}"))
        terminal.println(bold("Yeni dosya sec:"))
        val newFile = selectFile(listOf("aab", "apk")) ?: return
        terminal.println()
        terminal.println("Comparing manifests...")
        terminal.println()
        try {
            val result = ManifestDiff().compare(file, newFile)
            renderer.renderManifestDiff(result)
        } catch (e: Exception) {
            terminal.println(red("Hata: ${e.message}"))
        }
    }

    private fun handleSizeDiff() {
        val file = currentFile ?: return
        terminal.println()
        terminal.println(green("Eski dosya: ${file.name}"))
        terminal.println(bold("Yeni dosya sec:"))
        val newFile = selectFile(listOf("aab", "apk")) ?: return
        terminal.println()
        terminal.println("Comparing sizes...")
        terminal.println()
        try {
            val result = SizeDiff().compare(file, newFile)
            renderer.renderSizeDiff(result)
        } catch (e: Exception) {
            terminal.println(red("Hata: ${e.message}"))
        }
    }

    private fun handleDownload() {
        terminal.println()
        val downloader = PlayStoreDownloader(terminal)

        val source = terminal.interactiveSelectList {
            addEntry("apkpure  - APKPure'dan indir (auth gerektirmez)")
            addEntry("url      - Direkt URL'den indir")
            addEntry(BACK_KEY)
        }?.substringBefore(" ")?.trim() ?: return

        if (source == "←") return

        when (source) {
            "apkpure" -> {
                if (!downloader.isApkeepAvailable()) {
                    terminal.println(red("apkeep bulunamadi!"))
                    terminal.println()
                    terminal.println(bold("Kurulum:"))
                    terminal.println(cyan("  cargo install apkeep"))
                    terminal.println(cyan("  brew install apkeep"))
                    terminal.println(cyan("  https://github.com/EFForg/apkeep/releases"))
                    return
                }
                terminal.println()
                val pkg = StringPrompt("Package name (ornegin: com.whatsapp)", terminal).ask()
                if (pkg.isNullOrBlank()) return

                val outputDir = Path(System.getProperty("user.dir"))
                val file = downloader.downloadFromApkPure(pkg, outputDir)

                if (file != null && (file.name.endsWith(".apk") || file.name.endsWith(".xapk"))) {
                    terminal.println()
                    terminal.println(bold("Indirilen dosyayi analiz icin yuklemek ister misin?"))
                    val choice = terminal.interactiveSelectList {
                        addEntry("Evet - yukle ve analiz et")
                        addEntry("Hayir")
                    } ?: return
                    if (choice.startsWith("Evet")) {
                        currentFile = file
                        RecentFiles.add(file)
                        terminal.println(green("Dosya yuklendi: ${file.name}"))
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
                    terminal.println(bold("Indirilen dosyayi analiz icin yuklemek ister misin?"))
                    val choice = terminal.interactiveSelectList {
                        addEntry("Evet - yukle ve analiz et")
                        addEntry("Hayir")
                    } ?: return
                    if (choice.startsWith("Evet")) {
                        currentFile = file
                        RecentFiles.add(file)
                        terminal.println(green("Dosya yuklendi: ${file.name}"))
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
        terminal.println("Rapor dosyasi: ${green(defaultOutput.absolutePathString())}")
        terminal.println(gray("Farkli bir yol icin yaz, ayni yol icin bos birak:"))
        val customPath = StringPrompt("Rapor yolu", terminal).ask()
        val outputPath = if (customPath.isNullOrBlank()) defaultOutput else Path(customPath.trim())

        try {
            val ext = file.extension.lowercase()
            val apkResult = if (ext == "apk") ApkAnalyzer().analyze(file) else null
            val aabResult = if (ext == "aab") AabAnalyzer().analyze(file) else null
            val nativeLibReport = try { NativeLibInspector().inspect(file) } catch (_: Exception) { null }
            val playCompat = try { PlayCompatChecker().check(file) } catch (_: Exception) { null }
            val resourceReport = try { ResourceLister().list(file) } catch (_: Exception) { null }

            HtmlReportGenerator().generateReport(
                filePath = file.absolutePathString(),
                apkResult = apkResult,
                aabResult = aabResult,
                nativeLibReport = nativeLibReport,
                playCompat = playCompat,
                resourceReport = resourceReport,
                outputPath = outputPath
            )
            terminal.println(green("Rapor olusturuldu: ${outputPath.absolutePathString()}"))
        } catch (e: Exception) {
            terminal.println(red("Hata: ${e.message}"))
            if (config.verbose) e.printStackTrace()
        }
    }

    private fun handleDecode() {
        terminal.println(bold("Stack trace dosyasi:"))
        val stackFile = selectFile(listOf("txt", "log", "stacktrace")) ?: return
        terminal.println(bold("Mapping dosyasi (mapping.txt):"))
        val mappingFile = selectFile(listOf("txt", "map")) ?: return
        terminal.println()
        terminal.println("Decoding stack trace...")
        terminal.println()
        try {
            val stackTrace = stackFile.readText()
            val result = StackTraceDecoder().decode(stackTrace, mappingFile)
            renderer.renderDecodedStackTrace(result)
        } catch (e: Exception) {
            terminal.println(red("Hata: ${e.message}"))
        }
    }

    private fun handleKeystore() {
        val file = selectFile(listOf("jks", "keystore", "p12", "pfx")) ?: return
        terminal.println()
        val password = StringPrompt("Keystore password (bos birakilabilir)", terminal).ask()
        terminal.println()
        terminal.println("Reading keystore ${file.name}...")
        terminal.println()
        try {
            val result = KeystoreInspector().inspect(file, password)
            renderer.renderKeystoreInfo(result)
            RecentFiles.add(file)
        } catch (e: Exception) {
            terminal.println(red("Hata: ${e.message}"))
        }
    }

    private fun handleAdb() {
        terminal.println()
        val action = terminal.interactiveSelectList {
            addEntry("pull       - Cihazdan APK cek (yuklu uygulamayi indir)")
            addEntry("packages   - Yuklu uygulamalari listele")
            addEntry("install    - APK kur")
            addEntry("launch     - APK kur ve baslat")
            addEntry("start      - Yuklu uygulamayi baslat")
            addEntry("logcat     - Paket filtreli logcat")
            addEntry("deeplink   - Deeplink test et")
            addEntry("screenshot - Ekran goruntusu al")
            addEntry("clear      - Uygulama verisini temizle")
            addEntry("uninstall  - Uygulamayi kaldir")
            addEntry("mirror     - Ekrani bilgisayara yansit (scrcpy)")
            addEntry("devices    - Bagli cihazlari listele")
            addEntry(BACK_KEY)
        }?.substringBefore(" ")?.trim() ?: return

        if (action == "←") return

        val adb = try {
            AdbService(terminal)
        } catch (e: Exception) {
            terminal.println(red("ADB bulunamadi: ${e.message}"))
            return
        }

        when (action) {
            "pull" -> {
                terminal.println()
                terminal.println(bold("Paket adini yaz veya listeden sec:"))
                val choiceAction = terminal.interactiveSelectList {
                    addEntry("search   - Paket ara (filtre ile)")
                    addEntry("manual   - Paket adini yaz")
                    addEntry(BACK_KEY)
                }?.substringBefore(" ")?.trim() ?: return

                val pkg = when (choiceAction) {
                    "search" -> {
                        terminal.println()
                        val filter = StringPrompt("Filtre (bos = tumu)", terminal).ask() ?: ""
                        val packages = adb.listPackages(filter.ifBlank { null })
                        if (packages.isEmpty()) {
                            terminal.println(yellow("Uygulama bulunamadi."))
                            return
                        }
                        terminal.println()
                        val selected = terminal.interactiveSelectList {
                            packages.forEach { addEntry(it) }
                            addEntry(BACK_KEY)
                        } ?: return
                        if (selected == BACK_KEY) return
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
                    terminal.println(bold("Indirilen APK'yi analiz icin yuklemek ister misin?"))
                    val loadChoice = terminal.interactiveSelectList {
                        addEntry("Evet - indirilen APK'yi yukle")
                        addEntry("Hayir - mevcut dosyayla devam et")
                    } ?: return
                    if (loadChoice.startsWith("Evet")) {
                        currentFile = pulledFile
                        RecentFiles.add(pulledFile)
                        terminal.println(green("Dosya yuklendi: ${pulledFile.name}"))
                    }
                }
            }
            "packages" -> {
                terminal.println()
                val filter = StringPrompt("Filtre (bos = tumu)", terminal).ask() ?: ""
                val packages = adb.listPackages(filter.ifBlank { null })
                if (packages.isEmpty()) {
                    terminal.println(yellow("Uygulama bulunamadi."))
                } else {
                    terminal.println(bold("Yuklu uygulamalar (${packages.size}):"))
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
                    terminal.println(yellow("Bagli cihaz bulunamadi."))
                } else {
                    terminal.println(bold("Bagli cihazlar:"))
                    devices.forEach { dev ->
                        terminal.println("  ${dev.model.ifEmpty { dev.serial }} - ${dev.status} (${dev.serial})")
                    }
                }
            }
        }
    }

    private fun resolveApkFile(): Path? {
        if (currentFile?.extension?.lowercase() == "apk") {
            terminal.println(bold("Hangi APK?"))
            val choice = terminal.interactiveSelectList {
                addEntry("${currentFile!!.name} - yuklu dosyayi kullan")
                addEntry("Baska APK sec")
                addEntry(BACK_KEY)
            } ?: return null
            return when {
                choice == BACK_KEY -> null
                choice.startsWith("Baska") -> selectFile(listOf("apk"))
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

        menuLabels.add("Dosya sec (File Picker) - sistem dosya secici dialogu")
        menuPaths.add(null)
        menuLabels.add("Yol yapistir / Drag & Drop - elle yol gir veya surukle birak")
        menuPaths.add(null)
        menuLabels.add(BACK_KEY)
        menuPaths.add(null)

        terminal.println(bold("Dosya sec:"))
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
                terminal.println("Dosya secici aciliyor...")
                val file = FileChooser.selectFile(
                    title = "Select ${extensions.joinToString("/").uppercase()} File",
                    extensions = extensions
                )
                if (file == null) {
                    terminal.println(yellow("Dosya secilmedi. Yolu elle girebilirsin:"))
                    promptForPath()
                } else {
                    terminal.println(green("Secilen: ${file.name}"))
                    file
                }
            }
            manualPathIdx -> promptForPath()
            else -> {
                val path = menuPaths[selectedIdx]
                if (path != null) terminal.println(green("Secilen: ${path.name}"))
                path
            }
        }
    }

    private fun promptForPath(): Path? {
        terminal.println()
        terminal.println("Dosya yolunu yapistir veya terminale surukle birak:")
        terminal.println(gray("  Iptal icin bos birak ve Enter'a bas"))
        terminal.println()

        val input = StringPrompt("Dosya yolu", terminal).ask()
        if (input.isNullOrBlank()) return null

        val cleaned = input.trim()
            .removeSurrounding("'")
            .removeSurrounding("\"")
            .replace("\\ ", " ")

        val path = Path(cleaned)

        if (!path.exists()) {
            terminal.println(red("Dosya bulunamadi: $path"))
            return null
        }
        if (!path.isRegularFile()) {
            terminal.println(red("Bu bir dosya degil: $path"))
            return null
        }

        terminal.println(green("Secilen: ${path.name}"))
        return path
    }
}
