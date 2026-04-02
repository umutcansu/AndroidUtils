package com.androidutil.core.signing

import com.androidutil.sdk.AndroidSdkLocator
import com.androidutil.util.ProcessRunner
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class SigningService(private val terminal: Terminal) {

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
                "apksigner bulunamadi. ANDROID_HOME ayarlayin veya Android SDK Build Tools kurun."
            )

        terminal.println("${apkPath.fileName} imzalaniyor...")

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
            terminal.println(TextColors.red("Imzalama basarisiz!"))
            terminal.println(result.stderr)
            return
        }

        terminal.println(TextColors.green("APK basariyla imzalandi: ${apkPath.fileName}"))
    }
}
