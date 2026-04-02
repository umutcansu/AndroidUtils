package com.androidutil.cli

import com.androidutil.core.resources.ResourceLister
import com.androidutil.output.JsonRenderer
import com.androidutil.output.TerminalRenderer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path

class ResourcesCommand : CliktCommand(name = "resources") {
    override fun help(context: Context) = "List resources and assets in AAB/APK — categories, sizes, largest files"

    private val file by argument(help = "Path to .aab or .apk file")
        .path(mustExist = true, canBeDir = false)

    private val config by requireObject<AppConfig>()

    override fun run() {
        val result = ResourceLister().list(file)
        val renderer = if (config.json) JsonRenderer(config.terminal) else TerminalRenderer(config.terminal, config.messages)
        renderer.renderResourceReport(result)
    }
}
