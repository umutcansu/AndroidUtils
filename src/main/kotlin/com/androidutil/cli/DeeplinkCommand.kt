package com.androidutil.cli

import com.androidutil.core.deeplink.DeeplinkAnalyzer
import com.androidutil.output.JsonRenderer
import com.androidutil.output.TerminalRenderer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path

class DeeplinkCommand : CliktCommand(name = "deeplink") {
    override fun help(context: Context) = "Extract and list deeplinks from AAB/APK — app links, custom schemes, auto-verify status"

    private val file by argument(help = "Path to .aab or .apk file")
        .path(mustExist = true, canBeDir = false)

    private val config by requireObject<AppConfig>()

    override fun run() {
        val renderer = if (config.json) JsonRenderer(config.terminal) else TerminalRenderer(config.terminal)
        val result = DeeplinkAnalyzer().analyze(file)
        renderer.renderDeeplinkAnalysis(result)
    }
}
