package com.androidutil.cli

import com.androidutil.core.signing.KeystoreInspector
import com.androidutil.output.JsonRenderer
import com.androidutil.output.TerminalRenderer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path

class KeystoreCommand : CliktCommand(name = "keystore") {
    override fun help(context: Context) = "Keystore operations"
    override fun run() = Unit
}

class KeystoreInfoCommand : CliktCommand(name = "info") {
    override fun help(context: Context) = "Show keystore details"

    private val file by argument(help = "Path to keystore file (.jks or .keystore)")
        .path(mustExist = true, canBeDir = false)

    private val password by option("-p", "--password", help = "Keystore password")

    private val config by requireObject<AppConfig>()

    override fun run() {
        val renderer = if (config.json) JsonRenderer(config.terminal) else TerminalRenderer(config.terminal, config.messages)
        val result = KeystoreInspector().inspect(file, password)
        renderer.renderKeystoreInfo(result)
    }
}
