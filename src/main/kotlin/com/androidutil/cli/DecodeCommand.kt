package com.androidutil.cli

import com.androidutil.core.stacktrace.StackTraceDecoder
import com.androidutil.output.JsonRenderer
import com.androidutil.output.TerminalRenderer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import kotlin.io.path.readText

class DecodeCommand : CliktCommand(name = "decode") {
    override fun help(context: Context) = "Decode obfuscated stack trace using ProGuard/R8 mapping.txt"

    private val stacktraceFile by argument(help = "File containing the obfuscated stack trace")
        .path(mustExist = true, canBeDir = false)

    private val mappingFile by option("-m", "--mapping", help = "Path to mapping.txt (ProGuard/R8)")
        .path(mustExist = true, canBeDir = false)
        .required()

    private val config by requireObject<AppConfig>()

    override fun run() {
        val renderer = if (config.json) JsonRenderer(config.terminal) else TerminalRenderer(config.terminal, config.messages)
        val stackTrace = stacktraceFile.readText()
        val result = StackTraceDecoder().decode(stackTrace, mappingFile)
        renderer.renderDecodedStackTrace(result)
    }
}
