package com.androidutil.core.manifest

import com.androidutil.sdk.AndroidSdkLocator
import com.androidutil.util.ProcessRunner
import java.nio.file.Path
import java.util.zip.ZipFile

class ManifestParser {

    fun parseFromApk(apkPath: Path): ManifestInfo {
        val aapt2 = AndroidSdkLocator.locate().aapt2Path
            ?: throw IllegalStateException(
                "aapt2 bulunamadi. ANDROID_HOME ayarlayin veya Android SDK Build Tools kurun."
            )

        val result = ProcessRunner.run(
            listOf(aapt2.toString(), "dump", "badging", apkPath.toString())
        )

        if (result.exitCode != 0) {
            throw IllegalStateException("aapt2 dump basarisiz: ${result.stderr}")
        }

        return parseBadgingOutput(result.stdout)
    }

    fun parseFromAab(aabPath: Path): ManifestInfo {
        return try {
            parseFromAabLibrary(aabPath)
        } catch (e: Exception) {
            ManifestInfo(
                packageName = "unknown",
                versionCode = 0,
                versionName = "unknown",
                minSdk = 0,
                targetSdk = 0,
                compileSdk = null,
                permissions = emptyList(),
                activities = 0,
                services = 0,
                receivers = 0,
                providers = 0
            )
        }
    }

    private fun parseFromAabLibrary(aabPath: Path): ManifestInfo {
        val appBundle = ZipFile(aabPath.toFile()).use { zip ->
            com.android.tools.build.bundletool.model.AppBundle.buildFromZip(zip)
        }

        val manifest = appBundle.baseModule.androidManifest

        val packageName = manifest.packageName
        val versionCode = manifest.versionCode.orElse(0).toLong()
        val versionName = manifest.versionName.orElse("unknown")
        val minSdk = manifest.minSdkVersion.orElse(0)
        val targetSdk = manifest.targetSdkVersion.orElse(0)

        // compileSdk
        val compileSdk = try {
            manifest.manifestElement
                .getAttribute("http://schemas.android.com/apk/res/android", "compileSdkVersion")
                .map { it.valueAsDecimalInteger }
                .orElse(null)
        } catch (_: Exception) { null }

        // uses-permission from manifest element
        val nsUri = "http://schemas.android.com/apk/res/android"
        val allPermissions = try {
            manifest.manifestElement
                .getChildrenElements("uses-permission")
                .map { elem ->
                    elem.getAttribute(nsUri, "name")
                        .map { it.valueAsString }
                        .orElse(null)
                }
                .filter { it != null }
                .toList()
                .filterNotNull()
        } catch (_: Exception) { emptyList() }

        // Component counts
        val appElement = try {
            manifest.manifestElement.getOptionalChildElement("application").orElse(null)
        } catch (_: Exception) { null }

        val activities = appElement?.getChildrenElements("activity")?.count()?.toInt() ?: 0
        val services = appElement?.getChildrenElements("service")?.count()?.toInt() ?: 0
        val receivers = appElement?.getChildrenElements("receiver")?.count()?.toInt() ?: 0
        val providers = appElement?.getChildrenElements("provider")?.count()?.toInt() ?: 0

        return ManifestInfo(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            permissions = allPermissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers
        )
    }

    /** Parse manifest XML string (from bundletool dump or aapt2 output) */
    fun parseXmlManifest(xml: String): ManifestInfo {
        val packageName = Regex("package=\"([^\"]*)\"").find(xml)
            ?.groupValues?.get(1) ?: "unknown"
        val versionCode = Regex("android:versionCode=\"(\\d+)\"").find(xml)
            ?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val versionName = Regex("android:versionName=\"([^\"]*)\"").find(xml)
            ?.groupValues?.get(1) ?: "unknown"
        val minSdk = Regex("android:minSdkVersion=\"(\\d+)\"").find(xml)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val targetSdk = Regex("android:targetSdkVersion=\"(\\d+)\"").find(xml)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val compileSdk = Regex("android:compileSdkVersion=\"(\\d+)\"").find(xml)
            ?.groupValues?.get(1)?.toIntOrNull()

        val permissions = Regex("<uses-permission[^>]*android:name=\"([^\"]*)\"")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()

        val activities = Regex("<activity[\\s>]").findAll(xml).count()
        val services = Regex("<service[\\s>]").findAll(xml).count()
        val receivers = Regex("<receiver[\\s>]").findAll(xml).count()
        val providers = Regex("<provider[\\s>]").findAll(xml).count()

        return ManifestInfo(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers
        )
    }

    private fun parseBadgingOutput(output: String): ManifestInfo {
        val packageMatch = Regex("package: name='([^']*)'\\s+versionCode='([^']*)'\\s+versionName='([^']*)'")
            .find(output)

        val packageName = packageMatch?.groupValues?.get(1) ?: "unknown"
        val versionCode = packageMatch?.groupValues?.get(2)?.toLongOrNull() ?: 0
        val versionName = packageMatch?.groupValues?.get(3) ?: "unknown"

        val minSdk = Regex("sdkVersion:'(\\d+)'").find(output)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val targetSdk = Regex("targetSdkVersion:'(\\d+)'").find(output)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val compileSdk = Regex("compileSdkVersion='(\\d+)'").find(output)
            ?.groupValues?.get(1)?.toIntOrNull()

        val permissions = Regex("uses-permission: name='([^']*)'")
            .findAll(output)
            .map { it.groupValues[1] }
            .toList()

        val activities = Regex("activity[^-]").findAll(output).count()
        val services = Regex("service[^-]").findAll(output).count()
        val receivers = Regex("receiver[^-]").findAll(output).count()
        val providers = Regex("provider[^-]").findAll(output).count()

        return ManifestInfo(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers
        )
    }
}
