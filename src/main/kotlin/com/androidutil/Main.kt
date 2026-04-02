package com.androidutil

import com.androidutil.cli.*
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.mordant.terminal.Terminal

fun main(args: Array<String>) {
    val interactiveFlags = setOf("--json", "-v", "--verbose")
    if (args.isEmpty() || args.all { it in interactiveFlags }) {
        // No subcommand given → interactive mode
        val json = args.contains("--json")
        val verbose = args.contains("-v") || args.contains("--verbose")
        val terminal = Terminal()
        val config = AppConfig(json = json, verbose = verbose, terminal = terminal)
        InteractiveMode(config).start()
    } else {
        try {
            // Normal CLI mode with Clikt
            AndroidUtilCommand()
                .subcommands(
                    AnalyzeCommand(),
                    ConvertCommand(),
                    DeeplinkCommand(),
                    DiffCommand(),
                    DecodeCommand(),
                    NativeLibCommand(),
                    PlayCheckCommand(),
                    ResourcesCommand(),
                    SizeDiffCommand(),
                    ManifestDiffCommand(),
                    SignCommand().subcommands(
                        SignApkCommand(),
                        SignVerifyCommand()
                    ),
                    KeystoreCommand().subcommands(
                        KeystoreInfoCommand()
                    ),
                    MirrorCommand(),
                    AdbCommand().subcommands(
                        AdbInstallCommand(),
                        AdbDeeplinkCommand(),
                        AdbScreenshotCommand(),
                        AdbClearCommand(),
                        AdbUninstallCommand(),
                        AdbMirrorCommand()
                    )
                )
                .main(args)
        } catch (e: java.util.zip.ZipException) {
            System.err.println("Hata: Gecersiz dosya formati — dosya bozuk veya gecerli bir APK/AAB degil.")
            System.exit(1)
        } catch (e: Exception) {
            if (e is com.github.ajalt.clikt.core.CliktError) throw e
            System.err.println("Hata: ${e.message}")
            System.exit(1)
        }
    }
}
