package com.androidutil.cli

import com.androidutil.core.converter.AabConverter
import com.androidutil.core.converter.ConversionConfig
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlin.io.path.isWritable

class ConvertCommand : CliktCommand(name = "convert") {
    override fun help(context: Context) = "Convert AAB to APK — universal or device-specific, with optional signing"

    private val file by argument(help = "Path to .aab file")
        .path(mustExist = true, canBeDir = false)

    private val output by option("-o", "--output", help = "Output path for generated APK(s)")
        .path()

    private val keystore by option("--keystore", help = "Keystore path for signing")
        .path(mustExist = true)

    private val keystorePassword by option("--keystore-password", help = "Keystore password")

    private val keyAlias by option("--key-alias", help = "Key alias in keystore")

    private val keyPassword by option("--key-password", help = "Key password")

    private val deviceSpec by option("--device-spec", help = "Device spec JSON for targeted APKs")
        .path(mustExist = true)

    private val universal by option("--universal", help = "Generate universal APK (tum cihazlar icin tek APK)").flag()

    private val config by requireObject<AppConfig>()

    override fun run() {
        // Validate .aab extension
        if (!file.toString().endsWith(".aab", ignoreCase = true)) {
            throw UsageError("Sadece .aab dosyalari donusturulebilir.")
        }

        // Validate output path is writable
        if (output != null) {
            val parentDir = output!!.parent
            if (parentDir != null && !parentDir.isWritable()) {
                throw UsageError("Cikti dizinine yazma izni yok: $parentDir")
            }
        }

        // Validate keystore options consistency
        if (keystore != null && keystorePassword == null) {
            throw UsageError("--keystore kullanildiginda --keystore-password gereklidir.")
        }
        if (keystore != null && keyAlias == null) {
            throw UsageError("--keystore kullanildiginda --key-alias gereklidir.")
        }

        val conversionConfig = ConversionConfig(
            aabPath = file,
            outputPath = output,
            keystorePath = keystore,
            keystorePassword = keystorePassword,
            keyAlias = keyAlias,
            keyPassword = keyPassword,
            deviceSpecPath = deviceSpec,
            universal = universal
        )
        AabConverter(config.terminal).convert(conversionConfig)
    }
}
