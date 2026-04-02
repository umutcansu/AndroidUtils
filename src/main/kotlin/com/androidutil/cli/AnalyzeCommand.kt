package com.androidutil.cli

import com.androidutil.core.analyzer.AabAnalyzer
import com.androidutil.core.analyzer.ApkAnalyzer
import com.androidutil.i18n.Messages
import com.androidutil.output.JsonRenderer
import com.androidutil.output.OutputRenderer
import com.androidutil.output.TerminalRenderer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import java.nio.file.Path

class AnalyzeCommand : CliktCommand(name = "analyze") {
    override fun help(context: Context) = "Analyze an AAB or APK file (manifest, certificates, 16KB alignment, permissions)"

    private val file by argument(help = "Path to .aab or .apk file")
        .path(mustExist = true, canBeDir = false)

    private val config by requireObject<AppConfig>()

    override fun run() {
        val renderer: OutputRenderer = if (config.json) {
            JsonRenderer(config.terminal)
        } else {
            TerminalRenderer(config.terminal, config.messages)
        }

        if (!config.json) {
            config.terminal.print(config.messages.get("analyze.progress", file.fileName))
        }
        when (file.toString().substringAfterLast('.').lowercase()) {
            "aab" -> {
                val result = AabAnalyzer().analyze(file)
                if (!config.json) config.terminal.print("\r\u001B[K") // clear status line
                renderer.renderAabAnalysis(result)
            }
            "apk" -> {
                val result = ApkAnalyzer().analyze(file)
                if (!config.json) config.terminal.print("\r\u001B[K")
                renderer.renderApkAnalysis(result)
            }
            else -> throw UsageError("Unsupported file type. Expected .aab or .apk")
        }
    }
}
