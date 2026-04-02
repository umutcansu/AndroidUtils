package com.androidutil.cli

import com.androidutil.core.playcompat.PlayCompatChecker
import com.androidutil.output.JsonRenderer
import com.androidutil.output.TerminalRenderer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path

class PlayCheckCommand : CliktCommand(name = "playcheck") {
    override fun help(context: Context) = "Check Google Play Store compatibility (targetSdk, 16KB alignment, 64-bit, permissions)"

    private val file by argument(help = "Path to .aab or .apk file")
        .path(mustExist = true, canBeDir = false)

    private val config by requireObject<AppConfig>()

    override fun run() {
        if (!config.json) config.terminal.print("Play uyumlulugu kontrol ediliyor...")
        val result = PlayCompatChecker().check(file)
        if (!config.json) config.terminal.print("\r\u001B[K")
        val renderer = if (config.json) JsonRenderer(config.terminal) else TerminalRenderer(config.terminal)
        renderer.renderPlayCompat(result)
    }
}
