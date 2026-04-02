package com.androidutil

import com.androidutil.cli.*
import com.androidutil.i18n.Messages
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.mordant.terminal.Terminal

fun main(args: Array<String>) {
    val interactiveFlags = setOf("--json", "-v", "--verbose")
    // Extract --lang value from args for both modes
    val langIndex = args.indexOf("--lang")
    val lang = if (langIndex >= 0 && langIndex + 1 < args.size) args[langIndex + 1]
        else System.getenv("ANDROIDUTIL_LANG") ?: "tr"
    val interactiveFlagsWithLang = interactiveFlags + setOf("--lang", lang)

    if (args.isEmpty() || args.all { it in interactiveFlagsWithLang }) {
        // No subcommand given → interactive mode
        val json = args.contains("--json")
        val verbose = args.contains("-v") || args.contains("--verbose")
        val terminal = Terminal()
        val messages = Messages.forLanguage(lang)
        val config = AppConfig(json = json, verbose = verbose, terminal = terminal, messages = messages)
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
            val messages = Messages.forLanguage(lang)
            System.err.println(messages["main.error.invalidFormat"])
            System.exit(1)
        } catch (e: Exception) {
            if (e is com.github.ajalt.clikt.core.CliktError) throw e
            val messages = Messages.forLanguage(lang)
            System.err.println(messages.get("main.error.general", e.message ?: ""))
            System.exit(1)
        }
    }
}
