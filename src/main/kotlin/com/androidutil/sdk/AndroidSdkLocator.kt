package com.androidutil.sdk

import java.nio.file.Path
import kotlin.io.path.*

data class ToolPaths(
    val aapt2Path: Path?,
    val apksignerPath: Path?,
    val keytoolPath: Path?
)

object AndroidSdkLocator {

    fun locate(): ToolPaths {
        val androidHome = resolveAndroidHome()
        return ToolPaths(
            aapt2Path = findBuildTool(androidHome, aapt2Binary()),
            apksignerPath = findBuildTool(androidHome, apksignerBinary()),
            keytoolPath = findKeytool()
        )
    }

    fun resolveAndroidHome(): Path? {
        return System.getenv("ANDROID_HOME")?.let { Path(it) }?.takeIf { it.exists() }
            ?: System.getenv("ANDROID_SDK_ROOT")?.let { Path(it) }?.takeIf { it.exists() }
            ?: platformDefault()
    }

    private fun platformDefault(): Path? {
        val os = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")
        return when {
            "mac" in os -> Path(home, "Library/Android/sdk")
            "linux" in os -> Path(home, "Android/Sdk")
            "win" in os -> Path(home, "AppData", "Local", "Android", "Sdk")
            else -> null
        }?.takeIf { it.exists() }
    }

    private fun findBuildTool(sdkHome: Path?, toolName: String): Path? {
        if (sdkHome == null) return null
        val buildTools = sdkHome.resolve("build-tools")
        if (!buildTools.exists()) return null

        return buildTools.listDirectoryEntries()
            .filter { it.isDirectory() }
            .sortedByDescending { it.name }
            .firstNotNullOfOrNull { dir ->
                val tool = dir.resolve(toolName)
                tool.takeIf { it.exists() }
            }
    }

    private fun findKeytool(): Path? {
        val javaHome = System.getProperty("java.home") ?: return null
        val keytool = Path(javaHome, "bin", keytoolBinary())
        return keytool.takeIf { it.exists() }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("win")

    private fun aapt2Binary(): String =
        if (isWindows()) "aapt2.exe" else "aapt2"

    private fun apksignerBinary(): String =
        if (isWindows()) "apksigner.bat" else "apksigner"

    private fun keytoolBinary(): String =
        if (isWindows()) "keytool.exe" else "keytool"
}
