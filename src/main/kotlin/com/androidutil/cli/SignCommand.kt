package com.androidutil.cli

import com.androidutil.core.signing.SigningService
import com.androidutil.core.signing.SignatureVerifier
import com.androidutil.output.JsonRenderer
import com.androidutil.output.TerminalRenderer
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path

class SignCommand : CliktCommand(name = "sign") {
    override fun help(context: Context) = "Sign or verify APK signatures"
    override fun run() = Unit
}

class SignApkCommand : CliktCommand(name = "apk") {
    override fun help(context: Context) = "Sign an APK file"

    private val file by argument(help = "Path to .apk file")
        .path(mustExist = true, canBeDir = false)

    private val keystore by option("--keystore", help = "Keystore path")
        .path(mustExist = true)
        .required()

    private val keystorePassword by option("--keystore-password", help = "Keystore password")
        .required()

    private val keyAlias by option("--key-alias", help = "Key alias")
        .required()

    private val keyPassword by option("--key-password", help = "Key password")

    private val config by requireObject<AppConfig>()

    override fun run() {
        SigningService(config.terminal, config.messages).signApk(
            apkPath = file,
            keystorePath = keystore,
            keystorePassword = keystorePassword,
            keyAlias = keyAlias,
            keyPassword = keyPassword ?: keystorePassword
        )
    }
}

class SignVerifyCommand : CliktCommand(name = "verify") {
    override fun help(context: Context) = "Verify APK signature"

    private val file by argument(help = "Path to .apk file")
        .path(mustExist = true, canBeDir = false)

    private val config by requireObject<AppConfig>()

    override fun run() {
        val renderer = if (config.json) JsonRenderer(config.terminal) else TerminalRenderer(config.terminal, config.messages)
        val result = SignatureVerifier().verify(file)
        renderer.renderSignatureVerification(result)
    }
}
