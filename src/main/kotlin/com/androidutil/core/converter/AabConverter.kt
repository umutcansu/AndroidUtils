package com.androidutil.core.converter

import com.android.tools.build.bundletool.commands.BuildApksCommand
import com.android.tools.build.bundletool.commands.BuildApksCommand.ApkBuildMode
import com.android.tools.build.bundletool.model.Password
import com.android.tools.build.bundletool.model.SigningConfiguration
import com.androidutil.i18n.Messages
import com.androidutil.sdk.AndroidSdkLocator
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import java.io.PrintStream
import java.nio.file.Path
import java.util.Optional
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.name

data class ConversionConfig(
    val aabPath: Path,
    val outputPath: Path?,
    val keystorePath: Path?,
    val keystorePassword: String?,
    val keyAlias: String?,
    val keyPassword: String?,
    val deviceSpecPath: Path?,
    val universal: Boolean
)

class AabConverter(private val terminal: Terminal, private val msg: Messages) {

    fun convert(config: ConversionConfig) {
        val outputPath = config.outputPath
            ?: config.aabPath.resolveSibling(
                config.aabPath.name.replace(".aab", if (config.universal) ".apk" else ".apks")
            )

        // bundletool fails if output file already exists
        if (outputPath.exists()) {
            outputPath.deleteIfExists()
        }

        terminal.println(msg.get("converter.converting", config.aabPath.name))

        try {
            val builder = BuildApksCommand.builder()
                .setBundlePath(config.aabPath)
                .setOutputFile(outputPath)
                .setOverwriteOutput(true)

            if (config.universal) {
                builder.setApkBuildMode(ApkBuildMode.UNIVERSAL)
            }

            // Signing config
            if (config.keystorePath != null) {
                val ksPass = if (config.keystorePassword != null) {
                    Optional.of(Password.createFromStringValue("pass:${config.keystorePassword}"))
                } else {
                    Optional.empty()
                }
                val keyPass = if (config.keyPassword != null) {
                    Optional.of(Password.createFromStringValue("pass:${config.keyPassword}"))
                } else {
                    Optional.empty()
                }
                val signingConfig = SigningConfiguration.extractFromKeystore(
                    config.keystorePath,
                    config.keyAlias ?: "",
                    ksPass,
                    keyPass
                )
                builder.setSigningConfiguration(signingConfig)
            }

            // Add aapt2 path if available
            val tools = AndroidSdkLocator.locate()
            if (tools.aapt2Path != null) {
                builder.setAapt2Command(
                    com.android.tools.build.bundletool.androidtools.Aapt2Command.createFromExecutablePath(tools.aapt2Path)
                )
            }

            // Suppress bundletool's own stdout noise
            val originalOut = System.out
            try {
                System.setOut(PrintStream(java.io.OutputStream.nullOutputStream()))
                builder.build().execute()
            } finally {
                System.setOut(originalOut)
            }

            terminal.println(TextColors.green(msg.get("converter.success", outputPath.absolutePathString())))

            if (config.universal) {
                terminal.println(msg["converter.universalCreated"])
            } else {
                terminal.println(msg["converter.apksCreated"])
                terminal.println(TextColors.gray(msg["converter.apksInstallHint"]))
            }
        } catch (e: Exception) {
            terminal.println(TextColors.red(msg.get("converter.error", e.message ?: "")))
            if (e.cause != null) {
                terminal.println(TextColors.gray(msg.get("converter.errorDetail", e.cause?.message ?: "")))
            }
        }
    }
}
