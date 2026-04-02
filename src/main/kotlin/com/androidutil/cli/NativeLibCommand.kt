package com.androidutil.cli

import com.androidutil.core.nativelib.NativeLibInspector
import com.androidutil.output.JsonRenderer
import com.androidutil.output.TerminalRenderer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path

class NativeLibCommand : CliktCommand(name = "nativelib") {
    override fun help(context: Context) = "Inspect native libraries (.so) in AAB/APK — ABI, size, ELF class, 16KB alignment, strip status"

    private val file by argument(help = "Path to .aab or .apk file")
        .path(mustExist = true, canBeDir = false)

    private val config by requireObject<AppConfig>()

    override fun run() {
        if (!config.json) config.terminal.print("Native kutuphaneler inceleniyor...")
        val result = NativeLibInspector().inspect(file)
        if (!config.json) config.terminal.print("\r\u001B[K")
        val renderer = if (config.json) JsonRenderer(config.terminal) else TerminalRenderer(config.terminal)
        renderer.renderNativeLibReport(result)
    }
}
