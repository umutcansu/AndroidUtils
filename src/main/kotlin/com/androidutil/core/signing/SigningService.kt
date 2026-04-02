package com.androidutil.core.signing

import com.androidutil.i18n.Messages
import com.androidutil.sdk.AndroidSdkLocator
import com.androidutil.util.ProcessRunner
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class SigningService(private val terminal: Terminal, private val msg: Messages) {

    fun signApk(
        apkPath: Path,
        keystorePath: Path,
        keystorePassword: String,
        keyAlias: String,
        keyPassword: String
    ) {
        val tools = AndroidSdkLocator.locate()
        val apksigner = tools.apksignerPath
            ?: throw IllegalStateException(
                msg["signing.notFound"]
            )

        terminal.println(msg.get("signing.signing", apkPath.fileName))

        val command = listOf(
            apksigner.toString(), "sign",
            "--ks", keystorePath.absolutePathString(),
            "--ks-pass", "pass:$keystorePassword",
            "--ks-key-alias", keyAlias,
            "--key-pass", "pass:$keyPassword",
            apkPath.absolutePathString()
        )

        val result = ProcessRunner.run(command)

        if (result.exitCode != 0) {
            terminal.println(TextColors.red(msg["signing.failed"]))
            terminal.println(result.stderr)
            return
        }

        terminal.println(TextColors.green(msg.get("signing.success", apkPath.fileName)))
    }
}
