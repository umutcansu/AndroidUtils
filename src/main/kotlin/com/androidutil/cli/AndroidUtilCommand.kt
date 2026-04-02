package com.androidutil.cli

import com.androidutil.i18n.Messages
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.findOrSetObject
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.mordant.terminal.Terminal

data class AppConfig(
    val json: Boolean,
    val verbose: Boolean,
    val terminal: Terminal,
    val messages: Messages
)

class AndroidUtilCommand : CliktCommand(name = "androidutil") {
    override fun help(context: Context) = "Android developer utility tool for AAB/APK analysis, conversion, and signing"

    private val json by option("--json", help = "Output in JSON format").flag()
    private val verbose by option("-v", "--verbose", help = "Verbose output").flag()
    private val lang by option("--lang", help = "Language (tr/en)").default("tr")

    init {
        versionOption("1.0.0", names = setOf("--version", "-V"))
        context {
            autoEnvvarPrefix = "ANDROIDUTIL"
        }
    }

    override fun run() {
        val messages = Messages.forLanguage(lang)
        val config = AppConfig(json = json, verbose = verbose, terminal = terminal, messages = messages)
        currentContext.findOrSetObject { config }
    }
}
