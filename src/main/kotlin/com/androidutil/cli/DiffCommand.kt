package com.androidutil.cli

import com.androidutil.core.diff.PermissionDiff
import com.androidutil.output.JsonRenderer
import com.androidutil.output.TerminalRenderer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path

class DiffCommand : CliktCommand(name = "diff") {
    override fun help(context: Context) = "Compare permissions between two AAB/APK files"

    private val oldFile by argument("old", help = "Old AAB/APK file")
        .path(mustExist = true, canBeDir = false)

    private val newFile by argument("new", help = "New AAB/APK file")
        .path(mustExist = true, canBeDir = false)

    private val config by requireObject<AppConfig>()

    override fun run() {
        val renderer = if (config.json) JsonRenderer(config.terminal) else TerminalRenderer(config.terminal)
        val result = PermissionDiff().compare(oldFile, newFile)
        renderer.renderPermissionDiff(result)
    }
}
