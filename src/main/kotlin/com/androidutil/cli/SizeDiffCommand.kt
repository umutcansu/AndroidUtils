package com.androidutil.cli

import com.androidutil.core.diff.SizeDiff
import com.androidutil.output.JsonRenderer
import com.androidutil.output.TerminalRenderer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path

class SizeDiffCommand : CliktCommand(name = "sizediff") {
    override fun help(context: Context) = "Compare sizes between two AAB/APK files"

    private val oldFile by argument("old", help = "Path to old .aab or .apk file")
        .path(mustExist = true, canBeDir = false)

    private val newFile by argument("new", help = "Path to new .aab or .apk file")
        .path(mustExist = true, canBeDir = false)

    private val config by requireObject<AppConfig>()

    override fun run() {
        val result = SizeDiff().compare(oldFile, newFile)
        val renderer = if (config.json) JsonRenderer(config.terminal) else TerminalRenderer(config.terminal, config.messages)
        renderer.renderSizeDiff(result)
    }
}
